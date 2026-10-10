# Deploying the portal to GCP App Engine

How the portal fat jar is deployed to GCP App Engine Standard (Java 21). For local development see
[running-locally.md](running-locally.md).

## What gets deployed

`./gradlew appengineDeploy` builds the Spring Boot fat jar (`bootJar`, which also compiles the
Angular UI and embeds it — see [frontend-serving.md](frontend-serving.md)) and deploys it to App Engine
Standard (`runtime: java21`). The jar name is pinned to `portal.jar` so the `app.yaml` entrypoint
(`java -jar portal.jar`) never changes; the version identity is carried entirely by the App Engine
version name.

## Usage

```
./gradlew appengineDeploy -PgaeProjectId=<gcp-project> [-PgaeService=<service>] [-PgaePromote=<true|false>]
```

| Property       | Default    | Meaning                                                                                                  |
|----------------|------------|----------------------------------------------------------------------------------------------------------|
| `gaeProjectId` | (required) | GCP project to deploy to; the build fails fast with a clear error if it is missing                       |
| `gaeService`   | `default`  | App Engine service to deploy to (injected into the generated `app.yaml`)                                 |
| `gaePromote`   | `true`     | Route traffic to the new version (and stop the previous one, matching gcloud's pairing of the two flags) |

### Setting the properties permanently

Instead of passing `-P` on every deploy, the same properties can be set permanently — they are
plain Gradle properties, so this works in either of:

- `gradle.properties` next to `build.gradle.kts` (project-local):
  ```properties
  gaeProjectId=my-gcp-project
  gaeService=portal
  ```
- `~/.gradle/gradle.properties` (global, applies to every Gradle project of the user)
- an environment variable, CI-friendly: `ORG_GRADLE_PROJECT_gaeProjectId=my-gcp-project`

A command-line `-P` flag always wins over these; the built-in defaults (`gaeService=default`,
`gaePromote=true`) apply only when neither the CLI nor the permanent properties set a value.
`gaeProjectId` has no default and must come from one of these sources.


## Version naming

The App Engine version is always `<portal_version>-<timestamp>`, e.g. `v0-0-1-20261010-153012`
(UTC timestamp, `yyyyMMdd-HHmmss`). App Engine version IDs must start with a letter and may only
contain lowercase letters, digits and hyphens (max 63 chars), so the project version
(`0.0.1-SNAPSHOT`) is normalized: lowercased, the `-SNAPSHOT` suffix dropped, and every other
character replaced by a hyphen (`0.0.1` → `0-0-1`), then prefixed with `v`.

Every deploy creates a new version; old versions stay listed (unless promoted, which stops the
previous one). Roll back with `gcloud app services set-traffic <service> --splits=<version>=1`.

## app.yaml

The template lives in `src/main/appengine/app.yaml` (`runtime: java21`, `instance_class: F2`,
`entrypoint: java -jar portal.jar`). The final file staged for deploy is generated into
`build/generated-appengine/` by the `generateGaeConfig` task, which replaces the service
placeholder with the `gaeService` property — so the target service is configurable from the
command line without editing the template. Staging (`appengineStage`) copies the generated
`app.yaml` and the boot jar into `build/staged-app/`; `appengineDeploy` runs on that staged
directory.

The plugin (`com.google.cloud.tools.appengine`) downloads and manages the Cloud SDK automatically;
authentication follows gcloud's standard Application Default Credentials (`gcloud auth
application-default login` is not enough for deploys — the deploying user needs
`roles/appengine.deployer` plus the Cloud Build roles on the target project).

## Runtime behavior on GAE

- The app auto-selects the `prod` profile on GAE (the `GAE_DEPLOYMENT_ID` env var is set by the
  runtime — see `infrastructure/DeployInfoPortalApplication.kt`).
- The server binds to the port from the `PORT` environment variable (`server.port=${PORT:8080}` in
  `application.properties`; 8080 on the Java runtimes).
- No CORS config is needed: the UI is served same-origin from the fat jar (see
  [running-locally.md](running-locally.md)).
- Logging is structured JSON on stdout (`StackdriverJsonLayout` via `logback-spring.xml`), ingested
  by the runtime into Cloud Logging with correct severity and trace correlation — see
  [logging.md](logging.md).
