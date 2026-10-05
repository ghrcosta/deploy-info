# API contracts

Reference for both collectors (`collector-java`) and the portal frontend (`portal/deploy-info-portal/ui`).
All bodies are JSON. Base URL depends on the environment (local vs. deployed portal).

## Trigger contract — `POST /trigger/handleNewDirectory`

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
  configured window (`deploy-info.linking.window`, default 15 minutes) before it.
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
