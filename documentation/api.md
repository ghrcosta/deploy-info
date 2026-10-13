# API contracts

Reference for both collectors (`collector-java`) and the portal frontend (`portal/deploy-info-portal/ui`).
All bodies are JSON. Base URL depends on the environment (local vs. deployed portal).

## Authentication

When the portal has authentication enabled (`deploy-info.auth.enabled` — the prod profile), every
endpoint below requires an `Authorization: Bearer <token>` header:

<table>
  <tr><th>Client</th><th>Token</th></tr>
  <tr>
    <td>Collector</td>
    <td>gcloud access token (<code>gcloud auth print-access-token</code>) of a user present in the IAM of the
      portal's project (directly or through a group)</td>
  </tr>
  <tr>
    <td>Frontend</td>
    <td>Google Identity Services ID token of the same kind of user, obtained via the sign-in flow</td>
  </tr>
</table>

Rejection codes (the same for every protected endpoint):

<table>
  <tr><th>Status</th><th>Body</th><th>Meaning</th></tr>
  <tr>
    <td><code>401 Unauthorized</code></td>
    <td><code>{"error":"unauthenticated"}</code></td>
    <td>Token missing, malformed, expired or wrongly signed.</td>
  </tr>
  <tr>
    <td><code>403 Forbidden</code></td>
    <td><code>{"error":"not-permitted","email":"…"}</code></td>
    <td>Valid token, but the account is not present in the portal project's IAM.</td>
  </tr>
  <tr>
    <td><code>403 Forbidden</code></td>
    <td><code>{"error":"email-mismatch","tokenEmail":"…","requestUserEmail":"…"}</code></td>
    <td>Trigger only: the body's <code>userEmail</code> does not match the token's account (possible with gcloud
      service-account impersonation; see
      <code>portal/deploy-info-portal/documentation/authentication.md</code>).</td>
  </tr>
  <tr>
    <td><code>503 Service Unavailable</code></td>
    <td><code>{"error":"portal-issue"}</code></td>
    <td>The portal could not run its IAM membership check.</td>
  </tr>
</table>

With authentication disabled (local/test profiles) the header is simply not required.

## Auth endpoints (frontend only)

- `GET /auth/config` — public; `{"enabled":<bool>,"clientId":"<oauth-client-id>"}` — the sign-in
  configuration for the login screen. When `enabled` is false the frontend skips the login flow.
- `GET /auth/user` — protected; `{"email":"<token-account-email>"}` — the identity of the
  authenticated user (shown in the top bar; the sign-in flow calls it after receiving a credential).

## Who uses which contract

| Client         | Endpoints                                       |
|----------------|--------------------------------------------------|
| Collector | `POST /collector/handleNewDirectory`, `GET /collector/bucket` |
| Frontend | `GET /auth/config`, `GET /auth/user`, `GET /portal/tree`, `GET /portal/deploy/content` |

Prerequisite: the folder the deploy-link endpoint points at must follow the upload folder layout documented in
[`collector-plugin.md`](../collector-java/deploy-info-collector/documentation/collector-plugin.md)
(the Cloud Storage folder section) — the content contract depends on it.

## Collector contract — `POST /collector/handleNewDirectory`

Called by the collector after it has uploaded its output directory to the portal's Cloud Storage
bucket. The portal then tries to link the upload to the GCP deploy it was built for (see
`portal/deploy-info-portal/documentation/deploy-link.md`).

### Request body

<table>
  <tr><th>Field</th><th>Type</th><th>Description</th></tr>
  <tr>
    <td><code>directoryName</code></td>
    <td>string</td>
    <td>The collector's output directory name, convention
      <code>&lt;user&gt;_&lt;deployType&gt;_&lt;epochMillis&gt;</code>
      (e.g. <code>jdoe_GAE_1735689600000</code>). The collection timestamp is <strong>not</strong> sent explicitly —
      the portal derives it from the epoch-millis suffix of this name.</td>
  </tr>
  <tr>
    <td><code>projects</code></td>
    <td>list of string</td>
    <td>Candidate GCP projects the code may have been deployed to. The collector cannot know which one (e.g. PROD, DEV
      or QA), so it sends all of them; the portal matches against the deploys of <strong>all</strong> listed projects
      together and picks the deploy closest below the collection timestamp, regardless of which project it belongs to.
      Projects not configured in the portal are silently skipped.</td>
  </tr>
  <tr>
    <td><code>deployType</code></td>
    <td>string</td>
    <td><code>GAE</code> (App Engine) or <code>RUN</code> (Cloud Run). Must match the deploy type encoded in
      <code>directoryName</code>.</td>
  </tr>
  <tr>
    <td><code>userEmail</code></td>
    <td>string</td>
    <td>The gcloud account that performed the deploy and the collection. Only deploys created by this user
      (case-insensitive) are candidates.</td>
  </tr>
