# deploy-info

Let users see exactly which commits and local changes were in the code of each GCP App Engine or Cloud Run deploy.

Components:

- [`collector-java/deploy-info-collector`](collector-java/deploy-info-collector/README.md) — Gradle plugin that collects git/extra file info and uploads it to Cloud Storage ([how it works](collector-java/deploy-info-collector/documentation/collector-plugin.md))
- [`portal/deploy-info-portal`](portal/deploy-info-portal) — Spring Boot backend + Angular frontend ([run everything locally](documentation/local-development.md) · [backend documentation](portal/deploy-info-portal/documentation/running-locally.md) · [API contracts](documentation/api.md))

Project plan and status: see [TODO.md](TODO.md) and [STATUS_REPORT.md](STATUS_REPORT.md).
