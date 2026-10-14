# GCP infrastructure setup

What must exist in GCP for a production deploy-info setup. For running everything locally see
[local-development.md](local-development.md); for deploying the portal itself see
[deployment.md](../portal/deploy-info-portal/documentation/development/deployment.md).

Everything happens in two kinds of projects:

- the **portal project** — runs the portal (App Engine), its Datastore database and the upload bucket;
- any number of **watched projects** — the GCP projects whose App Engine / Cloud Run deployments are shown
  in the portal.

## 1. Portal project

### APIs to enable

<table>
  <thead>
    <tr>
      <th>API</th>
      <th>Why</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Cloud Asset API (<code>cloudasset.googleapis.com</code>)</td>
      <td>
        Authentication: the portal checks every caller against the project's IAM (see
        <a href="../portal/deploy-info-portal/documentation/backend/authentication.md">authentication.md</a>)
      </td>
    </tr>
    <tr>
      <td>Cloud Storage API</td>
      <td>The upload bucket and the portal's read/delete of collector uploads</td>
    </tr>
    <tr>
      <td>Cloud Firestore API (in <strong>Datastore mode</strong>)</td>
      <td>The Datastore database holding the configured projects and deployment links</td>
    </tr>
    <tr>
      <td>App Engine Admin API</td>
      <td>
        The portal runs on App Engine Standard (and lists App Engine deployments of watched projects through
        impersonation)
      </td>
    </tr>
  </tbody>
</table>

### Upload bucket

A Cloud Storage bucket whose name matches the `deploy-info.storage-bucket` property. The collector resolves the bucket
name from the portal, so the bucket never has to be configured anywhere else.

### Portal service account

On App Engine the portal runs as the project's **App Engine default service account**. It needs:

<table>
  <thead>
    <tr>
      <th>Role</th>
      <th>On</th>
      <th>Why</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Cloud Asset Viewer (<code>roles/cloudasset.viewer</code>)</td>
      <td>portal project</td>
      <td>
        The authentication membership check (see
        <a href="../portal/deploy-info-portal/documentation/backend/authentication.md">authentication.md</a>)
      </td>
    </tr>
    <tr>
      <td>Datastore User (<code>roles/datastore.user</code>)</td>
      <td>portal project</td>
      <td>
        Read/write the configured projects, the deployment links and the cleanup state (see
        <a href="../portal/deploy-info-portal/documentation/backend/deploy-cleanup.md">deploy-cleanup.md</a>)
      </td>
    </tr>
    <tr>
      <td>Storage Object Viewer (<code>roles/storage.objectViewer</code>)</td>
      <td>the upload bucket</td>
      <td>Read the collector uploads' files and list their contents</td>
    </tr>
    <tr>
      <td>Storage Object Admin (<code>roles/storage.objectAdmin</code>)</td>
      <td>the upload bucket</td>
      <td>
        Delete a deployment's upload folder once the deployment is gone — the link cleanup sweep and the
        unlinkable-upload cleanup
      </td>
    </tr>
  </tbody>
</table>

### People and credentials

- **Portal users** — anyone who should see the deployment information needs *any* IAM role on the portal
  project, directly or through a Google Group (group membership is expanded by the authentication check).
  No extra configuration exists or is needed.
- **Collector users** — the account that runs the collector needs object-creation permission on the
  upload bucket (e.g. Storage Object Creator) — the upload is performed by that account's own gcloud
  credentials — and, like every portal caller, needs an IAM presence in the portal project.
- **OAuth client** — a web-application OAuth client whose *Authorized JavaScript origins* include the
  portal's serving URL (and `http://localhost:4200` for local frontend development). Its client ID goes
  into `deploy-info.auth.client-id`; the id is not a secret — only the client's origin restrictions
  matter. See [authentication.md](../portal/deploy-info-portal/documentation/backend/authentication.md).

## 2. Watched projects

For each project registered in the portal's Projects screen, the portal lists its App Engine versions and
Cloud Run revisions **by impersonating that project's service account** (see
[gcp-deploy-listing.md](../portal/deploy-info-portal/documentation/backend/gcp-deploy-listing.md)). Each watched
project therefore needs:

1. **A service account to impersonate** — the project's default App Engine / compute service account
   works, or a dedicated one. This service account must have the following:
   - Roles:
     - App Engine Viewer (`roles/appengine.appViewer`): To access data from App Engine deployments.
     - Cloud Run Viewer (`roles/run.viewer`): To access data from Cloud Run deployments.
   - Permissions:
     - The **portal's** service account needs to be granted the Service Account Token Creator
       (`roles/iam.serviceAccountTokenCreator`) permission **on the watched project's service account**.
2. **Enabled listing APIs** — App Engine and Cloud Run APIs must be enabled. A disabled API is detected
   reactively by the portal and reported in the Projects UI.

The Projects screen validates exactly this setup when a project is added or edited, reporting per-issue
fix instructions (see
[projects.md](../portal/deploy-info-portal/documentation/backend/projects.md)).
