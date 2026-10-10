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
  the official Google Cloud client library (`com.google.cloud:google-cloud-appengine-admin`,
  `ServicesClient`/`VersionsClient`), listing every service and version of the project. The library
  handles authentication, request building, JSON parsing and pagination; the client caches one
  underlying client per service account and maps the library's resources into `AppEngineDeploy`.
- `infrastructure/gcp/cloudrun/GcpCloudRunApiClient.kt` — implements the Cloud Run listing through
  the official Google Cloud client library (`com.google.cloud:google-cloud-run`,
  `ServicesClient`/`RevisionsClient`), listing every service in every region and their revisions,
  resolving the location/service/revision ids out of the resource names with the library's
  `ServiceName`/`RevisionName` helpers. The client caches one underlying client pair per service
  account and maps the library's resources into `CloudRunDeploy`.
- `infrastructure/gcp/GcpCredentialsProvider.kt` — implements impersonation (Phase 1): the portal's
  own credentials (Application Default Credentials) impersonate the project's service account via
  the IAM Credentials API and return `GoogleCredentials` (an `ImpersonatedCredentials` instance)
  with the `cloud-platform` scope, handed directly to the client libraries (no raw bearer tokens).
  Uses the Google auth library (already a transitive dependency — no new libraries), which handles
  token caching and refresh-on-expiry; one credentials instance is kept per service account. The
  portal's credentials are resolved lazily, so missing ADC fails only when a listing call is made,
  not at startup.
- `infrastructure/gcp/CredentialsProvider.kt` — the interface (application seam) that supplies the
  credentials used to authenticate each listing call on behalf of a project's service account.
- `domain/CredentialsException.kt` — thrown on impersonation failure, with a category telling who
  can act on it: `SERVICE_ACCOUNT_MISCONFIGURATION` (user-fixable: wrong service account email or
  missing `roles/iam.serviceAccountTokenCreator` permission — the IAM API returns 403) vs
  `PORTAL_ISSUE` (portal-side: missing ADC, network failure, server errors, unexpected responses).
- `infrastructure/beans/GcpListerBeans.kt` — registers the two listers as Spring beans, each backed
  by the client library factory (`create(...)`) and the shared credentials provider.

## Required IAM setup

For each configured project, the portal's own service account needs the
`roles/iam.serviceAccountTokenCreator` role on that project's service account (to impersonate it),
and each impersonated service account needs read access to the listing APIs (App Engine Viewer /
Cloud Run Viewer).

## Disabled listing APIs

The listing APIs themselves are discrete services that can be disabled in the target project
(`appengine.googleapis.com`, `run.googleapis.com`). A disabled API fails the listing with an
`ApiException` of status `PERMISSION_DENIED` whose response body carries a `google.rpc.ErrorInfo`
with reason `SERVICE_DISABLED` — the same status a missing IAM role produces, so it would otherwise
masquerade as missing viewer roles. The gax client library does not expose `ErrorInfo` as a typed
field, so `GcpAppEngineApiClient`/`GcpCloudRunApiClient` recover the reason out of the exception
cause chain (`GcpListingException.errorInfoReason`) into `GcpListingException.reason`, and
`GcpServiceAccountValidator` maps it to the user-fixable `APIS_NOT_ENABLED` issue before the
generic `MISSING_LISTING_PERMISSION` check — the UI shows instructions for enabling the APIs.
Detection is reactive (from the listing error itself), so it needs no extra GCP permissions — a
proactive pre-check via Service Usage would require granting `serviceusage.services.list` on every
target project.

## What is not implemented yet

- **Endpoints:** no REST endpoint exposes the listing yet; it will be consumed by the trigger's core
  linking logic.

## Testing

The credentials provider is covered by unit tests (`tests/infrastructure/gcp/GcpCredentialsProviderTests.kt`)
that stub the impersonation transport locally — no external dependency, no GCP credentials and no
internet access. The API clients are covered by thin unit tests over a small seam
(`AppEngineAdminClient` / `CloudRunAdminClient`) that verify the domain mapping, client caching and
the wrapping of library API errors into `GcpListingException`; the client libraries themselves
(pagination, JSON parsing, transport) are assumed to be covered by Google. The fakes in
`src/test/kotlin/tests/fakes/` provide in-memory listers for other tests and future local-profile
stubbing.
