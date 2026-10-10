# Settings — Projects

The Settings feature manages the GCP projects whose deploys the portal will link. It is fully
implemented end to end (backend + Settings screen in the UI).

## REST endpoints (`ui/settings/SettingsController.kt`)

- `GET /settings/projects` — list configured projects (`200`; `204` when empty)
- `POST /settings/project` — add a project (body: `projectId`, `group?`, `serviceAccount`)
- `PUT /settings/project` — edit a project (identified by `projectId`)
- `DELETE /settings/project/{name}` — delete a project

Add and edit validate the service account before saving: the use cases run
`ServiceAccountValidator` (`infrastructure/gcp/GcpServiceAccountValidator.kt`), which (1)
impersonates the project's service account via the IAM Credentials API and (2) lists the project's
App Engine and Cloud Run deploys with it. On failure the project is **not** saved and the result
carries `issueServiceAccountError = true` plus:

- `serviceAccountIssue` — the issue code: `MISSING_IMPERSONATION_PERMISSION` (the portal lacks
  `roles/iam.serviceAccountTokenCreator` on the target service account), `MISSING_LISTING_PERMISSION`
  (the service account lacks the App Engine Viewer / Cloud Run Viewer roles), `APIS_NOT_ENABLED`
  (a listing API itself — the App Engine Admin API or the Cloud Run Admin API — is not enabled in
  the project; detected reactively from the listing error's `google.rpc.ErrorInfo` reason
  `SERVICE_DISABLED`, so no extra GCP permissions are needed for the detection), `PROJECT_NOT_FOUND`
  (the listing API cannot resolve the project at all — gax `NOT_FOUND`, or `INVALID_ARGUMENT` for a
  malformed id: the entered name is not the exact GCP project ID or the project does not exist) or
  `PORTAL_ISSUE` (a portal-side problem the user cannot fix). Caveat: the App Engine Admin API
  answers a wrong/unreachable project id with a plain `PERMISSION_DENIED` ("or it may not exist") —
  it cannot tell "no permission" from "does not exist" — so that case still reports
  `MISSING_LISTING_PERMISSION`; Cloud Run returns `NOT_FOUND` and reports `PROJECT_NOT_FOUND`.
- `portalServiceAccount` — the portal's own service account email (resolved server-side from the
  Application Default Credentials identity), the principal to grant the missing IAM roles to; null
  when it cannot be determined (e.g. the portal runs with user credentials).

The UI shows an error dialog naming the missing permission with an expandable area of step-by-step
grant instructions — via the GCP Console and via the gcloud CLI — and a "Retry" button that
re-submits the form (see `documentation/plan-service-account-validation.md`). `PROJECT_NOT_FOUND`
gets its own dialog text with instructions for finding the exact project ID (e.g. via
`gcloud projects list` or the console project selector) and the same Retry button;
`APIS_NOT_ENABLED` gets its own dialog text with instructions for enabling the APIs (the console
API Library, or `gcloud services enable appengine.googleapis.com run.googleapis.com`) and the same
Retry button. The Add/Edit
dialog labels the field "GCP project ID", with a hint that it is the exact project ID — not the
console display name.

## Implementation

- `domain/Project.kt` — a configured project (name, optional group, service account).
- `application/ProjectRepository.kt` — repository interface; implemented by
  `infrastructure/gcp/datastore/ProjectDatastoreRepository.kt` (Datastore entity
  `deploy-info/project`).
- `application/settings/` — use cases: `AddProjectUseCase`, `EditProjectUseCase`,
  `DeleteProjectUseCase`, `GetAllProjectsUseCase`; both add and edit validate the service account
  (edit only when the service account changed) and skip the save on failure.
- `application/ServiceAccountValidator.kt` + `infrastructure/gcp/GcpServiceAccountValidator.kt` —
  the two-step validation described above; validation runs lazily per request, never at startup.
