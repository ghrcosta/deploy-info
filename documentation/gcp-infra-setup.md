# GCP infrastructure setup

What must exist in GCP for a production deploy-info setup. For running everything locally see
[local-development.md](local-development.md); for deploying the portal itself see
[`portal/deploy-info-portal/documentation/deployment.md`](../portal/deploy-info-portal/documentation/deployment.md).

Everything below happens in two kinds of projects:

- the **portal project** — runs the portal (App Engine), its Datastore database and the upload bucket;
- any number of **watched projects** — the GCP projects whose App Engine / Cloud Run deploys are
  shown in the portal.

---

## 1. Portal project

### APIs to enable

<table>
  <tr><th>API</th><th>Why</th></tr>
  <tr>
    <td>Cloud Asset API (<code>cloudasset.googleapis.com</code>)</td>
    <td>Authentication: the portal checks every caller against the project's IAM via <code>AnalyzeIamPolicy</code>
      (see <a href="../portal/deploy-info-portal/documentation/authentication.md">authentication.md</a>)</td>
  </tr>
  <tr>
    <td>Cloud Storage API</td>
    <td>The upload bucket and the portal's read/delete of collector uploads</td>
  </tr>
  <tr>
    <td>Cloud Firestore API (in <strong>Datastore mode</strong>)</td>
    <td>The Datastore database holding the configured projects and deploy links (the same mode the local emulator
      uses, see <a href="../portal/deploy-info-portal/documentation/running-locally.md">running-locally.md</a>)</td>
  </tr>
  <tr>
    <td>App Engine Admin API</td>
    <td>The portal runs on App Engine Standard (and lists App Engine deploys of watched projects through
      impersonation)</td>
  </tr>
</table>

### Upload bucket

A Cloud Storage bucket whose name matches the `deploy-info.storage-bucket` property
(`deploy-info-uploads` by default, see `application-prod.properties`). Uniform bucket-level access
is recommended (permissions are then managed purely through IAM, which is what the role list below
assumes).

The collector resolves the bucket name from the portal (`GET /collector/bucket`), so the bucket
never has to be configured anywhere else.

### Portal service account

On App Engine the portal runs as the project's **App Engine default service account**
(`<project-id>@appspot.gserviceaccount.com`); it needs:

<table>
  <tr><th>Role</th><th>On</th><th>Why</th></tr>
  <tr>
    <td>Datastore User (<code>roles/datastore.user</code>)</td>
    <td>portal project</td>
    <td>Read/write the configured projects and the deploy links, plus the cleanup state (see
      <a href="../portal/deploy-info-portal/documentation/deploy-cleanup.md">deploy-cleanup.md</a>)</td>
  </tr>
  <tr>
    <td>Storage Object Viewer (<code>roles/storage.objectViewer</code>)</td>
    <td>the upload bucket</td>
    <td>Read the collector uploads' files and list their contents (<code>GcsFileContentReader</code>)</td>
  </tr>
  <tr>
    <td>Storage Object Admin (<code>roles/storage.objectAdmin</code>)</td>
    <td>the upload bucket</td>
    <td>Delete a deploy's upload folder once the deploy is gone — both the link cleanup sweep and the
      unlinkable-upload cleanup (<code>GcsStorageCleaner</code>)</td>
  </tr>
  <tr>
    <td>Cloud Asset Viewer (<code>roles/cloudasset.viewer</code>)</td>
    <td>portal project</td>
    <td><code>cloudasset.assets.analyzeIamPolicy</code>, the authentication membership check; without it
      authentication fails with a 503 (<code>PORTAL_IAM_CONFIG</code>) — see
      <a href="../portal/deploy-info-portal/documentation/authentication.md">authentication.md</a></td>
  </tr>
</table>

### People and credentials

- **Portal users** — anyone who should see the deploy information needs *any* IAM role on the
  portal project, directly or through a Google Group (group membership is expanded by the
  authentication check). No extra configuration exists or is needed.
- **Collector users** — the account that runs `deployInfoCollect` needs object-creation permission
  on the upload bucket (e.g. Storage Object Creator, `roles/storage.objectCreator`) — the upload is
  performed by that account's own gcloud credentials — and, like every portal caller, an IAM
  presence in the portal project (the trigger requests are authenticated the same way as browser
  calls).
- **OAuth client** — a web-application OAuth client whose *Authorized JavaScript origins* include
  the portal's serving URL (and `http://localhost:4200` for local frontend development). Its client
  id goes into `deploy-info.auth.client-id` (already set in `application-prod.properties`); the id
  is not a secret — only the client's origin restrictions matter. See
  [authentication.md](../portal/deploy-info-portal/documentation/authentication.md).

## 2. Watched projects

For each project registered in the portal's Settings screen, the portal lists its App Engine
versions and Cloud Run revisions **by impersonating that project's service account** (via the IAM
Credentials API — see [gcp-deploy-listing.md](../portal/deploy-info-portal/documentation/gcp-deploy-listing.md)).
Each watched project therefore needs:

1. **A service account to impersonate** — the project's default App Engine / compute service
   account works, or a dedicated one; its email is entered in the Settings dialog.
2. **Listing roles on the impersonated account** (its own project):
   - App Engine Viewer (`roles/appengine.appViewer`) — list services/versions;
   - Cloud Run Viewer (`roles/run.viewer`) — list services/revisions.
3. **Impersonation grant** — the **portal's** service account needs Service Account Token Creator
   (`roles/iam.serviceAccountTokenCreator`) **on the watched project's service account** (bound on
   the service account itself, not the project).
4. **Enabled listing APIs** — `appengine.googleapis.com` and `run.googleapis.com` in the watched
   project. A disabled API is detected reactively by the portal and reported in the Settings UI
   (`APIS_NOT_ENABLED`), but enabling them upfront avoids the round trip.

The Settings screen validates exactly this setup when a project is added or edited: it generates a
token through impersonation and tries a listing, reporting `MISSING_IMPERSONATION_PERMISSION`,
`MISSING_LISTING_PERMISSION` or `APIS_NOT_ENABLED` with per-issue fix instructions (see
[settings-projects.md](../portal/deploy-info-portal/documentation/settings-projects.md)). New
projects can be added before their IAM is complete and fixed with the dialog's *Retry* button.

## 3. Verification checklist

1. Deploy the portal: `./gradlew appengineDeploy -PgaeProjectId=<portal-project>` (see
   [deployment.md](../portal/deploy-info-portal/documentation/deployment.md)).
2. Open the portal URL, sign in, and add a watched project — the save action itself runs the
   impersonation + listing validation above; fix anything it reports.
3. From a project watched by the collector, run `deployInfoCollect` (portal URL + `deployType` +
   `projects` configured, see the [collector README](../collector-java/deploy-info-collector/README.md))
   right after a deploy — the upload appears in the portal's navigator.
4. Open the deploy in the UI: git log, git status, diffs and extra files are readable — this proves
   the bucket read path, the deploy link and the authentication all work.
5. Delete the deploy in GCP and trigger a tree refresh — the link and its upload folder disappear
   (cleanup; enable `deploy-info.cleanup.enabled=true` in prod to use it).