</table>

```json
{
  "directoryName": "jdoe_GAE_1735689600000",
  "projects": ["proj-prod", "proj-dev", "proj-qa"],
  "deployType": "GAE",
  "userEmail": "jdoe@example.com"
}
```

### Matching rules (portal-side)

- Only deploys created by `userEmail` (case-insensitive) are candidates.
- The deploy must have been created at (or before) the collection timestamp, and within the
  configured window (`deploy-info.linking.window`, 15 minutes in every environment's configuration) before it.
- Among the surviving candidates of **all** listed projects, the one with the newest creation time
  (i.e. the closest below the collection timestamp) wins. Ties on the exact same creation time are
  broken by listing order.
- If no plausible match is found, the matching is retried once after 5 minutes (a deploy may appear
  late, or the listing may fail transiently); if the retry still finds nothing, the upload's
  storage folder is deleted. The same retry applies to transient listing/credentials failures.
- A deploy identity that is already linked is never re-linked or overwritten.

### Responses

<table>
  <tr><th>Status</th><th>Body</th><th>Meaning</th></tr>
  <tr>
    <td><code>200 OK</code></td>
    <td><code>{"status":"created","project":…,"service":…,"version":…}</code></td>
    <td>A plausible deploy was found and linked.</td>
  </tr>
  <tr>
    <td><code>200 OK</code></td>
    <td><code>{"status":"already-linked","project":…,"service":…,"version":…}</code></td>
    <td>The matched deploy already had a link; the existing link was left untouched.</td>
  </tr>
  <tr>
    <td><code>202 Accepted</code></td>
    <td><code>{"status":"pending"}</code></td>
    <td>No match found (yet); the retry is scheduled and may still link the upload or delete its folder.</td>
  </tr>
  <tr>
    <td><code>422 Unprocessable Entity</code></td>
    <td><code>{"status":"unknown-project"}</code></td>
    <td>None of the listed projects is configured in the portal; the upload's folder is deleted after the retry.</td>
  </tr>
  <tr>
    <td><code>400 Bad Request</code></td>
    <td>(empty)</td>
    <td>The body is invalid, or <code>directoryName</code> does not follow the
      <code>&lt;user&gt;_&lt;deployType&gt;_&lt;epochMillis&gt;</code> convention.</td>
  </tr>
</table>

```json
{
  "status": "created",
  "project": "proj-qa",
  "service": "web",
  "version": "v42"
}
```

## Collector contract — `GET /collector/bucket`

Called by the collector **before** it uploads: it asks the portal which Cloud Storage bucket its
uploads go to, so the portal's configuration stays the single source of truth (the collector has no
bucket parameter of its own).

### Responses

<table>
  <tr><th>Status</th><th>Body</th><th>Meaning</th></tr>
  <tr>
    <td><code>200 OK</code></td>
    <td><code>{"bucket":"&lt;name&gt;"}</code></td>
    <td>The configured bucket name (<code>deploy-info.storage-bucket</code>); uploads go to
      <code>gs://&lt;name&gt;</code>.</td>
  </tr>
  <tr>
    <td><code>500 Internal Server Error</code></td>
    <td>(empty)</td>
    <td>The bucket is not configured in the portal — a server misconfiguration, not a caller error. The collector
      must not attempt any upload.</td>
  </tr>
</table>

```json
{
  "bucket": "deploy-info-uploads"
}
```

## Main-screen contract — `GET /portal/tree`

Called by the frontend navigator. **Side effect:** when the on-request cleanup trigger is enabled
and due (see `portal/deploy-info-portal/documentation/deploy-cleanup.md`), the validity/cleanup
sweep runs first — so the response is slower by the sweep's duration roughly once per cleanup
interval; otherwise it is a pure Datastore read.

### Response `200 OK`

A list of groups. Projects without a group (and links of projects no longer configured in the
portal) are under the `""` group, which is sorted last. Every level is sorted by name. Services are
keyed by deploy type, so the same service name may appear once as a GAE and once as a RUN service.

