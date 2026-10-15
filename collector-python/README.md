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

The collector authenticates against the portal with a gcloud access token of the active gcloud account
(details: [documentation/python-collector.md](documentation/python-collector.md) and the portal's
[authentication.md](../portal/deploy-info-portal/documentation/backend/authentication.md)).

## Usage

```
python3 deploy_info_collector.py --deploy-type GAE --portal-url https://deploy-info-portal.example.com \
    --projects proj-prod,proj-dev,proj-qa \
    [--project-dir .] [--max-file-size 50] [--max-git-log-lines 30] \
    [--collect-git-status | --no-collect-git-status] \
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
      <td>REQUIRED. Type of deployment: <code>GAE</code> (App Engine) or <code>RUN</code> (Cloud Run).</td>
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
      <td><code>--max-git-log-lines</code></td>
      <td>
        OPTIONAL. Maximum number of lines collected from <code>git log --oneline</code>. Default is 30.
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


Windows is supported; run with `py -3 deploy_info_collector.py …`.

## Use from another Python script

`main` is only a thin CLI shim; the collector logic lives in `run(params)`, which takes a
`CollectorParams`:

```python
import deploy_info_collector as dic                      # same directory as the caller, or put it on sys.path

exit_code = dic.run(dic.CollectorParams(
    deploy_type=dic.DeployType.GAE,                      # GAE (App Engine) or RUN (Cloud Run) — the enum, not a string
    portal_url="https://deploy-info-portal.example.com",
    projects=["proj-prod", "proj-dev"],                  # the final list: no comma-splitting/dedup is applied here
    project_dir=".",                                     # output goes to <project-dir>/build/collector/…
    max_file_size=50,                                    # KB
    max_git_log_lines=30,
    collect_git_status=True,
    extra_files=[],                                      # flat list of paths relative to project_dir, files only
    verbose=False,
))
```

`run` returns `0` on success and `1` on failure, logging the error instead of raising. The defaults live
in `CollectorParams` so the optional fields may be omitted ; only `deploy_type`, `portal_url` and
`projects` are mandatory. Like the CLI run, `run` `os.chdir`s into `project_dir` while collecting,
and restores the caller's original working directory when it returns (success or failure).

## Tests

Unit tests live in [`tests/`](tests/) — one module per collector module, mirroring the Java
plugin's test suite (`collector-java/deploy-info-collector/src/test/kotlin`) 1:1. They use only
the standard library (`unittest`/`unittest.mock`) and are hermetic: no git, gcloud or network
access (everything external is mocked).

```
python3 -m unittest discover -s tests   # from this directory; -v for the verbose list
```

Windows is supported; run with `py -3 deploy_info_collector.py …`.
