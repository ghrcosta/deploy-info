# DeployInfo Collector — Gradle Plugin

Documentation of how the Gradle collector plugin (`collector-java/deploy-info-collector`) works internally.
For how to use it, see the plugin's own [README](../README.md).
For how to run the whole project locally, see [local-development.md](../../../documentation/local-development.md).

Plugin id: `io.github.ghrcosta.deploy-info-collector`.

---

## 1. Entry points

### `CollectorPlugin.kt` (`io.github.ghrcosta`)

Implements Gradle's `Plugin<Project>`. All it does is register the data collection task.

### `CollectorTask.kt` (`io.github.ghrcosta`)

A `DefaultTask` named **`deployInfoCollect`**, registered under task **group `deployInfo`**
(description: "Execute collector"). Parameters:

<table>
  <tr><th>Parameter</th><th>Type</th><th>Required</th><th>Default</th><th>Meaning</th></tr>
  <tr>
    <td><code>deployType</code></td>
    <td><code>Property&lt;DeployType&gt;</code></td>
    <td>required</td>
    <td>—</td>
    <td>Type of deploy being collected. Enum <code>CollectorTask.DeployType</code> with values <code>GAE</code>
      (App Engine) and <code>RUN</code> (Cloud Run). Used in the output directory name and sent to the portal by
      <code>PortalTrigger</code>.</td>
  </tr>
  <tr>
    <td><code>portalUrl</code></td>
    <td><code>Property&lt;String&gt;</code></td>
    <td>required</td>
    <td>—</td>
    <td>URL of the portal backend.</td>
  </tr>
  <tr>
    <td><code>maxFileSize</code></td>
    <td><code>Property&lt;Int&gt;</code></td>
    <td>optional</td>
    <td><code>50 * 1024</code> (50KB)</td>
    <td>Maximum file size (bytes) that may be collected; larger files are skipped / replaced with a placeholder.</td>
  </tr>
  <tr>
    <td><code>collectGitStatus</code></td>
    <td><code>Property&lt;Boolean&gt;</code></td>
    <td>optional</td>
    <td><code>true</code></td>
    <td>Whether git-related data is collected at all.</td>
  </tr>
  <tr>
    <td><code>extraFilesToCollect</code></td>
    <td><code>ListProperty&lt;String&gt;</code></td>
    <td>optional</td>
    <td>empty</td>
    <td>Extra files to copy into the upload. Paths are relative to the project root; only files (no directories); the
      file is collected without its parent dirs, so same-name files collide.</td>
  </tr>
</table>

All parameters are also exposed as command-line `@Option`s (e.g. `./gradlew deployInfoCollect --deploy-type=GAE`).

Every parameter is an `@get:Input`, so Gradle's up-to-date checking takes them into account.

### Execution flow (`CollectorTask.TaskImpl.run()`)

When `deployInfoCollect` runs, six steps execute in order:

1. **`Context.init(...)`** — creates output directories and detects the gcloud account (see §3).
2. **`GitCollector`** — if `collectGitStatus == true`, collects git data (see §4).
3. **`ExtraFilesCollector`** — if `extraFilesToCollect` is set, copies those files (see §5).
4. **`BucketLookup`** — resolves the upload bucket from the portal via `GET /collector/bucket` (see §6).
5. **`Uploader`** — writes the uuid-map CSV / properties files and runs `gcloud storage cp` (see §7).
6. **`PortalTrigger`** — notifies the portal that the upload is complete so it can link the deploy (see §8).

Then logs "Done!". Errors in the bucket-lookup step (§6), the upload step (§7) or the trigger step (§8) throw and fail
the task.

---

## 2. Supporting utilities (`util/`)

- **`CommandUtils.kt`** — `executeCommand` / `executeCommandOrThrowException`: run a shell command via `ProcessBuilder`,
  capturing stdout/stderr to temp files inside the collector base directory (reading both streams to files prevents the
  process from blocking on full buffers). Returns `CommandResult(stdout, stderr)`. The plain `executeCommand` swallows
  `IOException` and returns the error text as output; the `OrThrowException` variant propagates.
- **`SystemUtils.kt`** — on Windows, maps executables to their `.cmd` equivalents (`gcloud` → `gcloud.cmd`); a no-op
  elsewhere. All commands go through this.
- **`FileUtils.kt`** — `File.getContents()` (returns `<FILE IS TOO LARGE (n bytes)>`, `<FILE IS BINARY>` or `<FILE IS
  BLANK>` placeholders instead of content), `File.isLarge()` (larger than `Context.maxFileSize`), `File.isBinary()`
  (mime-type sniff via URL connection; text/json/xml/html/yaml treated as text, anything else binary), `createEmptyFile`
  / `createEmptyDirectory` (the latter deletes an existing dir first so contents of different deploys never mix).
- **`CsvUtils.kt`** — Escapes `uuid-git.csv`
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
| `gitUuidMap`     | uuid → filepath + new/modified/untracked state for files collected by `GitCollector`. |
| `extraUuidMap`   | uuid → filepath for files collected by `ExtraFilesCollector`.                                   |

`Context.init(rootDir, buildDir, deployType, maxFileSize)`:

