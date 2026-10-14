# Serving the frontend from the portal

How the portal fat jar compiles and serves the Angular frontend, so users only need the jar (the UI and
the API come from the same origin).

## Build-time embedding

The Gradle build installs and compiles the frontend and `bootJar` embeds the build output under Spring
Boot's default classpath static location.

## Runtime serving

- Static files are served from the embedded build output; `/` serves `index.html`.
- Unknown **extension-less** paths outside the backend API prefixes fall back to `index.html`, so deep
  links and page refreshes on Angular routes work (Angular's client-side router takes over). Missing
  assets (paths with an extension) and API paths return their normal 404s.
- The API endpoints keep working unchanged: request-mapped controllers are consulted before resource
  handlers.

## Same-origin (no CORS)

Because the UI is served by the same origin as the API, no CORS configuration is needed in production:
an empty `deploy-info.cors.allowed-origins` disables CORS processing entirely (browsers send an `Origin`
header even on same-origin non-GET requests, and a registered CORS mapping would reject them). The key
is defined explicitly in every properties file, never in the base one, so each profile decides its own
value. Accordingly, the production frontend build uses relative URLs; the local dev build points at the
backend on `http://localhost:8080`.
