# GCP deployment listing

How the portal backend lists the existing deploys of a configured GCP project: given a configured
project, the portal discovers every App Engine version and Cloud Run revision that exists in it, newest
first. It is the data source for the linking logic that associates a collector upload with the deployment it
was built for.

## How it works

- Two listers (one for App Engine, one for Cloud Run) list every service and version/revision of the
  project, newest first, mapping each into a deployment object with project, service, version/revision id,
  creation time and the public URL. A listing attempt that returns an error or other invalid response
  throws a listing exception.
- **Impersonation**: the portal's own credentials (Application Default Credentials) impersonate the
  project's service account via the IAM Credentials API and are handed to the client libraries, which
  handle authentication, token caching and refresh. One credentials instance is kept per service
  account; the portal's credentials are resolved lazily, so missing ADC fails only when a listing call
  is made, not at startup.
- The underlying API clients are created per service account and cached; the Google client libraries
  handle request building, JSON parsing and pagination.

## Failure handling

Impersonation failures carry a category telling who can act on them: a service-account misconfiguration
(user-fixable: wrong service account email or missing `roles/iam.serviceAccountTokenCreator` permission)
vs. a portal-side issue (missing ADC, network failure, server errors, unexpected responses).

## Disabled listing APIs

The listing APIs themselves are discrete services that can be disabled in the target project
(`appengine.googleapis.com`, `run.googleapis.com`). A disabled API fails the listing with the same status
a missing IAM role produces, so it would otherwise masquerade as missing viewer roles; the portal
recovers the `SERVICE_DISABLED` reason out of the error and maps it to the user-fixable
`APIS_NOT_ENABLED` issue (shown with enabling instructions in the Projects UI) before the generic
missing-permission check. Detection is reactive (from the listing error itself), so it needs no extra
GCP permissions.

## IAM setup

The roles the portal's service account needs on each watched project's service account (impersonation
and viewer access) are part of the overall setup described in
[gcp-infra-setup.md](../../../../documentation/gcp-infra-setup.md).
