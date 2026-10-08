# DeployInfo Collector — Gradle Plugin

Documentation of how the Gradle collector plugin (`collector-java/deploy-info-collector`) works internally.
For how to use it, see the plugin's own [README](../README.md).
For how to run the whole project locally, see [local-development.md](../../../documentation/local-development.md).

Plugin id: `io.github.ghrcosta.deploy-info-collector` · version `0.0.1` · Kotlin 2.1.10 · JVM toolchain 17.

---

## 1. Entry points

### `CollectorPlugin.kt` (`io.github.ghrcosta`)

Implements Gradle's `Plugin<Project>`. All it does is register the task:

```kotlin
project.tasks.register(CollectorTask.TASK_NAME, CollectorTask::class.java)
```

### `CollectorTask.kt` (`io.github.ghrcosta`)

A `DefaultTask` named **`deployInfoCollect`**, registered under task **group `deployInfo`**
(description: "Execute collector"). Parameters:

| Parameter             | Type                   | Required | Default            | Meaning                                                                                                                                                                              |
|-----------------------|------------------------|----------|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `deployType`          | `Property<DeployType>` | required | —                  | Type of deploy being collected. Enum `CollectorTask.DeployType` with values `GAE` (App Engine) and `RUN` (Cloud Run). Used in the output directory name and `collector.properties`.  |
| `portalUrl`           | `Property<String>`     | required | —                  | URL of the portal backend. |
| `maxFileSize`         | `Property<Int>`        | optional | `50 * 1024` (50KB) | Maximum file size (bytes) that may be collected; larger files are skipped / replaced with a placeholder.                                                                             |
| `collectGitStatus`    | `Property<Boolean>`    | optional | `true`             | Whether git-related data is collected at all.                                                                                                                                        |
| `extraFilesToCollect` | `ListProperty<String>` | optional | empty              | Extra files to copy into the upload. Paths are relative to the project root; only files (no directories); the file is collected without its parent dirs, so same-name files collide. |

All parameters are also exposed as command-line `@Option`s (e.g. `./gradlew deployInfoCollect --deploy-type=GAE`).

Every parameter is an `@get:Input`, so Gradle's up-to-date checking takes them into account.

### Execution flow (`CollectorTask.TaskImpl.run()`)

When `deployInfoCollect` runs, six steps execute in order:

1. **`Context.init(...)`** — creates output directories, detects the gcloud account, writes `collector.properties` (see §3).
2. **`GitCollector`** — if `collectGitStatus == true`, collects git data (see §4).
3. **`ExtraFilesCollector`** — if `extraFilesToCollect` is set, copies those files (see §5).
4. **`BucketLookup`** — resolves the upload bucket from the portal via `GET /collector/bucket` (see §6).
5. **`Uploader`** — writes the uuid-map property files and runs `gcloud storage cp` (see §7).
6. **`PortalTrigger`** — notifies the portal that the upload is complete so it can link the deploy (see §8).

Then logs "Done!". Errors in the bucket-lookup step (§6), the upload step (§7) or the trigger step (§8) throw and fail the task.

---

## 2. Supporting utilities (`util/`)

- **`CommandUtils.kt`** — `executeCommand` / `executeCommandOrThrowException`: run a shell command via `ProcessBuilder`, capturing stdout/stderr to temp files inside the collector base directory (reading both streams to files prevents the process from blocking on full buffers). Returns `CommandResult(stdout, stderr)`. The plain `executeCommand` swallows `IOException` and returns the error text as output; the `OrThrowException` variant propagates.
- **`SystemUtils.kt`** — on Windows, maps executables to their `.cmd` equivalents (`gcloud` → `gcloud.cmd`); a no-op elsewhere. All commands go through this.
- **`FileUtils.kt`** — `File.getContents()` (returns `<FILE IS TOO LARGE (n bytes)>`, `<FILE IS BINARY>` or `<FILE IS BLANK>` placeholders instead of content), `File.isLarge()` (larger than `Context.maxFileSize`), `File.isBinary()` (mime-type sniff via URL connection; text/json/xml/html/yaml treated as text, anything else binary), `createEmptyFile` / `createEmptyDirectory` (the latter deletes an existing dir first so contents of different deploys never mix).
- **`Logger.kt`** — thin logging wrapper around the Gradle logger.

