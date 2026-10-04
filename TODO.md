# DeployInfo TODO

Goal ordering: get a working end-to-end POC first. Implementation starts with the portal backend core; the API contract is defined only once that core exists; the Gradle plugin is then adapted to it. Everything non-essential (auth, Python collector, DevOps) is deferred until the POC is proven.

---

## Phase 0 — Documentation (before implementation)

### A. How the Gradle collector plugin works

- [x] Document the plugin/task entry points: `CollectorPlugin.kt`, `CollectorTask.kt` — registration, task group, all parameters (`storageBucket`, `deployType`, `maxFileSize`, `collectGitStatus`, `extraFilesToCollect`) with types, defaults, meaning
- [x] Document the execution flow end to end: what happens when `deployInfoCollect` runs, and which components are invoked (`Context`, `GitCollector`, `ExtraFilesCollector`, `Uploader`, `PortalTrigger`)
- [x] Document `Context`: local output directory creation (`build/collector/<user>_<deployType>_<timestamp>/`), gcloud account email detection, `collector.properties` content (email, deploy, timestamp)
- [x] Document `GitCollector`: what is collected (`git-status.txt`, `git-log.txt`, per-changed-file diffs, full content for untracked files), binary/large-file handling, UUID naming + `gitUuidMap`
- [x] Document `ExtraFilesCollector`: which extra files are copied, skip rules (binary/large/missing), `extraUuidMap`
- [x] Document the upload artifacts: `uuid-git.properties` / `uuid-extra.properties` written by `Uploader`, and the `gcloud storage cp --recursive` upload target (bucket + output dir naming convention), stderr error handling
- [x] Document `PortalTrigger` (currently a stub) and cross-platform concerns (`gcloud.cmd` on Windows, `CommandUtils`, `SystemUtils`)
- [x] Document the resulting Cloud Storage folder layout (final structure of the uploaded directory) — the contract the backend file-content serving will rely on

### B. How to run the entire project locally

- [x] Document prerequisites: JDK version, Node/npm for the Angular UI, gcloud CLI (incl. Datastore & Storage emulators), an authenticated gcloud account (the collector reads it), a test GCP project/bucket
- [x] Document running the portal backend: `local` profile (Datastore emulator on port 8090, firestore-in-datastore-mode), what works locally vs what needs stubbing (GCP listing APIs, impersonation) — verify by actually running it
- [x] Document running the frontend: `ui` dev server on port 4200, proxy/CORS interplay with the backend (CORS currently hardcoded to localhost:4200)
- [x] Document using the collector locally: how to apply the plugin to a sample project, run `deployInfoCollect`, and where output lands before upload
- [x] Document (or stub) the pieces that cannot run fully local yet — gcloud storage upload to a real/test bucket, `PortalTrigger` (stub), GCP deploy listing — and note what changes once the backend core exists
- [x] Validate the doc by following it end to end on a clean checkout; fix any gaps found
- [x] Deliverables: per-component documentation folders — `collector-java/deploy-info-collector/documentation/` (plugin internals) and `portal/deploy-info-portal/documentation/` (features + local run) — plus the cross-component `docs/local-development.md` (link from root README)

---

## Phase 1 — POC (critical path)

### 1. Portal backend core (first — the contract depends on it)

- [x] Datastore model + repository for the deploy link (project, deployType/service/version/location as identifier, storage folder, email, timestamp) — see [`portal/deploy-info-portal/documentation/deploy-link.md`](portal/deploy-info-portal/documentation/deploy-link.md)
- [x] GCP deploy-listing client for App Engine (list services/versions) — see [`portal/deploy-info-portal/documentation/gcp-deploy-listing.md`](portal/deploy-info-portal/documentation/gcp-deploy-listing.md)
- [x] GCP deploy-listing client for Cloud Run (list services/revisions) — see [`portal/deploy-info-portal/documentation/gcp-deploy-listing.md`](portal/deploy-info-portal/documentation/gcp-deploy-listing.md)
- [x] Impersonation: generate an access token for each configured project's service account to call the listing APIs — see [`portal/deploy-info-portal/documentation/gcp-deploy-listing.md`](portal/deploy-info-portal/documentation/gcp-deploy-listing.md)
- [ ] Core linking logic: given a collector upload, find the most recent deploy of the given type; if < 5 min old and not yet linked, create the link record
- [ ] File-content capability: read `git-log.txt`, `git-status.txt`, per-changed-file diffs (GIT section) and uuid-mapped extras (Extras section) from the Cloud Storage folder (structure per Phase 0 doc)
- [ ] Validity/cleanup logic: detect deploys that no longer exist in GCP and delete Datastore entry + Storage folder

