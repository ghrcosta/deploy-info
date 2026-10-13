# deploy-info-collector (Python)

Standalone Python script to collect files and make them available to the portal — a drop-in alternative
to the [Gradle collector plugin](../collector-java/deploy-info-collector/README.md) for projects not
built with Gradle (or CI pipelines that prefer a plain script).

Single file (`deploy_info_collector.py`), no dependencies beyond the Python 3.10+ standard library, git
and an authenticated gcloud CLI. After collecting, it uploads the files to Cloud Storage and notifies the
portal ([trigger contract](../documentation/api.md)), which links the upload to the GCP deploy it was
built for. The upload bucket is resolved from the portal via `GET /collector/bucket`.

How it works internally: [documentation/python-collector.md](documentation/python-collector.md).

## Prerequisites

- Python ≥ 3.10 (`python3` on Linux/macOS, `py -3` on Windows)
- `git` on PATH
- `gcloud` on PATH and logged in (`gcloud auth login` or `gcloud auth application-default login`) — it
  provides both the account identity and the Cloud Storage upload

**Authentication:** the collector authenticates itself against the portal with a gcloud access token of
the active gcloud account — the same account that performs the upload, so there is nothing extra to
configure. The portal verifies that the account is present in the IAM of the portal's project and rejects
the calls with actionable errors otherwise. Portals with authentication disabled (local development) are
unaffected; if no token can be obtained, the calls are sent without the Authorization header so those
portals still work.

## Usage

```
python3 deploy_info_collector.py --deploy-type GAE --portal-url https://deploy-info-portal.example.com \
    --projects proj-prod,proj-dev,proj-qa \
    [--project-dir .] [--max-file-size 50] [--collect-git-status | --no-collect-git-status] \
    [--extra-files src/config.yaml NOTES.md] [--verbose]
```

Exit code is `0` on success, `1` on any failure, `2` on malformed arguments.

| Argument | Description |
|---|---|
| --deploy-type | REQUIRED. Type of deploy: `GAE` (App Engine) or `RUN` (Cloud Run). |
| --portal-url | REQUIRED. URL of the portal backend. The upload bucket is resolved from the portal, so the portal must be reachable before the upload starts. |
| --projects | REQUIRED. GCP projects where the code may have been deployed. Comma-separated and/or repeatable; merged and deduplicated; must yield at least one non-empty value. |
| --project-dir | OPTIONAL. Project root directory; base for resolving git/extra-file paths. Output goes to `<project-dir>/build/collector/…`. Default is the current directory. |
| --max-file-size | OPTIONAL. Maximum size of files that can be included, **in KB** (the Gradle plugin takes bytes — the only CLI-surface difference). Default is 50. |
| --collect-git-status / --no-collect-git-status | OPTIONAL. Whether git-related data is collected. Default is enabled. |
| --extra-files | OPTIONAL. Extra files to include. Paths relative to `--project-dir`; files only, no directories; collected without parent dirs, so same-name files collide. |
| --verbose | OPTIONAL. Print debug lines (each executed command). |

## Notes

- The collector writes its output to `<project-dir>/build/collector/` and clears that directory at the
  start of every run, so contents of different deploys never mix.
- The output directory name (`<gcloud-user>_<DEPLOYTYPE>_<epochMillis>`) is the sole carrier of
  who/what/when; no metadata file is written.
- Large files become `<FILE IS TOO LARGE (n bytes)>` placeholders, binaries `<FILE IS BINARY>`, blank
  files `<FILE IS BLANK>` — mirroring the Gradle plugin byte-for-byte.
- The resulting Cloud Storage folder layout is the same contract the portal serving relies on (see
  [documentation/python-collector.md](documentation/python-collector.md)).
- Windows is supported by construction (mapping `gcloud` → `gcloud.cmd` is the only OS branch; all I/O is
  explicit UTF-8); run with `py -3 deploy_info_collector.py …`.
