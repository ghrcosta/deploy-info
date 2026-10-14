# deploy-info-collector (Python)

Standalone Python script to collect files and make them available to the portal — a drop-in alternative
to the [Gradle collector plugin](../collector-java/deploy-info-collector/README.md) for projects not
built with Gradle.

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

<table>
  <thead>
    <tr>
      <th>Argument</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>--deploy-type</code></td>
      <td>REQUIRED. Type of deploy: <code>GAE</code> (App Engine) or <code>RUN</code> (Cloud Run).</td>
    </tr>
    <tr>
      <td><code>--portal-url</code></td>
      <td>
        REQUIRED. URL of the portal backend.
      </td>
    </tr>
    <tr>
      <td><code>--projects</code></td>
      <td>
        REQUIRED. List of GCP projects where the code may have been deployed. Comma-separated and/or
        repeatable; merged and deduplicated; must yield at least one non-empty value.
      </td>
    </tr>
    <tr>
      <td><code>--project-dir</code></td>
      <td>
        OPTIONAL. Project root directory; base for resolving git/extra-file paths. Output goes to
        <code>&lt;project-dir&gt;/build/collector/…</code>. Default is the current directory.
      </td>
    </tr>
    <tr>
      <td><code>--max-file-size</code></td>
      <td>
        OPTIONAL. Maximum size of files that can be included, in KB. Default is 50.
      </td>
    </tr>
    <tr>
      <td><code>--collect-git-status</code> / <code>--no-collect-git-status</code></td>
      <td>OPTIONAL. Whether git-related data is collected. Default is enabled.</td>
    </tr>
    <tr>
      <td><code>--extra-files</code></td>
      <td>
        OPTIONAL. Extra files to include. Paths relative to <code>--project-dir</code>. Directories are
        not supported. Only the file itself is collected, without its parent directories, so if two
        files have the same name, only one will be included.
      </td>
    </tr>
    <tr>
      <td><code>--verbose</code></td>
      <td>OPTIONAL. Print debug lines (each executed command).</td>
    </tr>
  </tbody>
</table>

## Notes

- The collector writes its output to `<project-dir>/build/collector/` and clears that directory at the
  start of every run, so contents of different deploys never mix.
- Large files become `<FILE IS TOO LARGE (n bytes)>` placeholders, binaries `<FILE IS BINARY>`, blank
  files `<FILE IS BLANK>` — mirroring the Gradle plugin byte-for-byte.
- The resulting Cloud Storage folder layout is the same contract the portal serving relies on (see
  [documentation/python-collector.md](documentation/python-collector.md)).
- Windows is supported by construction (mapping `gcloud` → `gcloud.cmd` is the only OS branch; all I/O is
  explicit UTF-8); run with `py -3 deploy_info_collector.py …`.
