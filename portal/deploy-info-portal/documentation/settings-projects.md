# Settings — Projects

The Settings feature manages the GCP projects whose deploys the portal will link. It is fully
implemented end to end (backend + Settings screen in the UI).

## REST endpoints (`ui/settings/SettingsController.kt`)

- `GET /settings/projects` — list configured projects (`200`; `204` when empty)
- `POST /settings/project` — add a project (body: `name`, `group?`, `serviceAccount`)
- `PUT /settings/project` — edit a project (identified by `name`)
- `DELETE /settings/project/{name}` — delete a project

Add and edit validate the service account before saving: the use cases run
`ServiceAccountValidator` (`infrastructure/gcp/GcpServiceAccountValidator.kt`), which (1)
impersonates the project's service account via the IAM Credentials API and (2) lists the project's
App Engine and Cloud Run deploys with it. On failure the project is **not** saved and the result
carries `issueServiceAccountError = true` plus:

- `serviceAccountIssue` — the issue code: `MISSING_IMPERSONATION_PERMISSION` (the portal lacks
  `roles/iam.serviceAccountTokenCreator` on the target service account), `MISSING_LISTING_PERMISSION`
  (the service account lacks the App Engine Viewer / Cloud Run Viewer roles) or `PORTAL_ISSUE`
  (a portal-side problem the user cannot fix);
- `portalServiceAccount` — the portal's own service account email (resolved server-side from the
  Application Default Credentials identity), the principal to grant the missing IAM roles to; null
  when it cannot be determined (e.g. the portal runs with user credentials).

The UI shows an error dialog naming the missing permission with an expandable area of step-by-step
grant instructions — via the GCP Console and via the gcloud CLI — and a "Retry" button that
re-submits the form (see `documentation/plan-service-account-validation.md`).

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
