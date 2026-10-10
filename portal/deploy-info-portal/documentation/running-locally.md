# Running the portal locally

How to run the portal backend and frontend on a local machine. For the overall project context see
the root [README](../../../README.md) and [local-development.md](../../../documentation/local-development.md).

## Backend

Located in this repository root (`portal/deploy-info-portal`; Spring Boot 4.1.x, Kotlin, Spring Cloud
GCP 7.x).

The app **auto-selects its profile** (`infrastructure/DeployInfoPortalApplication.kt`): if the
`GAE_DEPLOYMENT_ID` env var exists → `prod`, otherwise → `local`. So running locally just works:

```
cd portal/deploy-info-portal
.\gradlew.bat bootRun
```

Starts on **http://localhost:8080**.

With the `local` profile (`application-local.properties`), Spring Cloud GCP starts a **Datastore
emulator** automatically on port 8090 (firestore-in-datastore-mode) — you do **not** need to start it
manually, but the `gcloud` CLI must be installed (that's where the emulator binary comes from). Data
persists between runs (emulator data dir under the gcloud user directory); the emulator must not
already be running on 8090 or startup fails.

Existing REST endpoints (Settings CRUD only):

- `GET /settings/projects` — list configured projects
- `POST /settings/projects` — add project (body: `name`, `group?`, `serviceAccount`)
- `PUT /settings/projects/{name}` — edit project
- `DELETE /settings/projects/{name}` — delete project

(see [settings-projects.md](settings-projects.md) for details)

### Known gap: Cloud Storage auto-configuration

`bootRun` currently fails during startup with `BeanCreationException: Error creating bean with name
'storage'` → `Your default credentials were not found` (`GcpStorageAutoConfiguration`). The
`spring-cloud-gcp-starter-storage` dependency in `build.gradle.kts` is auto-configured even though
nothing uses Storage yet, and it requires **Application Default Credentials** (ADC) regardless of
profile. Two workarounds until Phase 1 wires the real Storage usage:

1. set up ADC: `gcloud auth application-default login` (and `gcloud config set project <id>`), **or**
2. comment out `implementation(libs.spring.cloud.gcp.starter.storage)` in `build.gradle.kts` while
   developing locally (nothing uses it yet).

Consider feeding this back into Phase 1 (e.g. make the storage bean conditional or emulator-backed).

### Running the unit tests

```
.\gradlew.bat test
```

The tests run with the `test` profile (`application-test.properties`), which **excludes the GCP
Storage and Datastore auto-configurations** and uses in-memory fakes/mocks instead. Tests have **no
external dependencies**: no GCP credentials, no Datastore emulator, no internet access.

## Frontend (`ui/`)

Angular 22 + Material:

```
cd portal/deploy-info-portal/ui
npm install
npm start
```

Serves on **http://localhost:4200** (`ng serve`, development configuration).

There is **no Angular proxy** — the frontend calls the backend directly at a URL from
`src/environments/`:

- dev build uses `environment.development.ts` → `url: 'http://localhost:8080'`
- prod build uses `environment.ts` → `url: ''` (same-origin relative URLs — the packaged jar serves
  the UI itself, see below)

Because it's a cross-origin call, the **backend CORS config matters**:
`infrastructure/security/CorsConfiguration.kt` reads
`deploy-info.cors.allowed-origins` (comma-separated; `DeployInfoProperties.Cors`). The key is
defined in every properties file, never in the base one — `application-local.properties` and
`application-test.properties` set `http://localhost:4200`, so the dev pairing of backend 8080 +
UI 4200 works out of the box. An **empty value disables CORS processing entirely** — this is what
the production deploy needs, because there the UI is built into the same jar and served from the
same origin, and browsers still send an `Origin` header on non-GET requests: any registered CORS
mapping would reject those requests (`application-prod.properties` therefore sets the key to an
empty value, explicitly, since a profile file only overrides keys it defines). Add a value to a
profile's properties file only if the UI runs on another origin.

### Serving the frontend from the fat jar

`./gradlew bootJar` compiles the frontend (`npm ci` + `ng build`; `npm.cmd` on Windows, `npm`
elsewhere) and embeds it into `BOOT-INF/classes/static`, so the jar serves the UI itself on the
same origin as the API — with a frontend fallback (`infrastructure/frontend/`) for deep links and page
refreshes. Details: [frontend-serving.md](frontend-serving.md). Only the frontend-embedding tasks need
Node/npm: `test` and `bootRun` work without them.

Current UI status: the Settings screen is fully wired to the backend; the deploy navigator and file
viewer still use `EXAMPLE_DATA_*` mocks and a simulated `delay(1000)`.

## Deploying to GCP App Engine

The fat jar can be deployed straight from Gradle to GCP App Engine Standard (Java 21):

```
./gradlew appengineDeploy -PgaeProjectId=<gcp-project> [-PgaeService=<service>]
```

The version name is `<portal_version>-<timestamp>`, the GCP project and service are configurable
via the `-P` properties above. Details: [deployment.md](deployment.md).
