# Portal logging (GCP Logs Explorer)

How the portal's logs are configured so they show up in [Google Cloud Logs
Explorer](https://console.cloud.google.com/logs/viewer) with correct severity and request correlation
when running on App Engine.

## How the logs reach Cloud Logging

On App Engine (the prod profile) the portal writes **structured JSON on stdout**, which the App Engine
runtime ingests itself and parses into log entries — no extra API calls, credentials or background
flushing are needed.

Outside App Engine (local dev, tests) the portal falls back to Spring Boot's standard plain-text console
output.

## Configuration notes

- The `prod` profile is auto-selected on App Engine (the runtime sets `GAE_DEPLOYMENT_ID`); the profile
  selection must happen before the logging system initializes, which is why it is set on the application
  entry point rather than in a startup callback.
- All application logging must go through SLF4J (Spring Boot binds it to Logback, which drives the JSON
  pipeline above). Logging via `java.util.logging` bypasses Logback and would end up as unstructured
  entries without severity/trace metadata.

## Viewing the logs

In Logs Explorer (project = the App Engine project), select the resource type `GAE Application`
(`resource.type="gae_app"`), the service and optionally the version. The application's own output appears
alongside the runtime's request log.