---

## 3. `Context`

Singleton holding the state for one run:

| Field            | Meaning                                                                                         |
|------------------|-------------------------------------------------------------------------------------------------|
| `baseDir`        | `<project>/build/collector/` — base collector dir; also the cwd/temp-dir for commands.          |
| `outputDir`      | `baseDir/<user>_<DEPLOYTYPE>_<epochMillis>/` — everything collected here is what gets uploaded. |
| `projectRootDir` | The Gradle project root; base for resolving git/extra file paths.                               |
| `maxFileSize`    | `maxFileSize` task param, or 50KB.                                                              |
| `gitUuidMap`     | uuid → filepath for files collected by `GitCollector`.                                          |
| `extraUuidMap`   | uuid → filepath for files collected by `ExtraFilesCollector`.                                   |

`Context.init(project, deployType, maxFileSize)`:

1. Creates `build/collector/` (cleared first via `createEmptyDirectory`).
2. Runs `gcloud config get-value account` to get the authenticated gcloud email (throws if it fails — so the user **must** have `gcloud` installed and logged in before running the collector).
3. Output dir name: `<email-part-before-@>_<deployType>_<now-epoch-ms>`, e.g. `johndoe_GAE_1746322088662`.
4. Writes `collector.properties` into the output dir with three keys: `email`, `deploy` (`GAE`/`RUN`), `timestamp` (epoch millis). This file is uploaded along with everything else so the portal can identify who collected what and when.
   *(Phase 1 note: once `PortalTrigger` sends this info in the request body, this file becomes redundant and may be removed.)*

## 4. `GitCollector`

Executed when `collectGitStatus` is true. Writes into `Context.outputDir`:

