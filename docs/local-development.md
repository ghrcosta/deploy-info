# Running the deploy-info project locally

How to run every component of the project on a local machine: the Gradle collector plugin, the portal backend (Spring Boot + Datastore emulator), and the Angular frontend.

> **Current state (Phase 0 / POC stage):** the collector works end to end except the portal trigger (stub); the portal backend has the Settings CRUD only (deploy listing, linking, and file serving are Phase 1 work); the frontend main screen still runs on mock data (Settings screen is wired to the real backend). See [collector-plugin.md](collector-plugin.md) and `STATUS_REPORT.md`.

## 1. Prerequisites

| Tool | Version used | Notes |
|---|---|---|
| JDK | **21** (portal backend) / **17+** (collector plugin — toolchain; Gradle downloads it if needed) | Portal `build.gradle.kts` pins `JavaLanguageVersion.of(21)`. |
| Gradle | via wrapper | Portal: 8.13 (`gradlew.bat` in `portal/deploy-info-portal`); Collector: 8.10 (`gradlew.bat` in `collector-java/deploy-info-collector`). |
| Node.js + npm | for Angular 19 (Node 18/20/22 work) | Only needed for the frontend. |
| gcloud CLI | any recent version, on `PATH` | Needed by the collector (`gcloud config get-value account`, `gcloud storage cp`) and for the Datastore emulator (installed with gcloud). |
| Authenticated gcloud account | `gcloud auth login` done | The collector reads the account email and uploads to a bucket as this user. |
| GCP project + bucket | any test project | For the collector upload; the portal backend itself can run fully local (see §2). |

Note (Windows): the code translates `gcloud` → `gcloud.cmd` automatically (`util/SystemUtils.kt`), so plain `gcloud` commands work on both OSes.

## 2. Portal backend

Located at `portal/deploy-info-portal` (Spring Boot 3.5.x, Kotlin, Spring Cloud GCP 7.x).

The app **auto-selects its profile** (`infrastructure/DeployInfoPortalApplication.kt`): if the `GAE_DEPLOYMENT_ID` env var exists → `prod`, otherwise → `local`. So running locally just works:

```
cd portal/deploy-info-portal
.\gradlew.bat bootRun
```

Starts on **http://localhost:8080**.

With the `local` profile (`src/main/resources/application-local.properties`):

```
spring.cloud.gcp.datastore.emulator.enabled=true
spring.cloud.gcp.datastore.emulator.port=8090
spring.cloud.gcp.datastore.emulator.firestore-in-datastore-mode=true
```

