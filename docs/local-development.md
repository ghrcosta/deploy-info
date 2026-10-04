# Running the deploy-info project locally

How to run every component of the project on a local machine: the Gradle collector plugin, the portal
backend (Spring Boot + Datastore emulator), and the Angular frontend. Per-component details live in
each component's `documentation/` folder:

- Portal backend & frontend: [`portal/deploy-info-portal/documentation/`](../portal/deploy-info-portal/documentation/running-locally.md)
  (also covers the Settings feature and the GCP deploy listing)
- Collector plugin: [`collector-java/deploy-info-collector/documentation/collector-plugin.md`](../collector-java/deploy-info-collector/documentation/collector-plugin.md)

> **Current state (Phase 1 / POC stage):** the collector works end to end except the portal trigger
> (stub); the portal backend has the Settings CRUD working and the GCP deploy-listing clients
> implemented (listing APIs not exposed yet; impersonation is pending); the frontend main screen
> still runs on mock data (Settings screen is wired to the real backend). See `STATUS_REPORT.md`.

## 1. Prerequisites

| Tool | Version used | Notes |
|---|---|---|
| JDK | **21** (portal backend) / **17+** (collector plugin — toolchain; Gradle downloads it if needed) | Portal `build.gradle.kts` pins `JavaLanguageVersion.of(21)`. |
| Gradle | via wrapper | Portal: 8.13 (`gradlew.bat` in `portal/deploy-info-portal`); Collector: 8.10 (`gradlew.bat` in `collector-java/deploy-info-collector`). |
| Node.js + npm | for Angular 19 (Node 18/20/22 work) | Only needed for the frontend. |
| gcloud CLI | any recent version, on `PATH` | Needed by the collector (`gcloud config get-value account`, `gcloud storage cp`) and for the Datastore emulator (installed with gcloud). |
| Authenticated gcloud account | `gcloud auth login` done | The collector reads the account email and uploads to a bucket as this user. |
| GCP project + bucket | any test project | For the collector upload; the portal backend tests run fully local (no GCP access needed). |

Note (Windows): the code translates `gcloud` → `gcloud.cmd` automatically (`util/SystemUtils.kt`), so plain `gcloud` commands work on both OSes.

## 2. Component docs

- **Portal backend** (`portal/deploy-info-portal`): run instructions, profiles, Datastore emulator,
  unit tests and the Cloud Storage known gap —
  [`documentation/running-locally.md`](../portal/deploy-info-portal/documentation/running-locally.md).
- **Portal frontend** (`portal/deploy-info-portal/ui`): same document, frontend section.
- **Collector plugin** (`collector-java/deploy-info-collector`): usage in its
  [README](../collector-java/deploy-info-collector/README.md); internals in
  [`documentation/collector-plugin.md`](../collector-java/deploy-info-collector/documentation/collector-plugin.md).

## 3. End-to-end validation checklist (what works today)

1. `.\gradlew.bat test` in `collector-java/deploy-info-collector` — unit tests pass (no gcloud needed).
2. `.\gradlew.bat test` in `portal/deploy-info-portal` — unit tests pass (no GCP credentials, no
   Datastore emulator, no internet needed).
3. `.\gradlew.bat bootRun` in `portal/deploy-info-portal` — backend up on 8080 with Datastore emulator
   on 8090. ⚠️ Requires ADC (`gcloud auth application-default login`) or the storage-starter
   workaround, otherwise startup fails (see the known gap in the portal docs).
4. `npm start` in `ui` — UI up on 4200; Settings screen lists/adds/edits/deletes projects against the
   backend.
5. Publish + apply the collector to a sample project; run `deployInfoCollect`; inspect
   `build/collector/<user>_<type>_<ts>/`, then verify the same folder appears in `gs://<bucket>`.

Gaps found during validation (and what changes once the backend core exists) should be fed back into
`TODO.md` / `STATUS_REPORT.md`.