1. Creates `build/collector/` (cleared first via `createEmptyDirectory`).
2. Runs `gcloud config get-value account` to get the authenticated gcloud email (throws if it fails — so the user
**must** have `gcloud` installed and logged in before running the collector).
3. Output dir name: `<email-part-before-@>_<deployType>_<now-epoch-ms>`, e.g. `johndoe_GAE_1746322088662`. The directory
name is the sole carrier of who/what/when: `PortalTrigger` sends it (plus `deployType` and `userEmail`) to the portal,
and the portal derives the collection timestamp from the epoch-millis suffix — no metadata file is written into the
output directory.

## 4. `GitCollector`

Executed when `collectGitStatus` is true. Writes into `Context.outputDir`:

1. **`git-status.txt`** — output of `git status --long --branch --untracked-files=normal` (the human-readable status
shown on the portal).
2. **`git-log.txt`** — output of `git log --oneline`.
3. **Per changed file**: parses `git status --porcelain --untracked-files=normal --no-renames` to get the list of
changed paths (supports quoted names with spaces; skips entries that don't resolve to an existing file). Porcelain paths
are always relative to the **git repository root** (which may differ from the Gradle project root, e.g. when the repo
root is a parent of the module the plugin is applied to). They are resolved to absolute paths against the repository
root obtained from `git rev-parse --show-toplevel` (falling back to the Gradle project root if the command fails). This
works for any repo/module layout, on both Linux and Windows. For each path:
- the porcelain 2-char code decides the file's state: `??` → **untracked**; a code containing `A` (staged addition, e.g.
     `A `, `AM`) → **new**; any other code (`M`, `MM`, `D`, ...) → **modified**;
   - for 'modified' files, runs `git --no-pager diff "<path>"`; if the diff is non-blank, saves it;
- otherwise (e.g. new/untracked files have no diff) saves the **full file content** via `File.getContents()` — so large
     files become `<FILE IS TOO LARGE ...>`, binaries become `<FILE IS BINARY>`, blank files become `<FILE IS BLANK>`.
- content is written to a file named by a fresh **`UUID`** (not the real path — avoids path/encoding issues in Storage),
     and `uuid → "/real/path" + git state (new/untracked/modified)` is recorded in `Context.gitUuidMap` (backslashes
     normalized to `/`).

On `IOException` the error is logged and an `error.txt` with the message is written to the output dir instead of failing
the task.

## 5. `ExtraFilesCollector`

Executed with the `extraFilesToCollect` list. Each entry is normalized to `/path` with `/` separators, deduplicated,
resolved against the project root, and checked against the skip rules:

- skipped (with an error log) if: doesn't exist, is not a regular file, is binary, or is larger than `maxFileSize`.
- otherwise copied verbatim into `Context.outputDir` under a fresh **UUID** filename, with `uuid → "/path"` recorded in
  `Context.extraUuidMap`.

## 6. `BucketLookup`

Executed once per task run, right before the upload; no caching. It resolves the upload bucket from the portal
(contract: [`documentation/api.md`](../../../documentation/api.md)):

1. Sends `GET {portalUrl}/collector/bucket` using plain `java.net.HttpURLConnection` — no HTTP client dependency is
   added to the published plugin jar. Connect and read timeouts are set (10 s connect, 30 s read).
2. A `200 OK` response body `{"bucket": "<name>"}` is parsed and the bucket name returned.
3. Any failure — invalid `portalUrl`, non-200 status, malformed response body, network failure or timeout — throws a
   `GradleException` naming the portal URL, failing the Gradle task before anything is uploaded.

The URL-opening is a constructor-injected seam (`(URL) -> HttpURLConnection`), which makes the action unit-testable
with mockK without touching the network (`action/BucketLookupTests.kt`).

## 7. `Uploader`

Executed with the bucket name resolved by `BucketLookup` (§6):

1. If `gitUuidMap` is non-empty, writes **`uuid-git.csv`** into the output dir with header `uuid,filepath,status`.
2. If `extraUuidMap` is non-empty, writes **`uuid-extra.properties`** the same way (Java `Properties` format:
`<uuid>=<path>`).
3. Runs:

```
gcloud storage cp --recursive <outputDir> gs://<bucketName>
```

Since the dir name is unique (`<user>_<type>_<timestamp>`), this creates a new **top-level folder in the bucket** with
that name — the folder the portal later reads.

4. Inspects the command's **stderr** for lines starting with `ERROR` (deduplicated); if any, throws a `RuntimeException`
listing them, failing the Gradle task. (Exit code is not checked; only stderr `ERROR` lines.)

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
    ├── git-status.txt                     # `git status --long --branch` output
    ├── git-log.txt                        # `git log --oneline` output
    ├── <uuid>                             # diff or full content of one changed file
    ├── <uuid>                             #   (paths in uuid-git.csv)
    ├── ...
    ├── uuid-git.csv                       # uuid,filepath,status for all git-collected files
    ├── <uuid>                             # verbatim copy of one extra file
    ├── ...
    ├── uuid-extra.properties              # <uuid>=<path> for all extra files
    └── error.txt                          # only when GitCollector hit an IOException
```

`git-status.txt` / `git-log.txt` are absent when `collectGitStatus=false`, and uuid files / maps only exist when
something was actually collected. The backend file-content serving relies on exactly this contract: the directory name
encodes user/deployType/timestamp (the portal receives the rest of the metadata — projects, deployType, userEmail — via
the trigger request body, see `documentation/api.md`), and file paths (plus the git new/untracked/modified state) for
uuid files come from `uuid-git.csv` (git files) and `uuid-extra.properties` (extras).
