# GCP Deploy Listing

How the portal backend lists the existing deploys of a configured GCP project. This is the "deploy
listing" part of the Phase 1 backend core: given a configured project, the portal can discover every
App Engine version and Cloud Run revision that exists in it, newest first. It is the data source for
the linking logic that will associate a collector upload with the deploy it was built for.

## Components

- `application/GcpAppEngineLister.kt` / `application/GcpCloudRunLister.kt` — interfaces (application
  layer). Both define `listAllDeploys(project: Project): List<...Deploy>`, throwing
  `domain/GcpListingException` when a listing API cannot be reached, returns an error, or returns an
  unexpected response.
- `domain/AppEngineDeploy.kt` / `domain/CloudRunDeploy.kt` — one deploy of one service, with project,
  service, version/revision id, creation time, and the public URL when available.
- `infrastructure/gcp/appengine/GcpAppEngineApiClient.kt` — implements the App Engine listing through
  the App Engine Admin REST API (`https://appengine.googleapis.com`, `v1/apps/...`), paging through
  services and versions.
- `infrastructure/gcp/cloudrun/GcpCloudRunApiClient.kt` — implements the Cloud Run listing through the
  Cloud Run Admin REST API (`https://run.googleapis.com`, `v2/projects/...`), listing every service in
  every region and their revisions, parsing the location/service/revision ids out of the resource
  names.
- `infrastructure/gcp/AccessTokenProvider.kt` — supplies the bearer token used to authenticate each
  listing call on behalf of a project's service account.
- `infrastructure/beans/GcpListerBeans.kt` — registers the two listers as Spring beans, each with its
  own `RestTemplate`, the shared access token provider, and the API base URL constant defined by the
  client itself.

## What is not implemented yet

- **Access tokens (impersonation):** `GcpListerBeans` registers a placeholder `AccessTokenProvider`
  that always throws — any call to a listing API fails until impersonation is implemented (Phase 1:
  generate an access token for each configured project's service account).
- **Endpoints:** no REST endpoint exposes the listing yet; it will be consumed by the trigger's core
  linking logic.

## Testing

The API clients are covered by unit tests (`tests/infrastructure/gcp/...`) that stub all HTTP calls
locally — no external dependency and no internet access. The fakes in `src/test/kotlin/tests/fakes/`
provide in-memory listers for other tests and future local-profile stubbing.
