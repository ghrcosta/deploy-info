# Main Screen — Navigator Tree & File Content (Phase 1.2 item 2)

The REST contracts the deploy-viewer screen (navigator tree + file viewer) is built on. The wire-level
shapes (paths, request/response bodies, status codes) are the shared reference in
[`docs/api.md`](../../docs/api.md); this doc covers the design decisions behind them. The core
capabilities behind them already existed (the tree's data sources, `GetDeployContentUseCase`); this
feature adds the tree use case and the `PortalController` endpoints that expose both, plus the
on-request cleanup trigger wired into the tree endpoint (see `deploy-cleanup.md`).

## Data sources

- **The tree** is built purely from Datastore: the configured projects (their `group` field is the
  top-level grouping) and the deploy links (`DeployLinkRepository.getAll()`). No GCP listing calls —
  a listing failure can never fail a tree request; link validity is the cleanup sweep's job.
- **The file content** is read purely from Cloud Storage: the version nodes carry the upload's
  `storageFolder` (the collector's output directory name — no bucket or GCS coupling), so the content
  endpoint never needs Datastore. No identity lookup is left in it at all.

## Tree model (`application/tree/GetDeployTreeUseCase.kt`)

`GroupEntry(name, projects)` > `ProjectEntry(name, services)` > `ServiceEntry(name, type, versions)`
> `VersionEntry(id, name, type, location, url, author, timestamp, storageFolder)`. Structure and
sorting mirror the frontend navigator: services are keyed by (deploy type, service id) — the same
service name may exist as a GAE and a RUN service; every level is sorted by name; ungrouped projects
(and links of no-longer-configured projects) go under the empty-name group, sorted last.

The version node's `id` is the deploy identity's key name
(`<project>_<TYPE>_<location|->_<service>_<version>`, i.e. `DeployLink.keyName`); `name` is the
version id. The remaining fields feed the file-viewer header (link/author/timestamp/type/location)
and the content endpoint (`storageFolder`).

## REST layer (`ui/portal/`)

- `GET /portal/tree` → `List<GroupDTO>`; the on-request cleanup trigger (`RunCleanupIfDueUseCase`)
  runs first when due — see `deploy-cleanup.md` for why (App Engine Standard kills idle instances,
  so there is no fixed scheduler); concurrent sweeps from multiple instances are accepted as
  redundant — every delete is idempotent (see `deploy-cleanup.md`).
- `GET /portal/deploy/content?folder=<storageFolder>` → `200` `DeployContentDTO`
  (`git.gitlog` / `git.gitstatus` / `git.changes[]` / `extras[]`, field names matching the
  frontend's `DeployData`), `404` when the folder is absent/empty (e.g. cleaned up in the meantime),
  `502` when Cloud Storage itself fails (portal-side issue).

Wiring: `infrastructure/beans/PortalUseCaseBeans.kt` (tree + content use cases and the lazily
resolved `FileContentReader`) and `infrastructure/beans/CleanupBeans.kt` (sweep + trigger).

## Testing

- `tests/application/tree/GetDeployTreeUseCaseTests.kt` — grouping/sorting at all levels, the
  empty group sorted last, the same service as GAE and RUN, Cloud Run location in the identity,
  all version-node fields, unconfigured-project links, empty tree.
- `tests/application/content/GetDeployContentUseCaseTests.kt` — updated for the folder-only input
  (full folder, no-git, status-only, missing uuid-map files, absent folder → `FolderNotFound`,
  reader failure → `StorageError`).
- `tests/application/cleanup/RunCleanupIfDueUseCaseTests.kt` — the trigger timing rules (see
  `deploy-cleanup.md`).

## Explicitly out of scope

The frontend wiring (Phase 1 §5) — replacing the `EXAMPLE_DATA_*` mocks and the fake `DeployData`
simulation in `ui/src/app/deploy-viewer/` — is the next item and consumes exactly these contracts.
