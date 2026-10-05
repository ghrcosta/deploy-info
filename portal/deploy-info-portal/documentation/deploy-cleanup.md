# Deploy Cleanup — Validity/Cleanup Logic (Phase 1.1 item 7, on-request trigger Phase 1.2)

The sweep that detects deploy links whose deploy no longer exists in GCP and removes them: the
Datastore entry and the collector upload's Cloud Storage folder. This complements the linking logic
(`CreateDeployLinkUseCase`, which deletes the uploads of *unlinkable* uploads): the cleanup deletes
the leftovers of deploys that were linked but have since been removed from GCP (a version or
revision deleted, a service torn down, a whole project wiped).

## Implementation

- `application/cleanup/CleanupInvalidDeployLinksUseCase.kt` — the sweep itself. For every configured
  project (`ProjectRepository.getAll()`) and both deploy types, the deploys are listed from GCP
  (same listers as the linking logic) and compared against the links in the repository. A link is
  **stale** when its identity (service id, version id, location — location only for Cloud Run) is
  absent from the listing. Nothing is returned; the outcome (checked projects, deleted links, skipped
  project/type combinations) is reported via log messages.
- `application/cleanup/RunCleanupIfDueUseCase.kt` — the on-request trigger. The sweep runs from tree
  requests (`GET /portal/tree`) when due, **not** on a fixed schedule: the portal runs on App Engine
  Standard, where idle instances are terminated after ~15 minutes, so a scheduled job either never
  fires (long interval, dead instance) or keeps an instance alive forever (short interval). Tree
  requests are the app's natural heartbeat, so the sweep piggybacks on them. Timing rules:
  1. Disabled (`deploy-info.cleanup.enabled=false`) → no-op; nothing is read or written.
  2. The last completed sweep is younger than `deploy-info.cleanup.interval` → skip; a normal tree
     request pays no sweep cost.
  3. Otherwise the sweep runs; a sweep that throws is logged and **not** marked completed, so the
     next tree request retries immediately.
- `domain/CleanupState.kt` + `application/cleanup/CleanupStateRepository.kt` +
  `infrastructure/gcp/datastore/CleanupStateEntity.kt` / `CleanupStateDatastoreRepository.kt` — the
  persisted state singleton (`deploy-info/cleanup-state` kind, id `singleton`) holding
  `lastCleanupCompleted`, the only thing the due-check reads.

## Concurrency

No claim/lock is used: two backend instances may run the sweep at the same time, and this is
accepted as merely **redundant**, not dangerous:

- Every delete is idempotent and delete-only-on-proof — a link is deleted only when a successful
  listing proves its deploy is gone, the Datastore entry goes first, and a second delete of the same
  already-deleted link/folder is a no-op.
- The worst case is duplicated GCP listing API calls and duplicated work, both bounded by the number
  of instances and the once-per-interval trigger.
- If an instance dies mid-sweep, nothing was marked completed, so the next tree request retries.

## Safety rules

1. **Delete only on proof.** A link is deleted only when a *successful* listing proves its deploy is
   gone. A `GcpListingException` or `CredentialsException` for a project/deploy type skips that
   combination entirely — a transient failure never deletes data.
2. **Grace period.** Links younger than `deploy-info.cleanup.grace-period` are never judged. This
   guards against the snapshot race: the sweep compares links against a point-in-time listing; a
   deploy that finishes *while the sweep runs* is live but absent from the snapshot, so a freshly
   created link could look "stale" to the same sweep that took the snapshot. A link must be strictly
   older than the grace period to be judged (a link exactly as old is not yet judged).
3. **Delete order.** The Datastore entry goes first, then the Cloud Storage folder. A storage failure
   is logged at WARNING and the sweep continues — this can leave an orphaned folder as debris, but
   never a link pointing at a folder that was deleted underneath it.
4. **Unconfigured projects.** Links whose project is no longer configured in the portal are left
   untouched (without the project's service-account configuration they cannot even be listed
   safely). They appear ungrouped in the navigator tree until manually removed.

## Configuration (`deploy-info.*`)

| Property                           | Meaning                                                                                                                                         |
|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `deploy-info.cleanup.enabled`      | Whether the on-request trigger is active. **`false` in `application.properties`** — no profile turns it on by default; override per environment. |
| `deploy-info.cleanup.interval`     | Minimum time between completed sweeps (e.g. `PT15M`).                                                                                           |
| `deploy-info.cleanup.grace-period` | Minimum link age before it may be judged stale (e.g. `PT10M`). Should comfortably exceed one sweep duration.                                     |

No default values are baked into the code: a profile missing one of these keys fails context startup
at binding time. `application-local.properties` intentionally does not enable the cleanup.

## Testing

`tests/application/cleanup/CleanupInvalidDeployLinksUseCaseTests.kt` — pure unit tests over the
fakes (no Spring, Datastore, credentials, or internet), covering: stale GAE/RUN links deleted
(repository + storage folder), live links kept, version-level identity, Cloud Run location as part
of the identity, the grace period (including the exactly-at-the-boundary case), listing-failure
isolation (per project/type), storage-failure resilience, and unconfigured projects.

`tests/application/cleanup/RunCleanupIfDueUseCaseTests.kt` — the trigger, over
`tests/fakes/FakeCleanupStateRepository`: disabled is a no-op, first-ever request sweeps and is
marked completed, within the interval skipped (including the exactly-at-the-boundary case), after
the interval it sweeps again, and a failed sweep is not marked completed so the next request
retries immediately.
