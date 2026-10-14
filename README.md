# deploy-info

See exactly which commits and local changes were in the code of each GCP App Engine or Cloud Run deploy: a
collector gathers the git/extra-file data of a build, uploads it to Cloud Storage and tells the portal, which
links it to the deploy it was built for and shows it in a web UI.

- [`collector-java/deploy-info-collector`](collector-java/deploy-info-collector/README.md) — Gradle plugin
  ([how it works](collector-java/deploy-info-collector/documentation/collector-plugin.md))
- [`collector-python`](collector-python/README.md) — standalone Python script
  ([how it works](collector-python/documentation/python-collector.md))
- [`portal/deploy-info-portal`](portal/deploy-info-portal) — Spring Boot backend + Angular frontend
  ([API contracts](documentation/api.md) ·
  [deploy to GAE](portal/deploy-info-portal/documentation/development/deployment.md) ·
  [backend docs](portal/deploy-info-portal/documentation/development/running-locally.md))

Run it locally: [documentation/local-development.md](documentation/local-development.md).
Production GCP setup: [documentation/gcp-infra-setup.md](documentation/gcp-infra-setup.md).

Licensed under the [GPL-3](LICENSE).
