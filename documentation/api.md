# API contracts

Reference for both collectors and the portal frontend. All bodies are JSON; the base URL depends on the
environment (local vs. deployed portal).

## Authentication

When the portal has authentication enabled (the prod profile), every endpoint below requires an
`Authorization: Bearer <token>` header:

| Client | Token |
|---|---|
| Collector | gcloud access token of a user present in the IAM of the portal's project (directly or through a group) |
| Frontend | Google Identity Services ID token of the same kind of user, obtained via the sign-in flow |

Rejection codes (the same for every protected endpoint):

<table>
  <thead>
    <tr>
      <th>Status</th>
      <th>Body</th>
      <th>Meaning</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>401 Unauthorized</td>
      <td><code>{"error":"unauthenticated"}</code></td>
      <td>Token missing, malformed, expired or wrongly signed.</td>
    </tr>
    <tr>
      <td>403 Forbidden</td>
      <td><code>{"error":"not-permitted","email":"…"}</code></td>
      <td>Valid token, but the account is not present in the portal project's IAM.</td>
    </tr>
    <tr>
      <td>403 Forbidden</td>
      <td><code>{"error":"email-mismatch",…}</code></td>
      <td>
        Trigger only: the body's <code>userEmail</code> does not match the token's account (possible with gcloud
        service-account impersonation; see
        <a href="../portal/deploy-info-portal/documentation/backend/authentication.md">authentication.md</a>).
      </td>
    </tr>
    <tr>
      <td>503 Service Unavailable</td>
      <td><code>{"error":"portal-issue"}</code></td>
      <td>The portal could not run its IAM membership check.</td>
    </tr>
  </tbody>
</table>

With authentication disabled (local/test profiles) the header is simply not required.

## Auth endpoints (frontend only)

- `GET /auth/config` — public; sign-in configuration (`enabled`, `clientId`). When `enabled` is false the
  frontend skips the login flow.
- `GET /auth/user` — protected; validates the browser's ID token and returns the authenticated
  user's `email`. The frontend calls it to restore/establish the session and to distinguish
  "signed in" from "signed in without IAM access".

## Collector contract — `POST /collector/handleNewDirectory`

Called by the collector after it has uploaded its output directory to the portal's Cloud Storage bucket.
The portal then tries to link the upload to the GCP deploy it was built for (see
[deploy-link.md](../portal/deploy-info-portal/documentation/backend/deploy-link.md)).

<table>
  <thead>
    <tr>
      <th>Field</th>
      <th>Type</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>directoryName</code></td>
      <td>string</td>
      <td>
        Collector output directory name, convention
        <code>&lt;user&gt;_&lt;deployType&gt;_&lt;epochMillis&gt;</code> (e.g. <code>user_GAE_1735689600000</code>).
        The collection timestamp is <strong>not</strong> sent explicitly — the portal derives it from the
        epoch-millis suffix.
      </td>
    </tr>
    <tr>
      <td><code>projects</code></td>
      <td>list of string</td>
      <td>
        Candidate GCP projects the code may have been deployed to. The collector cannot know which one, so it sends
        all of them; the portal matches against the deploys of <strong>all</strong> listed projects together.
        Projects not configured in the portal are silently skipped.
      </td>
    </tr>
    <tr>
      <td><code>deployType</code></td>
      <td>string</td>
      <td>
        <code>GAE</code> (App Engine) or <code>RUN</code> (Cloud Run). Must match the type encoded in
        <code>directoryName</code>.
      </td>
    </tr>
    <tr>
      <td><code>userEmail</code></td>
      <td>string</td>
      <td>
        The gcloud account that performed the deploy and the collection. Only deploys created by this user
        (case-insensitive) are candidates.
      </td>
    </tr>
  </tbody>
</table>

Matching rules (portal-side):

- Only deploys created by `userEmail` (case-insensitive) are candidates.
- The deploy must have been created at (or before) the collection timestamp, and within the configured
  window (`deploy-info.linking.window`, 15 minutes by default) before it.
