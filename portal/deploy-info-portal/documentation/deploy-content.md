# Deploy Content — File-Content Capability

The Phase 1 capability that reads the collected files (GIT section and extras) out of a collector
upload's Cloud Storage folder, for the deploy a viewer selects in the navigator. The storage folder
layout is the one produced by the collector (see the collector plugin documentation, §8); this
feature is the backend counterpart of that contract.

## Data sources

- **The deploy link** (`domain/DeployLink.kt`): the input is a deploy identity
  `(projectName, deployType, serviceId, versionId, location)` — the same tuple the frontend tree
  carries per version node. The link provides the upload's storage folder; if no link exists, the
  deploy has no collected content.
- **The Cloud Storage folder**: `git-log.txt`, `git-status.txt`, uuid-named diff/full-content files
  with `uuid-git.properties` (`<uuid>=<path>`), and verbatim extra files with
  `uuid-extra.properties`. Every part is optional: git files only exist when the collector was
  configured to collect them, the uuid maps only when something was collected.

## Model (`domain/DeployContent.kt`)

- `ContentFile(filepath, content)` — one collected file with its original repository path (from the
  uuid map) and its stored text.
- `GitContent(gitlog?, gitstatus?, changes: List<ContentFile>)` — the GIT section; `null` in
  `DeployContent` when the upload contains no git data at all.
- `DeployContent(git?, extras: List<ContentFile>)` — the whole folder's content; `extras` is empty
  when there are none.

Field names mirror the frontend's `DeployData` (`git.gitlog`, `git.gitstatus`, `git.changes`,
`extras`), so the REST contract (Phase 1 §4) can map this 1:1.

## Interface (`application/FileContentReader.kt`)

`readText(storageFolder, fileName): String?` — the text of one file in the folder, null when the
file does not exist, `domain/StorageReadException` when the storage backend fails. Plus
`listFileNames(storageFolder): List<String>` — the folder's file names (empty when the folder is
absent/empty), used to detect an upload folder that is gone. The bucket is an infrastructure
concern; the interface works with the folder name only.

## Use case (`application/content/GetDeployContentUseCase.kt`)

- Input: the upload's `storageFolder` (carried by the tree's version nodes — this capability touches
  Cloud Storage only, never Datastore; the identity → folder mapping lives in the tree + Datastore).
- Reads the folder through `FileContentReader`: first `listFileNames` (an absent/empty folder →
  `Output.FolderNotFound`, e.g. the cleanup sweep removed it), then the two git files plus both uuid
  maps; each uuid file's content is resolved back to its original path. Entries referencing files
  that are missing from the folder are skipped (partial uploads must not fail the whole read).
- Output: `Content(DeployContent)` / `FolderNotFound` / `StorageError` (portal-issue failure).
- Single-response design: log, status, all changes and all extras come back in one `DeployContent`,
  matching the frontend's single fetch per deploy click (files are size-capped by the collector's
  `maxFileSize`, so the payload stays bounded).

## Infrastructure (`infrastructure/gcp/storage/GcsFileContentReader.kt`)

Reads blobs with the Cloud Storage client from the Spring Cloud GCP Storage starter; same lazy
`Storage`-provider pattern as `GcsStorageCleaner` (the portal starts without Storage credentials).
All GCS failures are wrapped in `StorageReadException`.

## Spring wiring (`infrastructure/beans/PortalUseCaseBeans.kt`)

`fileContentReader` and `getDeployContentUseCase` beans in `PortalUseCaseBeans` (the linking beans
live in `CollectorUseCaseBeans`); reuses the existing `storageBucket` property — no new
configuration. The same property is exposed to the collector via the `GET /collector/bucket`
endpoint (see `documentation/api.md` in the repository root), so the collector resolves its upload
target from the portal.

## Testing

`tests/fakes/FakeFileContentReader.kt` (in-memory, with a fail-all-reads switch) and
`tests/application/content/GetDeployContentUseCaseTests.kt`: full folder (paths from the uuid maps),
no-git folder, empty/absent folder, status-only folder, uuid-map entry with a missing file, reader
failure. No emulator, credentials, or internet access required.

## Explicitly out of scope

The frontend wiring (§5) — this capability is the core the REST layer (`PortalController`, Phase
1.2, see `main-screen.md`) and the frontend build on.
