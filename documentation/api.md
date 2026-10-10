# API contracts

Reference for both collectors (`collector-java`) and the portal frontend (`portal/deploy-info-portal/ui`).
All bodies are JSON. Base URL depends on the environment (local vs. deployed portal).

## Who uses which contract

| Client         | Endpoints                                       |
|----------------|--------------------------------------------------|
| Collector (`collector-java`) | `POST /collector/handleNewDirectory`, `GET /collector/bucket` |
| Frontend (`portal/deploy-info-portal/ui`) | `GET /portal/tree`, `GET /portal/deploy/content` |

Prerequisite: the folder the deploy-link endpoint points at must follow the upload folder layout documented in
[`collector-java/deploy-info-collector/documentation/collector-plugin.md`](../collector-java/deploy-info-collector/documentation/collector-plugin.md)
(the Cloud Storage folder section) — the content contract depends on it.

## Collector contract — `POST /collector/handleNewDirectory`

Called by the collector after it has uploaded its output directory to the portal's Cloud Storage
bucket. The portal then tries to link the upload to the GCP deploy it was built for (see
`portal/deploy-info-portal/documentation/deploy-link.md`).

### Request body

| Field           | Type           | Description |
|-----------------|----------------|-------------|
| `directoryName` | string         | The collector's output directory name, convention `<user>_<deployType>_<epochMillis>` (e.g. `jdoe_GAE_1735689600000`). The collection timestamp is **not** sent explicitly — the portal derives it from the epoch-millis suffix of this name. |
| `projects`      | list of string | Candidate GCP projects the code may have been deployed to. The collector cannot know which one (e.g. PROD, DEV or QA), so it sends all of them; the portal matches against the deploys of **all** listed projects together and picks the deploy closest below the collection timestamp, regardless of which project it belongs to. Projects not configured in the portal are silently skipped. |
| `deployType`    | string         | `GAE` (App Engine) or `RUN` (Cloud Run). Must match the deploy type encoded in `directoryName`. |
| `userEmail`     | string         | The gcloud account that performed the deploy and the collection. Only deploys created by this user (case-insensitive) are candidates. |

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

| Status | Body | Meaning |
|--------|------|---------|
| `200 OK` | `{"status":"created","project":…,"service":…,"version":…}` | A plausible deploy was found and linked. |
| `200 OK` | `{"status":"already-linked","project":…,"service":…,"version":…}` | The matched deploy already had a link; the existing link was left untouched. |
| `202 Accepted` | `{"status":"pending"}` | No match found (yet); the retry is scheduled and may still link the upload or delete its folder. |
| `422 Unprocessable Entity` | `{"status":"unknown-project"}` | None of the listed projects is configured in the portal; the upload's folder is deleted after the retry. |
| `400 Bad Request` | (empty) | The body is invalid, or `directoryName` does not follow the `<user>_<deployType>_<epochMillis>` convention. |

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

| Status | Body | Meaning |
|--------|------|---------|
| `200 OK` | `{"bucket":"<name>"}` | The configured bucket name (`deploy-info.storage-bucket`); uploads go to `gs://<name>`. |
| `500 Internal Server Error` | (empty) | The bucket is not configured in the portal — a server misconfiguration, not a caller error. The collector must not attempt any upload. |

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

| Field (per level)                | Type           | Description |
|----------------------------------|----------------|-------------|
| `name` (group)                   | string         | The project group (`""` = ungrouped). |
| `projects[].projectId`           | string         | The GCP project ID. |
| `projects[].services[].name`     | string         | The service id. |
| `projects[].services[].type`     | string         | `GAE` or `RUN`. |
| `versions[].id`                  | string         | The deploy identity key `<project>_<TYPE>_<location|->_<service>_<version>`. Stable identifier for the version node. |
| `versions[].name`                | string         | The version id (the display name). |
| `versions[].location`            | string\|null   | Cloud Run region; null for App Engine. |
| `versions[].url`                 | string\|null   | The deploy's public URL, when known. |
| `versions[].author`              | string         | The gcloud account that deployed and collected. |
| `versions[].timestamp`           | number         | Epoch millis of the collection timestamp. |
| `versions[].storageFolder`       | string         | The upload's storage folder name — pass it to the content endpoint. |

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

| Status | Body | Meaning |
|--------|------|---------|
| `200 OK` | `DeployContent` (below) | The collected files. Every part is optional: `git` is null when the upload contains no git data; `extras` is empty when there are none. |
| `404 Not Found` | (empty) | The folder does not exist (or is empty) — e.g. the cleanup sweep removed it after the tree was loaded. |
| `502 Bad Gateway` | (empty) | Cloud Storage could not be read (a portal issue, not a user issue). |
| `400 Bad Request` | (empty) | The `folder` parameter is missing. |

```json
{
  "git": {
    "gitlog": "commit 1a2b3c ...",
    "gitstatus": "On branch main ...",
    "changes": [
      { "filepath": "/src/main.kt", "content": "diff --git a/src/main.kt ..." }
    ]
  },
  "extras": [
    { "filepath": "/docs/readme.md", "content": "# readme" }
  ]
}
```
