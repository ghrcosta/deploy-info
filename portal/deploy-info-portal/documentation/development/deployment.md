# Deploying the portal to GCP App Engine

How the portal fat jar is deployed to GCP App Engine Standard (Java 21). For local development see
[running-locally.md](running-locally.md).

## What gets deployed

`./gradlew appengineDeploy` builds the Spring Boot fat jar (which also compiles the Angular UI and embeds
it — see [frontend-serving.md](frontend-serving.md)) and deploys it to App Engine Standard
(`runtime: java21`). The jar name is pinned to `portal.jar` so the `app.yaml` entrypoint never changes;
the version identity is carried entirely by the App Engine version name.

## Usage

```
./gradlew appengineDeploy -PgaeProjectId=<gcp-project> [-PgaeService=<service>] [-PgaePromote=<true|false>]
```

<table>
  <thead>
    <tr>
      <th>Property</th>
      <th>Default</th>
      <th>Meaning</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>gaeProjectId</code></td>
      <td>(required)</td>
      <td>GCP project to deploy to; the build fails fast with a clear error if it is missing</td>
    </tr>
    <tr>
      <td><code>gaeService</code></td>
      <td><code>default</code></td>
      <td>App Engine service to deploy to (injected into the generated <code>app.yaml</code>)</td>
    </tr>
    <tr>
      <td><code>gaePromote</code></td>
      <td><code>true</code></td>
      <td>
        Route traffic to the new version (and stop the previous one, matching gcloud's pairing of the two flags)
      </td>
    </tr>
  </tbody>
</table>

Instead of passing `-P` on every deploy, the same properties can be set permanently: in a
`gradle.properties` next to the build file (project-local), in `~/.gradle/gradle.properties` (global), or
as an environment variable, CI-friendly (`ORG_GRADLE_PROJECT_gaeProjectId=…`). A command-line `-P` flag
always wins over these; the built-in defaults apply only when neither the CLI nor the permanent
properties set a value. `gaeProjectId` has no default and must come from one of these sources.

## Version naming

The App Engine version is always `v<portal_version>`, e.g. for version `0.0.1` it becomes `v0-0-1`. App
Engine version IDs must start with a letter and may only contain lowercase letters, digits and hyphens
(max 63 chars), so the project version is normalized.

## app.yaml

The template lives in `src/main/appengine/app.yaml` . The final file staged for deploy is generated at
build time, which replaces the service placeholder with the `gaeService` property — so the target service
is configurable from the command line without editing the template. Staging copies the generated `app.yaml`
and the boot jar into the staged directory that `appengineDeploy` deploys.