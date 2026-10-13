# Running the deploy-info project locally

How to run every component of the project on a local machine: the Gradle collector plugin, the portal
backend (Spring Boot + Datastore emulator), and the Angular frontend. Per-component details live in
each component's `documentation/` folder:

- Portal backend & frontend:
  [`portal/deploy-info-portal/documentation/`](../portal/deploy-info-portal/documentation/running-locally.md)
  (also covers the Settings feature and the GCP deploy listing)
- Collector plugin:
  [`collector-plugin.md`](../collector-java/deploy-info-collector/documentation/collector-plugin.md)

> **Current state (Phase 1 / POC stage):** the collector works end to end except the portal trigger
> (stub); the portal backend has the Settings CRUD working and the GCP deploy-listing clients
> implemented (listing APIs not exposed yet; impersonation is pending); the frontend main screen
> still runs on mock data (Settings screen is wired to the real backend). See `STATUS_REPORT.md`.

## 1. Prerequisites

<table>
  <tr><th>Tool</th><th>Version used</th><th>Notes</th></tr>
  <tr>
    <td>JDK</td>
    <td><strong>21</strong> (portal backend) / <strong>17+</strong> (collector plugin — toolchain; Gradle downloads
      it if needed)</td>
    <td>Portal <code>build.gradle.kts</code> pins <code>JavaLanguageVersion.of(21)</code>.</td>
  </tr>
  <tr>
    <td>Node.js + npm</td>
    <td>for Angular 22 (Node 18/20/22 work)</td>
    <td>Only needed for the frontend.</td>
  </tr>
  <tr>
    <td>chrome-headless-shell</td>
    <td>154.x (installed on demand)</td>
    <td>Headless browser for the <strong>frontend unit tests only</strong>. Install via
      <code>npx @puppeteer/browsers install chrome-headless-shell@154.0.8037.92</code>, or let <code>npm test</code>
      auto-install it (see step 5 of the checklist). Auto-discovered from <code>~/.cache/puppeteer</code>; set
      <code>CHROME_BIN</code> to override.</td>
  </tr>
  <tr>
    <td>gcloud CLI</td>
    <td>any recent version, on <code>PATH</code></td>
    <td>Needed by the collector (<code>gcloud config get-value account</code>, <code>gcloud storage cp</code>) and
      for the Datastore emulator (installed with gcloud).</td>
  </tr>
  <tr>
    <td>Authenticated gcloud account</td>
    <td><code>gcloud auth login</code> done</td>
    <td>The collector reads the account email and uploads to a bucket as this user.</td>
  </tr>
  <tr>
    <td>GCP project + bucket</td>
    <td>any test project</td>
    <td>For the collector upload; the portal backend tests run fully local (no GCP access needed).</td>
  </tr>
</table>

Note (Windows): the code translates `gcloud` → `gcloud.cmd` automatically (`util/SystemUtils.kt`), so plain `gcloud`
commands work on both OSes.

## 2. Component documentation

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
   workaround, otherwise startup fails (see the known gap in the portal documentation).
4. `npm start` in `ui` — UI up on 4200; Settings screen lists/adds/edits/deletes projects against the
   backend. Alternatively, `.\gradlew.bat bootJar` in `portal/deploy-info-portal` compiles the
   frontend, embeds it into the fat jar and serves it on 8080 from the same origin as the API (no
   CORS involved; see the portal's `documentation/frontend-serving.md`).
5. `npm test` in `portal/deploy-info-portal/ui` — frontend unit tests pass, always headless
   (`chrome-headless-shell` from the Puppeteer cache; no desktop browser needed; auto-installed by
   the `pretest` hook if missing — one-time ~260 MB download, needs internet). Details in the
   portal documentation's frontend section.
6. Publish + apply the collector to a sample project; run `deployInfoCollect`; inspect
   `build/collector/<user>_<type>_<ts>/`, then verify the same folder appears in `gs://<bucket>`.

   Note: the collector now resolves its upload bucket from the portal first (`GET /collector/bucket`,
   see [`documentation/api.md`](api.md)) — so the portal backend must be reachable (step 3) before
   `deployInfoCollect` can run; the bucket name comes from the portal's `deploy-info.storage-bucket`
   property.

Authentication (Phase 2.7) is **disabled in the local profile** — every step above runs without a
Google sign-in. To exercise it locally: create an OAuth client id (web application, authorized
JavaScript origin `http://localhost:4200`), grant your account any role on the portal's GCP
project, then run the backend with `deploy-info.auth.enabled=true`, `deploy-info.auth.client-id=<id>`
and the `GOOGLE_CLOUD_PROJECT` environment variable set to the portal's project id (the App Engine
runtime sets it automatically; locally it must be provided by hand). See
[`authentication.md`](../portal/deploy-info-portal/documentation/authentication.md).

Gaps found during validation (and what changes once the backend core exists) should be fed back into
`TODO.md` / `STATUS_REPORT.md`.


