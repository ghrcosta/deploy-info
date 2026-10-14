# Deployment cleanup

A sweep that detects deployment links whose deployment no longer exists in GCP and removes them — both the
Datastore entry and the corresponding Cloud Storage folder.

## Trigger

The sweep runs from tree requests (`GET /portal/tree`) when due, **not** on a fixed schedule: the portal
runs on App Engine Standard, where idle instances are terminated after ~15 minutes, so a Spring-scheduled
job either never fires (long interval, dead instance) or keeps an instance alive forever (short interval).
Tree requests are the app's natural heartbeat, so the sweep piggybacks on them:

1. Disabled (`deploy-info.cleanup.enabled=false`) → no-op; nothing is read or written.
2. The last completed sweep is younger than `deploy-info.cleanup.interval` → skip; a normal tree request
   pays no sweep cost.
3. Otherwise, the sweep runs; a sweep that throws is logged and **not** marked completed, so the next tree
   request retries immediately.

## How it works

For every configured project and both deployment types, the deployments are listed from GCP (same listers as the
linking logic, see [gcp-deployment-listing.md](gcp-deployment-listing.md)) and compared against the links in
database. A link is **stale** when its identity (service id, version id, location — location only for
Cloud Run) is absent from the listing. Nothing is returned; the outcome is reported via log messages. The
only persisted state is the last-completed timestamp the due-check reads.

## Concurrency

No claim/lock is used: two backend instances may run the sweep at the same time, and this is accepted as
merely **redundant**, not dangerous:

- Every delete is idempotent and delete-only-on-proof — a link is deleted only when a successful listing
  proves its deployment is gone, the Datastore entry goes first, and a second delete of the same
  already-deleted link/folder is a no-op.
- The worst case is duplicated GCP listing API calls and duplicated work, both bounded by the number of
  instances and the once-per-interval trigger.
- If an instance dies mid-sweep, nothing was marked completed, so the next tree request retries.

## Safety rules

1. **Delete only on proof.** A link is deleted only when a *successful* listing proves its deployment is
   gone. A listing or credentials failure for a project/deployment type skips that combination entirely — a
   transient failure never deletes data.
2. **Grace period.** Links younger than `deploy-info.cleanup.grace-period` are never judged. This guards
   against the snapshot race: the sweep compares links against a point-in-time listing; a deployment that
   finishes *while the sweep runs* is live but absent from the snapshot, so a freshly created link could
   look "stale" to the same sweep that took the snapshot.
3. **Delete order.** The Datastore entry goes first, then the Cloud Storage folder. A storage failure is
   logged at WARNING and the sweep continues — this can leave an orphaned folder as debris, but never a
   link pointing at a folder that was deleted underneath it.
4. **Unconfigured projects.** Links whose project is no longer configured in the portal are left
   untouched (without the project's service-account configuration they cannot even be listed safely).
   They appear ungrouped in the navigator tree until manually removed.

## Configuration (`deploy-info.*`)

<table>
  <thead>
    <tr>
      <th>Property</th>
      <th>Meaning</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>deploy-info.cleanup.enabled</code></td>
      <td>
        Whether the trigger is active. <code>true</code> on prod, <code>false</code> on other environments.
      </td>
    </tr>
    <tr>
      <td><code>deploy-info.cleanup.interval</code></td>
      <td>Minimum time between completed sweeps (15 minutes by default).</td>
    </tr>
    <tr>
      <td><code>deploy-info.cleanup.grace-period</code></td>
      <td>
        Minimum link age before it may be judged stale (10 minutes by default). Should comfortably exceed one
        sweep duration.
      </td>
    </tr>
  </tbody>
</table>
