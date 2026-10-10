# Portal logging (GCP Logs Explorer)

How the portal's logs are configured so they show up in [Google Cloud Logs
Explorer](https://console.cloud.google.com/logs/viewer) with correct severity and request
correlation when running on App Engine.

## Why the starter alone is not enough

The `spring-cloud-gcp-starter-logging` dependency (see `gradle/libs.versions.toml` and
`build.gradle.kts`) only wires the **trace-ID correlation plumbing**: it provides the
`TraceIdExtractor` bean consumed by the custom
`infrastructure/gcp/logging/TraceIdFilter.kt`, which runs `TraceIdLoggingWebMvcInterceptor` as a
highest-priority servlet filter so even filter-level log messages get the request's
`X-Cloud-Trace-Context`.

It does **not** route any log output to Cloud Logging — that has to be configured in Logback, in
`src/main/resources/logback-spring.xml`. Without it the app emits Spring Boot's plain-text console
output, which the GAE runtime ingests only as unstructured `stdout`/`stderr` entries with default
severity.

## How the logs reach Cloud Logging

`logback-spring.xml` has two branches:

- **`!prod`** (local dev, tests): Spring Boot's standard console output
  (`org/springframework/boot/logging/logback/defaults.xml` + `console-appender.xml`). No GCP
  access, no credentials, no behavior change versus the Boot defaults.
- **`prod`** (App Engine, selected via `GAE_DEPLOYMENT_ID` in `DeployInfoPortalApplication.kt`):
  a console appender wrapped in `com.google.cloud.spring.logging.StackdriverJsonLayout`, i.e.
  structured JSON on stdout. This was chosen over the Cloud Logging API appender
  (`com/google/cloud/spring/logging/logback-appender.xml`) because the GAE java21 runtime
  ingests stdout itself — no extra API calls, credentials, or background flushing are needed, and
  the runtime parses the JSON into log entries with:
  - `severity` mapped from the Logback level (WARN, ERROR, …) — filterable in Logs Explorer;
  - `trace` = `projects/<project>/traces/<traceId>` (from the MDC set by the `TraceIdFilter`,
    extracted from `X-Cloud-Trace-Context`), so log entries group with the request log in the
    Logs Explorer log view;
  - `serviceContext.service` from `GAE_SERVICE` (set by the runtime), matching the App Engine
    service so Error Reporting can attribute errors.

`projectId` resolves from the `GOOGLE_CLOUD_PROJECT` environment variable, which the App Engine
runtime always sets. (The Cloud Logging stack ignores `spring.cloud.gcp.project-id` from
`application.properties` — only these environment variables matter for logging.)

## Why the profile is set before `run()`

`logback-spring.xml` branches on the active Spring profiles (`<springProfile name="prod">`), and
Spring Boot initializes the logging system from the environment **early** in startup — before
application-context customizers run. The profile selection in `DeployInfoPortalApplication.kt`
therefore lives on the `SpringApplication` object (`setAdditionalProfiles(...)` before `run()`,
not inside a `runApplication { ... }` initializer): with the previous placement the `prod` branch
never activated and GAE logging stayed unstructured.

## What the application logs with

All logging goes through **SLF4J** (`org.slf4j.LoggerFactory`), which Spring Boot binds to Logback:
only that way does every message flow through the `logback-spring.xml` pipeline and get the JSON
treatment on GAE. Do not log via `java.util.logging.Logger` — JUL bypasses Logback, and its
messages would end up as unstructured entries without severity/trace metadata.

## Viewing the logs

In Logs Explorer (project = the App Engine project), select the resource type `GAE Application`
(`resource.type="gae_app"`), the service and optionally the version. The application's own output
appears alongside the runtime's request log — each entry carries `severity`, `trace` and the JSON
payload fields written by `StackdriverJsonLayout`.
