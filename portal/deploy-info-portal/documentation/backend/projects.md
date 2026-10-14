# Projects

The Projects feature manages the GCP projects whose deploys the portal will link.

## REST endpoints

- `GET /project/all` — list configured projects (`200`; `204` when empty)
- `POST /project` — add a project (body: `projectId`, `group?`, `serviceAccount`)
- `PUT /project` — edit a project (identified by `projectId`)
- `DELETE /project/{name}` — delete a project

## Service account validation

Add and edit validate the project's service account before saving: the portal impersonates the service
account via the IAM Credentials API and lists the project's App Engine and Cloud Run deploys with it. On
failure the project is **not** saved and the result carries an issue code:

- `MISSING_IMPERSONATION_PERMISSION` — the portal lacks `roles/iam.serviceAccountTokenCreator` on the
  target service account;
- `MISSING_LISTING_PERMISSION` — the service account lacks the App Engine Viewer / Cloud Run Viewer
  roles;
- `APIS_NOT_ENABLED` — a listing API is not enabled in the project (detected reactively from the listing
  error, so no extra GCP permissions are needed for the detection);
- `PROJECT_NOT_FOUND` — the listing API cannot resolve the project at all: the entered name is not the
  exact GCP project ID or the project does not exist. Caveat: the App Engine Admin API answers a
  wrong/unreachable project id with a plain `PERMISSION_DENIED` — it cannot tell "no permission" from
  "does not exist" — so that case still reports `MISSING_LISTING_PERMISSION`; Cloud Run returns
  `NOT_FOUND` and reports `PROJECT_NOT_FOUND`;
- `PORTAL_ISSUE` — a portal-side problem the user cannot fix.

The result also carries the portal's own service account email — the principal to grant the missing IAM
roles to; null when it cannot be determined (e.g. the portal runs with user credentials).

## UI behavior

The Projects screen shows an error dialog naming the missing permission, with an expandable area of
step-by-step grant instructions (GCP Console and gcloud CLI) and a **Retry** button that re-submits the
form. `PROJECT_NOT_FOUND` and `APIS_NOT_ENABLED` get their own dialog texts with instructions for finding
the exact project ID and for enabling the APIs. The Add/Edit dialog labels the field "GCP project ID",
with a hint that it is the exact project ID — not the console display name.
