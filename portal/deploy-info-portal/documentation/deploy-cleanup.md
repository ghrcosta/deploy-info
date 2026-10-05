# Deploy Cleanup — Validity/Cleanup Logic (Phase 1.1 item 7)

The periodic sweep that detects deploy links whose deploy no longer exists in GCP and removes them:
the Datastore entry and the collector upload's Cloud Storage folder. This complements the linking
logic (`CreateDeployLinkUseCase`, which deletes the uploads of *unlinkable* uploads): the cleanup
deletes the leftovers of deploys that were linked but have since been removed from GCP (a version or
revision deleted, a service torn down, a whole project wiped).

## Implementation

- `application/cleanup/CleanupInvalidDeployLinksUseCase.kt` — the sweep itself. For every configured
  project (`ProjectRepository.getAll()`) and both deploy types, the deploys are listed from GCP
  (same listers as the linking logic) and compared against the links in the repository. A link is
  **stale** when its identity (service id, version id, location — location only for Cloud Run) is
  absent from the listing. Nothing is returned; the outcome (checked projects, deleted links, skipped
  project/type combinations) is reported via log messages.
- `infrastructure/scheduling/CleanupScheduler.kt` — runs the sweep on a fixed interval with a
  single-threaded daemon scheduled executor (same POC-grade approach as `SimpleRetryScheduler`). The
  first sweep runs after one interval, not at startup. A failed sweep is logged and waits for the
  next tick.
- `infrastructure/beans/CleanupBeans.kt` — the wiring. The `CleanupScheduler` bean only exists when
  `deploy-info.cleanup.enabled=true` (`@ConditionalOnProperty`); there is no code default.

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
   safely). They may become stale debris; revisit with the settings semantics in Phase 2.

## Configuration (`deploy-info.*`)

| Property                           | Meaning                                                                                                                                                |
|------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `deploy-info.cleanup.enabled`      | Whether the periodic sweep is scheduled at all. **`false` in `application.properties`** — no profile turns it on by default; override per environment. |
| `deploy-info.cleanup.interval`     | How often the sweep runs (e.g. `PT15M`).                                                                                                               |
| `deploy-info.cleanup.grace-period` | Minimum link age before it may be judged stale (e.g. `PT10M`). Should comfortably exceed one sweep duration.                                           |

No default values are baked into the code: a profile missing one of these keys fails context startup
at binding time. `application-local.properties` intentionally does not enable the cleanup.

## Testing

`tests/application/cleanup/CleanupInvalidDeployLinksUseCaseTests.kt` — pure unit tests over the
existing fakes (no Spring, Datastore, credentials, or internet), covering: stale GAE/RUN links
deleted (repository + storage folder), live links kept, version-level identity (a newer version of
the same service does not keep an older version's link), Cloud Run location as part of the identity,
the grace period (including the exactly-at-the-boundary case), listing-failure isolation (per
project/type), storage-failure resilience, and unconfigured projects. The fakes gained failure
knobs (`throwOnProject` on `FakeAppEngineLister`, `throwOnEveryList` on `FakeCloudRunLister`,
`throwOnEveryDelete` on `FakeStorageCleaner`).