<table>
  <tr><th>Field (per level)</th><th>Type</th><th>Description</th></tr>
  <tr><td><code>name</code> (group)</td><td>string</td><td>The project group (<code>""</code> = ungrouped).</td></tr>
  <tr><td><code>projects[].projectId</code></td><td>string</td><td>The GCP project ID.</td></tr>
  <tr><td><code>projects[].services[].name</code></td><td>string</td><td>The service id.</td></tr>
  <tr><td><code>projects[].services[].type</code></td><td>string</td><td><code>GAE</code> or <code>RUN</code>.</td></tr>
  <tr>
    <td><code>versions[].id</code></td>
    <td>string</td>
    <td>The deploy identity key
      <code>&lt;project&gt;_&lt;TYPE&gt;_&lt;location|->_&lt;service&gt;_&lt;version&gt;</code>.
      Stable identifier for the version node.</td>
  </tr>
  <tr><td><code>versions[].name</code></td><td>string</td><td>The version id (the display name).</td></tr>
  <tr><td><code>versions[].location</code></td><td>string|null</td><td>Cloud Run region; null for App Engine.</td></tr>
  <tr><td><code>versions[].url</code></td><td>string|null</td><td>The deploy's public URL, when known.</td></tr>
  <tr>
    <td><code>versions[].author</code></td>
    <td>string</td>
    <td>The gcloud account that deployed and collected.</td>
  </tr>
  <tr><td><code>versions[].timestamp</code></td><td>number</td><td>Epoch millis of the collection timestamp.</td></tr>
  <tr>
    <td><code>versions[].storageFolder</code></td>
    <td>string</td>
    <td>The upload's storage folder name — pass it to the content endpoint.</td>
  </tr>
</table>

```json
[
  {
    "name": "PROD",
    "projects": [
      {
        "projectId": "proj-qa",
        "services": [
          {
            "name": "web",
            "type": "GAE",
            "versions": [
              {
                "id": "proj-qa_GAE_-_web_v42",
                "name": "v42",
                "type": "GAE",
                "location": null,
                "url": "https://v42-dot-web.proj-qa.appspot.com",
                "author": "jdoe@example.com",
                "timestamp": 1735689600000,
                "storageFolder": "jdoe_GAE_1735689600000"
              }
            ]
          }
        ]
      }
    ]
  }
]
```

## Main-screen contract — `GET /portal/deploy/content?folder=<storageFolder>`

Returns the collected files (GIT section and extras) of one upload folder, exactly as carried by the
version nodes of the tree — so this endpoint touches Cloud Storage only, never Datastore. The folder
name is the collector's output directory name (e.g. `jdoe_GAE_1735689600000`).

### Query parameter

| Parameter | Required | Description |
|-----------|----------|-------------|
| `folder`  | yes      | The `storageFolder` from the tree's version node. |

### Responses

<table>
  <tr><th>Status</th><th>Body</th><th>Meaning</th></tr>
  <tr>
    <td><code>200 OK</code></td>
    <td><code>DeployContent</code> (below)</td>
    <td>The collected files. Every part is optional: <code>git</code> is null when the upload contains no git data;
      <code>extras</code> is empty when there are none.</td>
  </tr>
  <tr>
    <td><code>404 Not Found</code></td>
    <td>(empty)</td>
    <td>The folder does not exist (or is empty) — e.g. the cleanup sweep removed it after the tree was loaded.</td>
  </tr>
  <tr>
    <td><code>502 Bad Gateway</code></td>
    <td>(empty)</td>
    <td>Cloud Storage could not be read (a portal issue, not a user issue).</td>
  </tr>
  <tr>
    <td><code>400 Bad Request</code></td>
    <td>(empty)</td>
    <td>The <code>folder</code> parameter is missing.</td>
  </tr>
</table>

### Caching

A `200` response carries `Cache-Control: private, max-age=604800, immutable`: an upload folder's
content never changes after the collector wrote it (the only lifecycle event is the cleanup sweep
deleting the whole folder, which the tree then no longer links to), so the browser may reuse the
response for a week without revalidation. The `private` scope keeps shared caches (proxies/CDNs)
out — the endpoint sits behind authentication — and the error responses carry no cache header, so
they are never cached.

```json
{
  "git": {
    "gitlog": "commit 1a2b3c ...",
    "gitstatus": "On branch main ...",
    "changes": [
      { "filepath": "/src/main.kt", "content": "diff --git a/src/main.kt ...", "status": "modified" },
      { "filepath": "/src/new-file.kt", "content": "full file content ...", "status": "new" },
      { "filepath": "/src/untracked-file.kt", "content": "full file content ...", "status": "untracked" }
    ]
  },
  "extras": [
    { "filepath": "/docs/readme.md", "content": "# readme", "status": "modified" }
  ]
}
```

`status` comes from the collector's `git status --porcelain` state as recorded in `uuid-git.csv`:
`new` (a porcelain code containing `A` — the file was added to the index), `untracked` (porcelain
code `??`), or `modified` (everything else); extras are always `modified`.
