# Serving the frontend from the portal

How the portal fat jar compiles and serves the Angular frontend, so users only need the jar (the
UI and the API come from the same origin).

## Build-time embedding

The Gradle build (`portal/deploy-info-portal/build.gradle.kts`) registers three tasks, wired into
`bootJar`:

- `npmInstallUi` — installs the frontend dependencies (`npm ci` in `ui/`; skipped when
  `node_modules` is up to date).
- `buildUi` — compiles the frontend (`ng build`, production configuration by default).
- `packageUi` — copies the build output (`ui/dist/ui/browser`) to a Gradle build folder.

`bootJar` embeds that folder under `BOOT-INF/classes/static` — Spring Boot's default classpath
static location, so no serving dependency is added. Both npm tasks run `npm.cmd` on Windows and
`npm` elsewhere, so the build works on both operating systems. Node/npm are only needed for the
frontend-embedding tasks: `test` and `bootRun` keep working without them (the local dev pairing is
`ng serve` on 4200 + backend on 8080, see [running-locally.md](running-locally.md)).

## Runtime serving

- Static files are served from `classpath:/static` (the embedded build output).
- `/` serves `index.html` (Spring Boot welcome page).
- Unknown **extension-less** paths outside the backend API prefixes (`collector`, `portal`,
  `settings`) fall back to `index.html`, so deep links and page refreshes on Angular routes work
  (Angular's client-side router takes over). Missing assets (paths with an extension) and API paths
  return their normal 404s — the fallback lives in `infrastructure/frontend/`
  (`FrontendResourceResolver` + `FrontendServingConfiguration`).
- The API endpoints keep working unchanged: Spring MVC consults request-mapped controllers before
  resource handlers.

## Same-origin (no CORS)

Because the UI is served by the same origin as the API, no CORS configuration is needed in
production — `deploy-info.cors.allowed-origins` is empty in `application-prod.properties` (the key
is defined explicitly in every properties file, never in the base one, so each profile decides its
value), and an empty origin list disables CORS processing entirely (browsers
send an `Origin` header even on same-origin non-GET requests, and a registered CORS mapping would
reject them; see `infrastructure/security/CorsConfiguration.kt`). Accordingly, the production
frontend build uses relative URLs: `ui/src/environments/environment.ts` sets `url: ''`. The local
dev build replaces that file (angular.json file replacements) with
`environment.development.ts`, which points at the backend on `http://localhost:8080`.
