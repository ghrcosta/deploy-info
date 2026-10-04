# Deploy Link — Datastore Model & Repository

The Phase 1 model that links a collector upload (a Cloud Storage folder) to the GCP deploy it was
built for. Design decisions are recorded here; the linking logic that creates these records is a
separate TODO item.

## Data sources

- **From the collector's trigger request** (Phase 1 item 3): project name, deploy type, storage
  folder (output dir name), user email, collection timestamp (encoded in the dir name).
- **From the backend's GCP listing clients** (linking logic): service id, version/revision id,
  Cloud Run region, public URL.

The collector does not send service/version/location — it cannot know them reliably; the portal
matches the upload to the most recent deploy of that type.

## Model (`domain/DeployLink.kt`)

`data class DeployLink(projectName, deployType: DeployType, serviceId, versionId, location?,
storageFolder, userEmail, collectTimestamp: Instant, url?)`.

Identity is the full tuple **(projectName, deployType, serviceId, versionId, location)** — the
location is part of the identity because Cloud Run revision ids repeat across regions (null for
App Engine). The `keyName` computed property encodes this identity as a Datastore string key:
`<project>_<TYPE>_<location|->_<service>_<version>` (`-` for the null GAE location).

## Repository (`application/DeployLinkRepository.kt`)

- `get(projectName, deployType, location, serviceId, versionId): DeployLink?`
- `getAllFor(projectName, deployType): List<DeployLink>` — the lookup the linking logic uses for
  "most recent, not yet linked" matching
- `save(deployLink)` — upsert (saving an existing identity replaces it)
- `delete(deployLink)` — used later by the validity/cleanup logic

## Infrastructure (`infrastructure/gcp/datastore/`)

`DeployLinkEntity` (`@Entity(name = "deploy-info/deploy-link")`, `@Id String id` = the keyName)
and `DeployLinkDatastoreRepository` (`@Repository` over `DatastoreTemplate`, picked up by
component scan, no bean config needed) — same pattern as the `Project*` classes.

## Testing

`tests/fakes/FakeDeployLinkRepository.kt` (in-memory) and
`tests/infrastructure/gcp/datastore/DeployLinkDatastoreRepositoryTests.kt` (mocked
`DatastoreTemplate`, covering GAE and Cloud Run round-trips, identity lookup, getAllFor filtering,
and delete). No emulator, credentials, or internet access required.