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
- `infrastructure/gcp/cloudrun/GcpCloudRunApiClient.kt` — implements the Cloud Run listing through
  the Cloud Run Admin REST API (`https://run.googleapis.com`, `v2/projects/...`), listing every service in
  every region and their revisions, parsing the location/service/revision ids out of the resource
  names.
- `infrastructure/gcp/IamCredentialsAccessTokenProvider.kt` — implements impersonation (Phase 1): the
  portal's own credentials (Application Default Credentials) impersonate the project's service
  account via the IAM Credentials API and return an access token with the `cloud-platform` scope.
  Uses `ImpersonatedCredentials` from the Google auth library (already a transitive dependency — no
  new libraries), which handles token caching and refresh-on-expiry; one credentials instance is kept
  per service account. The portal's credentials are resolved lazily, so missing ADC fails only when a
  listing call is made, not at startup.
- `domain/AccessTokenException.kt` — thrown on impersonation failure, with a category telling who can
  act on it: `SERVICE_ACCOUNT_MISCONFIGURATION` (user-fixable: wrong service account email or missing
  `roles/iam.serviceAccountTokenCreator` permission — the IAM API returns 403) vs `PORTAL_ISSUE`
  (portal-side: missing ADC, network failure, server errors, unexpected responses).
- `infrastructure/gcp/AccessTokenProvider.kt` — the interface (application seam) that supplies the
  bearer token used to authenticate each listing call on behalf of a project's service account.
- `infrastructure/beans/GcpListerBeans.kt` — registers the two listers as Spring beans, each with its
  own `RestTemplate`, the shared access token provider, and the API base URL constant defined by the
  client itself.

## Required IAM setup

For each configured project, the portal's own service account needs the
`roles/iam.serviceAccountTokenCreator` role on that project's service account (to impersonate it),
and each impersonated service account needs read access to the listing APIs (App Engine Viewer /
Cloud Run Viewer).

## What is not implemented yet

- **Endpoints:** no REST endpoint exposes the listing yet; it will be consumed by the trigger's core
  linking logic.

## Testing

The API clients and the access token provider are covered by unit tests (`tests/infrastructure/gcp/...`)
that stub all HTTP calls locally — no external dependency, no GCP credentials and no internet access.
The fakes in `src/test/kotlin/tests/fakes/` provide in-memory listers for other tests and future
local-profile stubbing.
