# DeployInfo — Project Status Report

> Report based on a full review of the code (collector plugin, portal backend, Angular frontend, git history) and TODO.md, extrapolated against the overall project goal: let users see exactly which commits and local changes were in the code of each GCP AppEngine or CloudRun deploy.

## 1. High-level picture

| Component | Status |
|---|---|
| Gradle collector plugin (`collector-java/deploy-info-collector`) | **~80% done** — collects, uploads, but portal trigger is a stub; missing 2 parameters |
| Python collector script | **0% — does not exist at all** (no directory anywhere in the workspace) |
| Portal backend (`portal/deploy-info-portal`) | **~40% done** — Settings CRUD working; GCP deploy-listing clients implemented (with service-account impersonation); the "deploy" linking core is missing |
| Portal frontend (`portal/deploy-info-portal/ui`) | **~45% done** — full UI shell built on mock data; no real backend integration for the main screen |
| DevOps / deployment of the portal itself | **~10%** — no app.yaml/Dockerfile, no frontend build integration |

---

## 2. Gradle collector plugin — what exists

**Implemented and functional:**

- Plugin class + `deployInfoCollect` task registered under group `deployInfo` (`CollectorPlugin.kt`, `CollectorTask.kt`).
- Parameters: `storageBucket`, `deployType` (GAE/RUN), plus optional `maxFileSize` (default 50KB), `collectGitStatus`, `extraFilesToCollect`.
- `GitCollector`: saves `git-status.txt`, `git-log.txt`, and per-changed-file diffs (or full content for untracked files) into UUID-named files with a `gitUuidMap` (uuid → filepath).
- `ExtraFilesCollector`: copies extra files (skips binary/large/missing) with `extraUuidMap`, writes `uuid-extra.properties` / `uuid-git.properties` at upload time (`Uploader`).
- `Uploader`: `gcloud storage cp --recursive` of the output dir to the bucket, with stderr error check.
- `Context`: creates `build/collector/<user>_<deployType>_<timestamp>/`, gets gcloud account email, writes `collector.properties` (email, deploy, timestamp).
- Cross-platform command execution (gcloud.cmd on Windows), logging, file utils (binary/large detection).
- Unit tests exist for GitCollector, ExtraFilesCollector, Uploader, Context.
- `README.md` with usage docs; published via `com.gradle.plugin-publish`.

**Missing / incomplete:**

1. **`PortalTrigger` is a stub** — `action/PortalTrigger.kt` is literally `// TODO: Send request to portal`. Nothing calls the portal.
2. **New parameters (TODO):** `portalUrl` and the list of projects where the code may be deployed. Neither exists in `CollectorTask`.
3. **Portal trigger body (TODO):** output directory name, user email, list of projects, deploy type — none of this is sent anywhere.
4. **Delete `collector.properties`** (TODO: "Context — Delete collector.properties") — it's still written in `Context.createPropertiesFile`. Once the portal trigger carries email/type/timestamp in the request body, the properties file becomes redundant. (Open question: should the info move entirely into the trigger request, or stay in the file *and* the request?)
5. Documentation debt: `README.md` parameter table will need the two new parameters added.

---

## 3. Python collector — what exists

**Nothing.** There is no Python code anywhere in the workspace (no `collector-python/` or similar directory). This entire component must be created:

- A script mirroring the Gradle plugin's behavior: git collection, extra files, uuid maps, `gcloud storage cp` upload, and portal trigger POST (once the trigger contract exists — ideally build this **after** the trigger endpoint/body is finalized so both collectors implement the same contract).
- Open design questions to decide: CLI arg names/defaults, distribution (pip package? single script?), Python version target, and whether it reuses the same bucket output-directory naming convention (`<user>_<deployType>_<timestamp>`).


---

## 4. Portal backend — what exists

**Implemented:**

- Spring Boot 3.5 + Kotlin 21 app (`DeployInfoPortalApplication.kt`) with local/prod/test profiles; runs on GAE by detecting `GAE_DEPLOYMENT_ID`.
- **Settings vertical fully working end-to-end:** `Project` domain, `ProjectRepository` interface, `ProjectDatastoreRepository` (Datastore, entity `deploy-info/project`), use cases (Add/Edit/Delete/GetAll) with unit tests, `SettingsController` with DTOs/responses, wired as beans. The UI already calls these endpoints.
- Datastore emulator config for local/test; `TraceIdFilter` for logging; CORS config.
- Build uses version catalog (`libs.versions.toml`) — this TODO item is **done for the portal**.

**Missing / stubs:**