### 2. Define the API contracts (based on the working core)

- [ ] Trigger contract: `POST /trigger/handleNewDirectory` request/response — body fields (output dir name, user email, projects list, deploy type) derived from what the backend core actually needs
- [ ] Main-screen contracts: tree endpoint (group > project > service > version), file-content endpoint(s)
- [ ] Document the contracts (e.g. `docs/api.md`) as the reference for both collectors and the frontend

### 3. Collector plugin (adapt to the contract)

- [ ] Add `portalUrl` parameter to `CollectorTask`
- [ ] Add "list of projects where the code may be deployed" parameter to `CollectorTask`
- [ ] Implement `PortalTrigger` per the documented contract
- [ ] Remove `collector.properties` from `Context.createPropertiesFile` if the contract makes it redundant
- [ ] Update `README.md` parameter table with the two new parameters
- [ ] Unit tests for `PortalTrigger` (mock HTTP)

### 4. Portal REST layer

- [ ] `TriggerController.handleNewDirectory()`: wire request DTO ? core linking logic
- [ ] `PortalController`: tree endpoint + file-content endpoints, backed by the core

### 5. Frontend integration (`portal/deploy-info-portal/ui`)

- [ ] Network service for the navigator tree; replace `EXAMPLE_DATA_*` mocks
- [ ] Network service for the file viewer; replace fake `DeployData` + `delay(1000)` simulation; remove `// TODO: Get data`
- [ ] Wire deploy header (link, author, timestamp) to real data
- [ ] Wire configuration icon; logout can stay a no-op until Phase 2 auth
- [ ] CORS: make the allowed origin configurable (keep localhost:4200 for dev)

### 6. POC validation (end-to-end)

- [ ] Local run setup: Datastore emulator + either stubbed GCP listing or a real test GCP project
- [ ] Build with the plugin ? verify upload to bucket ? verify trigger call ? verify link created
- [ ] Open the frontend ? see the deploy in the navigator ? view git-log / git-status / extras
- [ ] Document any discovered gaps and feed them back into the relevant phase

---

## Phase 2 — After the POC is proven

### 7. Authentication

- [ ] Login: use the AppEngine service account to check user permissions on the deployInfo project
- [ ] Secure the trigger endpoint (currently anyone can POST)
- [ ] Frontend login flow + real logout behavior

### 8. Settings hardening

- [ ] `AddProjectUseCase`: implement service-account validation (token generation + list permission) — replace hardcoded `issueServiceAccountError = false`
- [ ] Same validation in the Edit project use case

### 9. Python collector

- [ ] Mirror the final trigger contract (build only after the contract is stable)
- [ ] Decide distribution: pip package vs standalone script; CLI args; Python version target
- [ ] Git collection, extra files, uuid maps, `gcloud storage cp` upload, portal trigger POST

### 10. DevOps / packaging

- [ ] Decide scope of the "version toml" TODO (portal already done; collector still inline)
- [ ] Collector: move to version catalog + verify library versions are current
- [ ] Deployment config for the portal: app.yaml/Dockerfile, app-gradle-plugin task in `portal/deploy-info-portal/build.gradle.kts`
- [ ] Deploy script: replace `@url@` in `environment.ts`, copy the built UI into backend static resources, make Spring Boot serve the UI
- [ ] `application-prod.properties`: project id, bucket name, CORS origin
- [ ] GCP infra docs/scripts: bucket, Datastore, service accounts + IAM (impersonation; App Engine Viewer / Cloud Run Viewer), Storage lifecycle rule
- [ ] Root `README.md`
- [ ] CI (GitHub Actions or similar)
- [ ] Frontend tests: replace default `.spec.ts` scaffolding with meaningful tests

