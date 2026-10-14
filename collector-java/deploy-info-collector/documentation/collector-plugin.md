# DeployInfo Collector — Gradle Plugin

How the Gradle collector plugin (`collector-java/deploy-info-collector`) works internally.
For how to use it, see the plugin's [README](../README.md); for the trigger/bucket contracts, see
[api.md](../../../documentation/api.md); for running the whole project locally, see
[local-development.md](../../../documentation/local-development.md).

Plugin id: `io.github.ghrcosta.deploy-info-collector`.

## Gradle feature compatibility

To publish the plugin to Gradle Plugin Portal, it needs to declare compatibility status with two
Gradle features: Configuration cache and Isolated projects. These were validated using functional
tests.

## Execution flow

When the `deployInfoCollect` task runs, six steps execute in order:

1. **Setup** — the output directories are created (cleared first, so contents of different deployments never
   mix) and the authenticated gcloud account is detected; failing that, the task fails — gcloud must be
   installed and logged in before the collector can run.
2. **Git collection** (when enabled) — see below.
3. **Extra files** (when configured) — see below.
4. **Bucket lookup** — the upload bucket is resolved from the portal (see below).
5. **Upload** — the uuid-map files are written and the output directory is uploaded (see below).
6. **Portal trigger** — the portal is notified that the upload is complete (see below).

Errors in the bucket lookup, the upload or the trigger fail the task.

The output directory is named `<user>_<DEPLOYTYPE>_<epochMillis>` (gcloud account, deployment type, epoch
millis). The name is the sole carrier of who/what/when — no metadata file is written; the portal derives
the collection timestamp from the epoch-millis suffix.

## Git collection

Writes into the output directory:

- `git-status.txt` — output of `git status --long --branch` (the human-readable status shown in the
  portal).
- `git-log.txt` — output of `git log --oneline`, capped at the configured `maxGitLogLines` entries
  (default 30); when the history was cut, the file ends with a `... (older commits omitted)` line.
- Per changed file: the porcelain status lists the changed paths (relative to the **git repository
  root**, which may differ from the Gradle project root, e.g. when the repo root is a parent of the
  module). Each file is stored under a fresh UUID file name (avoids path/encoding issues in Storage),
  and its repo-root-relative path (as reported by porcelain, with a leading `/`) plus state is
  recorded in the `uuid-git.csv` map:
  - porcelain code `??` → **untracked**; a staged addition → **new**; any other code → **modified**;
  - modified files store their `git diff`; new/untracked files (which have no diff) store the full file
    content;
  - large files become `<FILE IS TOO LARGE (n bytes)>`, binaries `<FILE IS BINARY>`, blank files
    `<FILE IS BLANK>`.

On a git failure the error is logged and an `error.txt` with the message is written to the output
directory instead of failing the task.

## Extra files

Each configured path is resolved against the project root and copied verbatim into the output directory
under a fresh UUID, recorded in the `uuid-extra.properties` map. Files that do not exist, are not
regular files, are binary or larger than the size cap are skipped (with an error log).

## Bucket lookup

Before uploading, the collector asks the portal (`GET /collector/bucket`) which Cloud Storage bucket to
use, so the bucket name is configured only on the portal. Any failure (invalid portal URL, non-200
status, malformed response body, network failure or timeout) fails the task before anything is uploaded.

## Upload

1. If git files were collected, `uuid-git.csv` is written (`uuid,filepath,status`, where `filepath` is
   relative to the git repository root with a leading `/`; RFC 4180 escaping so quoted file paths
   survive commas/quotes).
2. If extra files were collected, `uuid-extra.properties` is written (`<uuid>=<path>`, Java Properties
   format).
3. A `gcloud storage` command creates a new top-level folder in the bucket named after the output
   directory — the folder the portal later reads.
4. The command's stderr is inspected for `ERROR` lines (deduplicated); any such line fails the task (the
   exit code is not checked).

## Portal trigger

After the upload, the collector notifies the portal: `POST {portalUrl}/collector/handleNewDirectory`
with the output directory name, the project list, the deployment type and the user email (contract:
[api.md](../../../documentation/api.md)).

Response handling:

- `200` `created` / `already-linked` — the link (project/service/version) is logged.
- `202` `pending` — logged only; the portal owns the matching and its retry, so this does not fail the
  build (the portal may still link the upload or delete its folder later).
- `422` `unknown-project` — fails the task: none of the listed projects is configured in the portal.
- Any other status, connection/timeout or unreadable/malformed response — fails the task with an error
  naming the portal URL.

## Authentication

Both portal calls carry a gcloud access token of the active account (`gcloud auth print-access-token`)
as the `Authorization: Bearer` header. When no token can be obtained, the calls are sent without the
header — a portal with authentication disabled (local development) works unchanged.

## Resulting Cloud Storage folder layout

The upload creates a new top-level folder per collection run:

```
gs://<bucket>/
└── <user>_<DEPLOYTYPE>_<epochMillis>/   e.g. john.doe_GAE_1746322088662
    ├── git-status.txt                   git status output
    ├── git-log.txt                      git log output
    ├── <uuid>                           diff or full content of one changed file (paths in uuid-git.csv)
    ├── uuid-git.csv                     uuid,filepath,status for all git-collected files
    ├── <uuid>                           verbatim copy of one extra file
    ├── uuid-extra.properties            uuid=path for all extra files
    └── error.txt                        only when git collection failed
```

`git-status.txt` / `git-log.txt` are absent when git collection is disabled, and uuid files / maps only
exist when something was actually collected.