1. **`git-status.txt`** — output of `git status --long --branch --untracked-files=normal` (the human-readable status shown on the portal).
2. **`git-log.txt`** — output of `git log --oneline`.
3. **Per changed file**: parses `git status --porcelain --untracked-files=normal --no-renames` to get the list of changed paths (supports quoted names with spaces; skips entries that don't resolve to an existing file). For each path:
   - runs `git --no-pager diff "<path>"`; if the diff is non-blank, saves it;
   - otherwise (e.g. untracked files have no diff) saves the **full file content** via `File.getContents()` — so large files become `<FILE IS TOO LARGE ...>`, binaries become `<FILE IS BINARY>`, blank files become `<FILE IS BLANK>`.
   - content is written to a file named by a fresh **`UUID`** (not the real path — avoids path/encoding issues in Storage), and `uuid → "/real/path"` is recorded in `Context.gitUuidMap` (backslashes normalized to `/`).

On `IOException` the error is logged and an `error.txt` with the message is written to the output dir instead of failing the task.

Note: paths from `git status` are relative to the **parent** of the Gradle project root (the repo root may be the parent of the module the plugin is applied to) — handled by `parsePathFromProjectRootParent`.

## 5. `ExtraFilesCollector`

Executed with the `extraFilesToCollect` list. Each entry is normalized to `/path` with `/` separators, deduplicated, resolved against the project root, and checked against the skip rules:

- skipped (with an error log) if: doesn't exist, is not a regular file, is binary, or is larger than `maxFileSize`.
- otherwise copied verbatim into `Context.outputDir` under a fresh **UUID** filename, with `uuid → "/path"` recorded in `Context.extraUuidMap`.

## 6. `BucketLookup`

Executed once per task run, right before the upload; no caching. It resolves the upload bucket from the portal
(contract: [`documentation/api.md`](../../documentation/api.md)):

1. Sends `GET {portalUrl}/collector/bucket` using plain `java.net.HttpURLConnection` — no HTTP client dependency is
   added to the published plugin jar. Connect and read timeouts are set (10 s connect, 30 s read).
2. A `200 OK` response body `{"bucket": "<name>"}` is parsed and the bucket name returned.
3. Any failure — invalid `portalUrl`, non-200 status, malformed response body, network failure or timeout — throws a
   `GradleException` naming the portal URL, failing the Gradle task before anything is uploaded.

The URL-opening is a constructor-injected seam (`(URL) -> HttpURLConnection`), which makes the action unit-testable
with mockk without touching the network (`action/BucketLookupTests.kt`).

## 7. `Uploader`

Executed with the bucket name resolved by `BucketLookup` (§6):

1. If `gitUuidMap` is non-empty, writes **`uuid-git.properties`** into the output dir (Java `Properties` format: `<uuid>=<path>`).
2. If `extraUuidMap` is non-empty, writes **`uuid-extra.properties`** the same way.
3. Runs:

```
gcloud storage cp --recursive <outputDir> gs://<bucketName>
```

Since the dir name is unique (`<user>_<type>_<timestamp>`), this creates a new **top-level folder in the bucket** with that name — the folder the portal later reads.

4. Inspects the command's **stderr** for lines starting with `ERROR` (deduplicated); if any, throws a `RuntimeException` listing them, failing the Gradle task. (Exit code is not checked; only stderr `ERROR` lines.)

## 8. `PortalTrigger`

Executed after the upload, as the last step of the task. It notifies the portal that a collection
output directory has landed in the bucket, so the portal can link the upload to the GCP deploy it
was built for.

It calls `POST {portalUrl}/collector/handleNewDirectory` (the contract is documented in
`documentation/api.md`) with a JSON body of `directoryName` (the output directory name,
`<user>_<deployType>_<epochMillis>`), `projects` (the task's project list), `deployType` and
`userEmail` (the gcloud account). The body is serialized from `PortalTriggerDTOs.kt`'s
`TriggerRequest` with **Gson** (the only third-party runtime dependency of the plugin); the HTTP
call itself uses plain `java.net.HttpURLConnection` so that no HTTP client dependency is added,
mirroring `BucketLookup` (same test seam, timeouts of 10s/30s).

Response handling:

- `200` with `created` / `already-linked` — logs the link (project/service/version).
- `202` with `pending` — logged only; the portal owns the matching and its 5-minute retry, so this
  does not fail the build (the portal may still link the upload or delete its folder later).
- `422` `unknown-project` — fails the task: none of the listed projects is configured in the portal.
- `400` or any other HTTP status, connection/timeout, unwritable request or unreadable/malformed
  response — fails the task with an error naming the portal URL.

The response is deserialized from `TriggerResponse` with Gson; a non-JSON or empty body fails the
task.

## 9. Resulting Cloud Storage folder layout

The upload creates a new top-level folder per collection run:

```
gs://<bucketName>/
└── <user>_<DEPLOYTYPE>_<epochMillis>/      # e.g. john.doe_GAE_1746322088662
    ├── collector.properties               # email=... , deploy=GAE|RUN , timestamp=<epoch ms>
    ├── git-status.txt                     # `git status --long --branch` output
    ├── git-log.txt                        # `git log --oneline` output
    ├── <uuid>                             # diff or full content of one changed file
    ├── <uuid>                             #   (paths in uuid-git.properties)
    ├── ...
    ├── uuid-git.properties                # <uuid>=<path> for all git-collected files
    ├── <uuid>                             # verbatim copy of one extra file
    ├── ...
    ├── uuid-extra.properties              # <uuid>=<path> for all extra files
    └── error.txt                          # only when GitCollector hit an IOException
```

`git-status.txt` / `git-log.txt` are absent when `collectGitStatus=false`, and uuid files / maps only exist when something was actually collected. The backend file-content serving (Phase 1) relies on exactly this contract: directory name encodes user/deployType/timestamp, `collector.properties` carries the same data, and file paths for uuid files come from the two properties files.