1. **`TriggerController.handleNewDirectory()` is empty** — the core of the system. Needed:
   - Request body: output directory name, user email, list of possible projects, deploy type.
   - Search all versions/services of the configured projects (via GCP APIs using each project's service account) to find the most recent deploy of that type.
   - Linking logic: if most recent deploy < 5 min old and not yet linked, create the Datastore link record (project, deploy type+service+version as identifier, Cloud Storage folder, email, timestamp).
2. **No Datastore model/repository for the deploy link** — only `Project` exists.
3. **GCP deploy-listing clients now exist** (`application/GcpAppEngineLister` / `GcpCloudRunLister` +
   `infrastructure/gcp/...` implementations, unit-tested without any external dependency) —
   impersonation (access tokens per project's service account, via the Google auth library) is
   implemented; no endpoint exposes the listing yet. See
   `portal/deploy-info-portal/documentation/gcp-deploy-listing.md`.
4. **`AddProjectUseCase`:** `issueServiceAccountError` is hardcoded `false` with `// TODO: Test if name + serviceAccount are working` — service-account permission validation not implemented (same presumably needed for Edit).
5. **`PortalController` is an empty stub** (`GET /portal/` returning nothing). Needs:
   - Main-screen data endpoint: tree of project > service > version, with validity check (delete Datastore entry + Storage folder if deploy no longer exists in GCP). The validity/cleanup sweep itself is now implemented as a scheduled job (`CleanupInvalidDeployLinksUseCase` + `CleanupScheduler`, off by default via `deploy-info.cleanup.enabled=false`) — see `portal/deploy-info-portal/documentation/deploy-cleanup.md`; the tree endpoint must still decide whether to additionally run it on requests (see TODO.md Phase 1.2).
   - File-content endpoint(s): serve `git-log.txt`, `git-status.txt`, uuid-mapped files ("GIT" and "Extras" sections) from the Cloud Storage folder.
6. **No login/auth at all** — no Spring Security dependency, no auth of any kind. TODO: check user permissions on the deployInfo project via the AppEngine service account. Also the trigger endpoint has no authentication (anyone could POST).
7. **`application-prod.properties` is empty** — no prod config (project id, bucket name, etc.).
8. Minor: `CorsConfiguration` hardcodes `http://localhost:4200` (won't work in prod).


---

## 5. Portal frontend — what exists

**Implemented (Angular 19 + Material):**

- App shell with routing (`/`, `/settings`); dark/light themes, styles.
- **Top bar** (Emporium-style: "DeployInfo" left) — matches TODO; logout/configuration icons present but their actions go nowhere.
- **Deploy navigator** (left tree: group > project > service > version) — fully built with sort/highlight logic, but **fed by hardcoded `EXAMPLE_DATA_*` constants**; note it already implements a "group" level above project which TODO.md doesn't mention.
- **File viewer** (right panel) — complete UI: deploy header (link, author, timestamp), GIT tabs (log/status/changes) and Extras, syntax highlighting incl. custom `git` language — but **fed by fake `DeployData` example objects with a `delay(1000)` simulation** and `// TODO: Get data`.
- **Settings screen** — fully wired to real backend (get/add/edit/delete projects, dialogs).
- `environment.ts` uses `@url@` placeholder (so the TODO "deploy script that sets @url@ and copies files" is half-prepared).

**Missing:**

1. Network service for the main screen (navigator + file viewer) and the file-content endpoints; replace all mock data.
2. Logout icon behavior / login flow entirely.
3. Deploy script that replaces `@url@` and copies the built UI into the backend's static resources (no such script exists; also the Spring Boot app doesn't currently serve the UI).
4. The `.spec.ts` test files are all default Angular scaffolding — no meaningful frontend tests.

---

## 6. DevOps / packaging (TODO section)

- "Change build.gradle to use version toml": ambiguous. The **portal already uses the version catalog**; the **collector plugin still uses inline versions** (`kotlin("jvm") version "2.1.10"`) and is the only build file where this TODO still applies. *Noted as-is in the report; confirm later whether the intent was the collector.*
- "Update libraries": portal was updated recently (Spring Boot 3.5.9, Spring Cloud GCP 7.4.1, per commit `83f91c1`). Collector: Kotlin 2.1.10 / mockk 1.14.0 — verify these are current at implementation time.
- **Missing entirely (not in TODO.md but required):**
  - Deployment config for the portal itself: no `app.yaml`, no Dockerfile, no App Engine/Cloud Run deploy task in `portal/deploy-info-portal/build.gradle.kts` (e.g., app-gradle-plugin), no UI build integration.
  - GCP infrastructure setup docs/scripts: bucket creation, Datastore, service accounts + IAM roles (deployInfo project SA needs permission to impersonate each configured project's SA; each SA needs App Engine Viewer / Cloud Run Viewer), Cloud Storage lifecycle rule for cleanup would be a sensible addition.
  - Top-level documentation (root `README.md` is just `# deploy-info`).
  - CI (no GitHub Actions or similar anywhere).

---

## 7. Suggested implementation order

1. **Define the trigger contract** (endpoint + request body) — it unblocks both collectors.
2. Collector plugin: add `portalUrl` + projects-list params, implement `PortalTrigger`, remove `collector.properties`.
3. Portal: implement `TriggerController` — GCP listing clients (GAE + Cloud Run via impersonated SAs), 5-minute linking rule, Datastore link entity + repository.
4. Portal: implement `PortalController` — tree data + file content from Storage, cleanup of deleted deploys.
5. Frontend: replace mock data with real endpoints; logout.
6. Auth: login/permission checks; secure the trigger endpoint.
7. Service-account validation in Add/Edit project use cases; Settings UI already supports the error flags.
8. Python collector (mirror the final contract).
9. Packaging/DevOps: version catalog for collector (pending confirmation), UI build/deploy script, app.yaml, prod properties, CORS, READMEs.

---

## 8. Open questions to resolve (no guessing)

1. TODO's "version toml" — collector, portal, or both? (Portal looks already done.)
2. Should `collector.properties` be removed entirely, or kept in the uploaded folder for traceability while the trigger also sends the data?
3. Link table identifier (deploy type + service + version): TODO asks "Separate columns? Single column?" — still undecided.
4. For the Python collector: expected distribution (pip package vs standalone script) and arg style?
5. Is the "group" level in the frontend navigator an intended feature (beyond TODO's project > service > version), backed by `Project.group`? Backend already has the `group` field, so likely yes — but the navigator needs data to populate it.

---
