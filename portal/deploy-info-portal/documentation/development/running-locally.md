# Running the portal locally

How to run the portal backend and frontend on a local machine. For the overall project context see the
root [README](../../../../README.md) and
[local-development.md](../../../../documentation/local-development.md).

## Backend

The app auto-selects its profile: if the `GAE_DEPLOYMENT_ID` env var exists → `prod`, otherwise → `local`.
Therefore, running locally just works:

```
cd portal/deploy-info-portal
./gradlew bootRun
```

Starts on **http://localhost:8080**.

With the `local` profile, Spring Cloud GCP starts a **Datastore emulator** automatically on port 8090 —
`gcloud` CLI must be installed. Data persists between runs; the emulator must not already be running on
8090 or startup fails.

Note: startup requires **Application Default Credentials** (`gcloud auth application-default login`),
because the Storage autoconfiguration needs them; otherwise startup fails.

### Running the unit tests

```
./gradlew test
```

The tests run with the `test` profile, which excludes the GCP Storage and Datastore auto-configurations
and uses in-memory fakes/mocks instead: no GCP credentials, no Datastore emulator, no internet access.

## Frontend (`ui/`)

Angular 22 + Material:

```
cd portal/deploy-info-portal/ui
npm install
npm start
```

Serves on **http://localhost:4200** (`ng serve`, development configuration).

There is **no Angular proxy** — the frontend calls the backend directly at a URL from
`src/environments/`: the dev build points at `http://localhost:8080`, the prod build uses same-origin
relative URLs (the packaged jar serves the UI itself, see
[frontend-serving.md](frontend-serving.md)). For the local cross-origin pairing, the backend's CORS
configuration already allows `http://localhost:4200` in the local/test profiles; add a value to a
profile's properties file only if the UI runs on another origin.

### Running the frontend unit tests

```
npm test        # or: npx ng test --watch=false for a single run
```

The tests **always run headless** — no desktop browser needed. A pretest hook resolves the headless
browser binary (the `CHROME_BIN` environment variable overrides it) and, if absent, downloads
`chrome-headless-shell` on demand (one-time download).

### Serving the frontend from the fat jar

`./gradlew bootJar` compiles the frontend and embeds it into the jar, so it serves the UI itself on the
same origin as the API. Details: [frontend-serving.md](frontend-serving.md). Only the
frontend-embedding tasks need Node/npm: `test` and `bootRun` work without them.

## Deploying to GCP App Engine

The fat jar can be deployed straight from Gradle — see [deployment.md](deployment.md).