Spring Cloud GCP starts a **Datastore emulator** automatically on port 8090 (firestore-in-datastore-mode) — you do **not** need to start it manually, but the `gcloud` CLI must be installed (that's where the emulator binary comes from). Data persists between runs (emulator data dir under the gcloud user directory); the emulator must not already be running on 8090 or startup fails.

Existing REST endpoints (Settings CRUD only, in `ui/settings/SettingsController.kt`):

- `GET /settings/projects` — list configured projects
- `POST /settings/projects` — add project (body: `name`, `group?`, `serviceAccount`)
- `PUT /settings/projects/{name}` — edit project
- `DELETE /settings/projects/{name}` — delete project

What works locally vs. what needs stubbing (Phase 1 items):

- ⚠️ **Known gap (found by actually running it):** `bootRun` currently fails during startup with
  `BeanCreationException: Error creating bean with name 'storage'` → `Your default credentials were not found`
  (`GcpStorageAutoConfiguration`). The `spring-cloud-gcp-starter-storage` dependency in `build.gradle.kts` is
  auto-configured even though nothing uses Storage yet, and it requires **Application Default Credentials** (ADC)
  regardless of profile. Two workarounds until Phase 1 wires the real Storage usage:
  1. set up ADC: `gcloud auth application-default login` (and `gcloud config set project <id>`), **or**
  2. comment out `implementation(libs.spring.cloud.gcp.starter.storage)` in `portal/deploy-info-portal/build.gradle.kts`
     while developing locally (nothing uses it yet).
  Consider feeding this back into Phase 1 (e.g. make the storage bean conditional or emulator-backed).
- ✅ Works (after applying a workaround above): Settings CRUD against the local Datastore emulator, CORS (see §3), and the controller skeletons exist (`ui/trigger/TriggerController.kt` → `POST /trigger/handleNewDirectory` no-op; `ui/portal/PortalController.kt` → `GET /portal` no-op).
- ⛔ Not yet implemented at all: GCP App Engine / Cloud Run deploy listing (impersonated service accounts), the collector-upload linking logic, and reading file contents from the Cloud Storage upload folder. These pieces don't exist yet; the local profile needs no further GCP configuration because nothing calls GCP yet. Once Phase 1 adds the listing/storage code, local runs will need either a real (test) GCP project with credentials or emulator-based stubs.
To run the backend tests: `.\gradlew.bat test` (uses the `test` profile, no emulator required).

## 3. Frontend

Located at `portal/deploy-info-portal/ui` (Angular 19, Material).

```
cd portal/deploy-info-portal/ui
npm install
npm start
```

Serves on **http://localhost:4200** (`ng serve`, development configuration).

There is **no Angular proxy** — the frontend calls the backend directly at an absolute URL from `src/environments/`:

- dev build uses `environment.development.ts` → `url: 'http://localhost:8080'`
- prod `environment.ts` still has the `@url@` placeholder (to be replaced by the Phase 10 deploy script)

Because it's a cross-origin call, the **backend CORS config matters**: `infrastructure/security/CorsConfiguration.kt` hardcodes `http://localhost:4200` as the only allowed origin (with credentials). So the dev pairing of backend 8080 + UI 4200 works out of the box; any other origin will be rejected until CORS is made configurable (Phase 1, item 5.5).

Current UI status: Settings screen is fully wired to the backend; the deploy navigator and file viewer still use `EXAMPLE_DATA_*` mocks and a simulated `delay(1000)`.

## 4. Using the collector locally

The plugin is not on a remote repo/portal yet — publish it to your local Maven first:

```
cd collector-java/deploy-info-collector
.\gradlew.bat publishToMavenLocal
```

Then, in any sample Gradle project:

1. Root `settings.gradle.kts` — add `mavenLocal()` to `pluginManagement.repositories`.
2. `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.ghrcosta.deploy-info-collector") version "0.0.1"
}

tasks.deployInfoCollect {
    storageBucket.set("my-test-bucket")
    deployType.set(CollectorTask.DeployType.GAE)
    // optional:
    // extraFilesToCollect.set(listOf("src/main/resources/application.properties"))
}
```

3. Run `.\gradlew.bat deployInfoCollect`.

What happens locally:

1. Requires `gcloud` on `PATH` **and** an authenticated account (`gcloud auth login`), since the output dir name is derived from the account email.
2. Output lands in the sample project's `build/collector/<user>_<GAE|RUN>_<timestamp>/`: `collector.properties`, `git-status.txt`, `git-log.txt`, uuid-named diff/content files, extra-file copies, `uuid-git.properties` / `uuid-extra.properties`.
3. Before upload, you can inspect that directory to verify what would be uploaded.

Pieces that cannot run fully local yet:

- **`gcloud storage cp` upload** needs a real (or test) bucket — there is no automatic Storage emulator wiring in the collector. Point `storageBucket` at a test bucket; if you only want to inspect the local output, note that the upload step failing will fail the task.
- **`PortalTrigger` is a stub** — nothing is sent to the portal; the only side effect is the bucket upload. Once Phase 1 defines the trigger contract (`docs/api.md`), it will POST to the running backend from §2.
- **GCP deploy listing on the portal** doesn't exist yet (Phase 1), so there's no local "link" to observe after upload.

## 5. End-to-end validation checklist (what works today)

1. `.\gradlew.bat test` in `collector-java/deploy-info-collector` — unit tests pass (no gcloud needed).
2. `.\gradlew.bat bootRun` in `portal/deploy-info-portal` — backend up on 8080 with Datastore emulator on 8090. ⚠️ Requires ADC (`gcloud auth application-default login`) or the storage-starter workaround from §2, otherwise startup fails (see the known gap above).
3. `npm start` in `ui` — UI up on 4200; Settings screen lists/adds/edits/deletes projects against the backend.
4. Publish + apply the collector to a sample project; run `deployInfoCollect`; inspect `build/collector/<user>_<type>_<ts>/`, then verify the same folder appears in `gs://<bucket>`.

Gaps found during validation (and what changes once the backend core exists) should be fed back into `TODO.md` / `STATUS_REPORT.md`.
