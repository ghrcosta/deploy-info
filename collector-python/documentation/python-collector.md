# DeployInfo Collector — Python script

How the standalone Python collector (`collector-python/deploy_info_collector.py`) works internally. It is
a faithful port of the Gradle collector plugin — see its
[documentation](../../collector-java/deploy-info-collector/documentation/collector-plugin.md) for the
shared behavior. For how to use it, see the [README](../README.md).

Single file, standard library only, Python ≥ 3.10, Windows + Linux.

## Execution flow

The run executes the same six steps as the Gradle plugin, in order: setup (output directories, cleared
first, plus gcloud account detection), git collection (when enabled), extra files (when configured),
bucket lookup, upload, and the portal trigger. One gcloud access token is fetched before the portal calls
and reused for both; if it cannot be obtained, the calls are sent without the `Authorization` header (a
portal with authentication disabled works unchanged).

Errors in the bucket lookup, the upload or the trigger fail the run (exit code 1; exit code 2 means
malformed arguments — see the README for the full exit-code list).

The output directory is named `<user>_<DEPLOYTYPE>_<epochMillis>`; the name is the sole carrier of
who/what/when — no metadata file is written.

## Command execution and file handling

- Commands run **without a shell**, tokenized with a quote-aware rule, so quoted paths with spaces work.
  Stdout and stderr are captured separately (stderr never merged into stdout) and read back with explicit
  UTF-8 — Windows' locale default would otherwise mangle git output. Spawn failures are swallowed (an
  error text is used as output) except where a failure must abort the run. Exit codes of spawned commands
  are not checked anywhere.
- On Windows, `gcloud` maps to `gcloud.cmd`; there is no other OS branch.
- Files above the size cap, binaries and blank files are recorded as `<FILE IS TOO LARGE (n bytes)>`,
  `<FILE IS BINARY>` and `<FILE IS BLANK>` placeholders instead of content.
- `uuid-git.csv` uses RFC 4180 escaping; `uuid-extra.properties` replicates Java's Properties format
  (`#<date>` comment first, `<uuid>=<path>` per entry) because the portal parses it with a Java
  Properties loader.

## Differences from the Gradle plugin

Behavior is a faithful port — same files, same uuid maps, same `gcloud storage cp` upload (including the
stderr-`ERROR`-lines-only failure check that ignores the exit code), same `POST
/collector/handleNewDirectory` trigger with the same response handling, same `error.txt` on git
collection failure, same token-less fallback. The only intentional differences:

- **CLI surface**: `--max-file-size` is in **KB** (default 50); the plugin takes bytes. Downstream
  behavior keeps byte parity.
- **No Gradle up-to-date checking** — a script run always executes.
- **`--project-dir`** replaces the plugin's project/build-directory pair; output always goes to
  `<project-dir>/build/collector/`.
- The plugin hooks into deploy tasks via Gradle (`finalizedBy`); the script is meant to be invoked by
  CI/deploy tooling after a deploy.

## Resulting Cloud Storage folder layout

The same contract as the Gradle plugin — see the folder-layout section of
[collector-plugin.md](../../collector-java/deploy-info-collector/documentation/collector-plugin.md).
