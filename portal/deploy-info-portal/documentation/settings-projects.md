# Settings — Projects

The Settings feature manages the GCP projects whose deploys the portal will link. It is fully
implemented end to end (backend + Settings screen in the UI).

## REST endpoints (`ui/settings/SettingsController.kt`)

- `GET /settings/projects` — list configured projects (`200`; `204` when empty)
- `POST /settings/project` — add a project (body: `name`, `group?`, `serviceAccount`)
- `PUT /settings/project` — edit a project (identified by `name`)
- `DELETE /settings/project/{name}` — delete a project

Add and edit return a result with optional issue flags (`issueNameConflict`,
`issueProjectNotFound`, `issueServiceAccountError`) and the projects left in the repository.

## Implementation

- `domain/Project.kt` — a configured project (name, optional group, service account).
- `application/ProjectRepository.kt` — repository interface; implemented by
  `infrastructure/gcp/datastore/ProjectDatastoreRepository.kt` (Datastore entity
  `deploy-info/project`).
- `application/settings/` — use cases: `AddProjectUseCase`, `EditProjectUseCase`,
  `DeleteProjectUseCase`, `GetAllProjectsUseCase`.

## Known limitations (Phase 2)

- `AddProjectUseCase` / `EditProjectUseCase`: `issueServiceAccountError` is hardcoded `false` —
  service-account permission validation is not implemented yet (see `TODO.md`, Phase 2 "Settings
  hardening").