- Among the candidates of all listed projects, the one with the newest creation time wins; ties on the
  exact same creation time are broken by listing order.
- If no plausible match is found, the matching is retried once after 5 minutes (a deploy may appear late,
  or the listing may fail transiently); if the retry still finds nothing, the upload's storage folder is
  deleted. The same retry applies to transient listing/credentials failures.
- A deploy identity that is already linked is never re-linked or overwritten.

<table>
  <thead>
    <tr>
      <th>Status</th>
      <th>Body</th>
      <th>Meaning</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>200 OK</td>
      <td><code>{"status":"created",…}</code></td>
      <td>A plausible deploy was found and linked.</td>
    </tr>
    <tr>
      <td>200 OK</td>
      <td><code>{"status":"already-linked",…}</code></td>
      <td>The matched deploy already had a link; the existing link was left untouched.</td>
    </tr>
    <tr>
      <td>202 Accepted</td>
      <td><code>{"status":"pending"}</code></td>
      <td>No match found (yet); the retry is scheduled and may still link the upload or delete its folder.</td>
    </tr>
    <tr>
      <td>422 Unprocessable Entity</td>
      <td><code>{"error":"unknown-project"}</code></td>
      <td>None of the listed projects is configured in the portal.</td>
    </tr>
    <tr>
      <td>400 Bad Request</td>
      <td>(empty)</td>
      <td>The body is invalid, or <code>directoryName</code> does not follow the naming convention.</td>
    </tr>
  </tbody>
</table>

## Collector contract — `GET /collector/bucket`

Called by the collector **before** it uploads: it asks the portal which Cloud Storage bucket its uploads
go to, so the portal's configuration stays the single source of truth (the collector has no bucket
parameter of its own).

<table>
  <thead>
    <tr>
      <th>Status</th>
      <th>Body</th>
      <th>Meaning</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>200 OK</td>
      <td><code>{"bucket":"&lt;name&gt;"}</code></td>
      <td>
        The configured bucket name (<code>deploy-info.storage-bucket</code>); uploads go to
        <code>gs://&lt;name&gt;</code>.
      </td>
    </tr>
    <tr>
      <td>500 Internal Server Error</td>
      <td>(empty)</td>
      <td>
        The bucket is not configured in the portal — a server misconfiguration, not a caller error. The collector
        must not attempt any upload.
      </td>
    </tr>
  </tbody>
</table>

## Main-screen contract — `GET /portal/tree`

A list of groups. Projects without a group (and links of projects no longer configured in the portal) are
under the `""` group, which is sorted last. Every level is sorted by name. Services are keyed by deploy
type, so the same service name may appear once as a GAE and once as a RUN service.

**Note:** when the on-request cleanup trigger is enabled and due
(see [deploy-cleanup.md](../portal/deploy-info-portal/documentation/backend/deploy-cleanup.md)), the cleanup sweep
runs first, therefore the request takes longer to complete. Otherwise, it is a pure Datastore read.

