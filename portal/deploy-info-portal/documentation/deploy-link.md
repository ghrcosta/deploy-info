# Deploy Link — Datastore Model & Repository

The Phase 1 model that links a collector upload (a Cloud Storage folder) to the GCP deploy it was
built for. Design decisions are recorded here; the linking logic that creates these records is a
separate TODO item.

## Data sources

- **From the collector's trigger request** (Phase 1 item 3): project ID, deploy type, storage
  folder (output dir name), user email, collection timestamp (encoded in the dir name).
- **From the backend's GCP listing clients** (linking logic): service id, version/revision id,
  Cloud Run region, public URL.

The collector does not send service/version/location — it cannot know them reliably; the portal
matches the upload to the most recent deploy of that type.

## Model (`domain/DeployLink.kt`)

`data class DeployLink(projectId, deployType: DeployType, serviceId, versionId, location?,
storageFolder, userEmail, collectTimestamp: Instant, deployTimestamp: Instant, url?)`.

`deployTimestamp` is the GCP deploy's creation time (from the listing, like
service/version/location/url). It disambiguates a version id that is deployed again: identity is
the full tuple **(projectId, deployType, serviceId, versionId, location)** — the
location is part of the identity because Cloud Run revision ids repeat across regions (null for
App Engine) — but the identity alone cannot tell "the same deploy is already linked" from "a new
deploy reused the version id". The `keyName` computed property encodes this identity as a
Datastore string key: `<project>_<TYPE>_<location|->_<service>_<version>` (`-` for the null GAE
location).

## Repository (`application/DeployLinkRepository.kt`)

- `get(projectId, deployType, location, serviceId, versionId): DeployLink?`
- `getAllFor(projectId, deployType): List<DeployLink>` — the lookup the linking logic uses for
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

## Redeploys (version id reuse)

When the linking logic matches a deploy whose identity already has a link, it compares the
existing link's `deployTimestamp` with the matched deploy's creation time:

- **Equal** — it is the very same deploy (e.g. a duplicate trigger): `AlreadyLinked`, the existing
  link and its storage folder are left untouched.
- **Different** — a new deploy reused the version id: the new link replaces the old one (the
  Datastore key is the identity, so `save` upserts over it) and the replaced link's storage folder
  is deleted. The save happens first, so a deletion failure can only leave an orphaned folder as
  debris — never a link pointing at a deleted folder (same tradeoff as the cleanup sweep).

Known limitation (follow-up): the cleanup sweep still judges staleness by identity only
(`(serviceId, versionId, location)`), so it cannot detect a link whose version id still exists in
GCP but whose deploy was replaced outside the portal's linking (e.g. the collector was never run
for the new deploy). Comparing `deployTimestamp` against the listing was deliberately not done:
redeploying over an existing App Engine version id updates the version in place, and its
`createTime` semantics would risk false deletions.

## Migration

`deployTimestamp` did not exist before: any deploy-link rows created earlier (local/emulator data)
must be deleted once after upgrading, since the new field is required on read.