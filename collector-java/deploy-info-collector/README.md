# deploy-info-collector

Gradle plugin to collect files and make them available to the portal.

After uploading the collected files to Cloud Storage, the plugin notifies the portal backend, which
links the upload to the GCP deploy it was built for. The upload bucket is not configured on the
collector: it is resolved from the portal. Both endpoint contracts are specified in
[api.md](../../documentation/api.md).

How it works internally:
[documentation/collector-plugin.md](documentation/collector-plugin.md).

## Usage

Apply the plugin (`io.github.ghrcosta.deploy-info-collector`) and configure the `deployInfoCollect` task's
parameters (table below). A typical setup sets `deployType`, `portalUrl` and `projects`, and hooks
`deployInfoCollect` with `finalizedBy` on the deploy task (e.g. `appengineDeploy`) so the collection runs
automatically after every deploy. All parameters are also available as command-line options (e.g.
`./gradlew deployInfoCollect --deploy-type=GAE`).

The collector authenticates against the portal with a gcloud access token of the active gcloud account
(details: [collector-plugin.md](documentation/collector-plugin.md)); the portal's verification rules and
error handling are described in
[authentication.md](../../portal/deploy-info-portal/documentation/backend/authentication.md).

Parameters:

<table>
  <tr>
    <th>Parameter</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>deployType</td>
    <td>REQUIRED. Type of deploy: <code>GAE</code> (App Engine) or <code>RUN</code> (Cloud Run).</td>
  </tr>
  <tr>
    <td>portalUrl</td>
    <td>REQUIRED. URL of the portal backend.</td>
  </tr>
  <tr>
    <td>projects</td>
    <td>REQUIRED. List of GCP projects where the code may have been deployed.</td>
  </tr>
  <tr>
    <td>maxFileSize</td>
    <td>OPTIONAL. Maximum size of files that can be included, in KB. Default is 50.</td>
  </tr>
  <tr>
    <td>collectGitStatus</td>
    <td>OPTIONAL. Whether git-related data is collected. Default is <code>true</code>.</td>
  </tr>
  <tr>
    <td>extraFilesToCollect</td>
    <td>
      OPTIONAL. Extra files to include in the collection. Each item must be a path relative to the project's root
      directory. Directories are not supported. Only the file itself is collected, without its parent directories, so
      if two files have the same name, only one will be included.
    </td>
  </tr>
</table>

## Development

1. In this project, run `./gradlew publishToMavenLocal` to publish the plugin to the local Maven
   repository.
2. In the project under test, add the `mavenLocal()` repository to the plugin-management repositories of
   the **root** `settings.gradle.kts` (a multi-module project may have several `settings.gradle.kts` —
   the root one is what counts), apply the plugin with an explicit version, and configure the task as
   above.