<table>
  <thead>
    <tr>
      <th>Field (per level)</th>
      <th>Type</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>name</code> (group)</td>
      <td>string</td>
      <td>The project group (<code>""</code> = ungrouped).</td>
    </tr>
    <tr>
      <td><code>projects[].projectId</code></td>
      <td>string</td>
      <td>The GCP project ID.</td>
    </tr>
    <tr>
      <td><code>projects[].services[].name</code></td>
      <td>string</td>
      <td>The service id.</td>
    </tr>
    <tr>
      <td><code>projects[].services[].type</code></td>
      <td>string</td>
      <td><code>GAE</code> or <code>RUN</code>.</td>
    </tr>
    <tr>
      <td><code>versions[].id</code></td>
      <td>string</td>
      <td>
        The deploy identity key
        <code>&lt;project&gt;_&lt;TYPE&gt;_&lt;location|-&gt;_&lt;service&gt;_&lt;version&gt;</code>; stable
        identifier for the version node.
      </td>
    </tr>
    <tr>
      <td><code>versions[].name</code></td>
      <td>string</td>
      <td>The version id (the display name).</td>
    </tr>
    <tr>
      <td><code>versions[].location</code></td>
      <td>string|null</td>
      <td>Cloud Run region; null for App Engine.</td>
    </tr>
    <tr>
      <td><code>versions[].url</code></td>
      <td>string|null</td>
      <td>The deploy's public URL, when known.</td>
    </tr>
    <tr>
      <td><code>versions[].author</code></td>
      <td>string</td>
      <td>The gcloud account that deployed and collected.</td>
    </tr>
    <tr>
      <td><code>versions[].timestamp</code></td>
      <td>number</td>
      <td>Epoch millis of the collection timestamp.</td>
    </tr>
    <tr>
      <td><code>versions[].storageFolder</code></td>
      <td>string</td>
      <td>The upload's storage folder name — pass it to the content endpoint.</td>
    </tr>
  </tbody>
</table>

## Main-screen contract — `GET /portal/deploy/content?folder=<storageFolder>`

Returns the collected files (GIT section and extras) of one upload folder — the `storageFolder` from the tree's version
node. Since the content is always the same, the request response is cached.

<table>
  <thead>
    <tr>
      <th>Status</th>
      <th>Body</th>
      <th>Meaning</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>200 OK</td>
      <td>DeployContent (below)</td>
      <td>
        The collected files. Every part is optional: <code>git</code> is null when the upload contains no git data;
        <code>extras</code> is empty when there are none.
      </td>
    </tr>
    <tr>
      <td>404 Not Found</td>
      <td>(empty)</td>
      <td>The folder does not exist (or is empty) — e.g. the cleanup sweep removed it after the tree was loaded.</td>
    </tr>
    <tr>
      <td>502 Bad Gateway</td>
      <td>(empty)</td>
      <td>Cloud Storage could not be read (a portal issue, not a user issue).</td>
    </tr>
    <tr>
      <td>400 Bad Request</td>
      <td>(empty)</td>
      <td>The <code>folder</code> parameter is missing.</td>
    </tr>
  </tbody>
</table>

## Project configuration endpoints (frontend only)

The Projects screen of the frontend manages the GCP projects whose deploys the portal will link (see
[projects.md](../portal/deploy-info-portal/documentation/backend/projects.md)). The four endpoints are
protected like every other endpoint. Outcomes are always reported in the response body, not through HTTP
error statuses: an operation that could not be performed returns `200 OK` with an `issues` object
(below) instead of the resulting project list.

- `GET /project/all` — the configured projects: `200 OK` with a list of `ProjectDTO` (below), or
  `204 No Content` (empty body) when no project is configured.
- `POST /project` — adds a project. Request body: `ProjectDTO`. The portal validates the project's
  service account (see below) before saving.
- `PUT /project` — edits the project identified by the body's `projectId`. The service account is only
  re-validated when it changed — an unchanged one was already validated when the project was added or
  last edited.
- `DELETE /project/{projectId}` — deletes the project and returns `200 OK` with the remaining project
  list. Deleting an unknown project is a no-op that still returns the list.

### `ProjectDTO` (request body of POST/PUT; items of every project list response)

<table>
  <thead>
    <tr>
      <th>Field</th>
      <th>Type</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>projectId</code></td>
      <td>string</td>
      <td>The GCP project ID (the exact ID, not the console display name).</td>
    </tr>
    <tr>
      <td><code>group</code></td>
      <td>string|null</td>
      <td>The project group shown in the navigator tree; null = ungrouped.</td>
    </tr>
    <tr>
      <td><code>serviceAccount</code></td>
      <td>string</td>
      <td>
        The project's service account the portal impersonates to list the project's deploys
        (see <a href="../portal/deploy-info-portal/documentation/backend/gcp-deploy-listing.md">gcp-deploy-listing.md</a>).
      </td>
    </tr>
  </tbody>
</table>

### Result body of `POST /project` and `PUT /project`

<table>
  <thead>
    <tr>
      <th>Field</th>
      <th>Type</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>issues</code></td>
      <td>Issues|null</td>
      <td>Present when the operation was not performed (any issue below); null when it succeeded.</td>
    </tr>
    <tr>
      <td><code>projects</code></td>
      <td>list of ProjectDTO|null</td>
      <td>All configured projects after a successful save; null when <code>issues</code> is present.</td>
    </tr>
  </tbody>
</table>

`Issues` fields (codes as strings in the body; <code>issueProjectIdConflict</code> only for POST,
<code>issueProjectNotFound</code> only for PUT):

<table>
  <thead>
    <tr>
      <th>Field</th>
      <th>Type</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>issueProjectIdConflict</code></td>
      <td>boolean</td>
      <td>A project with this <code>projectId</code> is already configured; the project was not saved.</td>
    </tr>
    <tr>
      <td><code>issueProjectNotFound</code></td>
      <td>boolean</td>
      <td>No project with this <code>projectId</code> is configured; nothing was changed.</td>
    </tr>
    <tr>
      <td><code>issueServiceAccountError</code></td>
      <td>boolean</td>
      <td>The service-account validation failed; the project was not saved.</td>
    </tr>
    <tr>
      <td><code>serviceAccountIssue</code></td>
      <td>string|null</td>
      <td>The <code>ServiceAccountIssue</code> code (below), only when <code>issueServiceAccountError</code>.</td>
    </tr>
    <tr>
      <td><code>portalServiceAccount</code></td>
      <td>string|null</td>
      <td>
        The portal's own service account email — the principal to grant the missing IAM roles to in the
        remediation instructions. Null when it cannot be determined (e.g. the portal runs with user
        credentials).
      </td>
    </tr>
  </tbody>
</table>

### Service-account validation (`serviceAccountIssue` codes)

Before saving, the portal impersonates the project's service account via the IAM Credentials API and
lists the project's App Engine and Cloud Run deploys with it. On failure the project is <strong>not</strong>
saved and the result carries one of these codes:

<table>
  <thead>
    <tr>
      <th>Code</th>
      <th>Meaning</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>MISSING_IMPERSONATION_PERMISSION</code></td>
      <td>
        The portal lacks <code>roles/iam.serviceAccountTokenCreator</code> on the target service account.
        Fixable by the user.
      </td>
    </tr>
    <tr>
      <td><code>MISSING_LISTING_PERMISSION</code></td>
      <td>
        The service account lacks the App Engine Viewer / Cloud Run Viewer roles required to list the
        project's deploys. Fixable by the user.
      </td>
    </tr>
    <tr>
      <td><code>APIS_NOT_ENABLED</code></td>
      <td>
        A listing API — the App Engine Admin API or the Cloud Run Admin API — is not enabled in the
        project. Fixable by the user: enable the API(s) for the project.
      </td>
    </tr>
    <tr>
      <td><code>PROJECT_NOT_FOUND</code></td>
      <td>
        The listing API cannot resolve the project: the entered name is not the exact GCP project ID, or
        the project does not exist. Fixable by the user. Caveat: the App Engine Admin API answers a
        wrong/unreachable project id with a plain <code>PERMISSION_DENIED</code> — it cannot tell
        "no permission" from "does not exist" — so that case still reports
        <code>MISSING_LISTING_PERMISSION</code>; Cloud Run returns <code>NOT_FOUND</code> and reports
        <code>PROJECT_NOT_FOUND</code>.
      </td>
    </tr>
    <tr>
      <td><code>PORTAL_ISSUE</code></td>
      <td>A portal-side problem the user cannot fix — needs developer/admin attention.</td>
    </tr>
  </tbody>
</table>

A request body that is not valid JSON or does not match `ProjectDTO` is rejected with
`400 Bad Request` (empty body).