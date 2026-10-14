# Running the deploy-info project locally

How to run every component on a local machine: the collectors, the portal backend (Spring Boot +
Datastore emulator) and the Angular frontend. Per-component details live in each component's
`documentation/` folder — the links are in the [root README](../README.md), and the portal-specific ones
in [running-locally.md](../portal/deploy-info-portal/documentation/development/running-locally.md).

## Prerequisites

<table>
  <thead>
    <tr>
      <th>Tool</th>
      <th>Version used</th>
      <th>Notes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>JDK</td>
      <td>
        <strong>21</strong> (portal backend) / <strong>17+</strong> (collector plugin — Gradle downloads it if
        needed)
      </td>
      <td></td>
    </tr>
    <tr>
      <td>Node.js + npm</td>
      <td>Node 24.x, pinned in the frontend's <code>ui/.nvmrc</code></td>
      <td>Only needed for the frontend.</td>
    </tr>
    <tr>
      <td>gcloud CLI</td>
      <td>any recent version, on <code>PATH</code></td>
      <td>
        Needed by the collectors (account detection, upload) and for the Datastore emulator.
        The user must be logged in (<code>gcloud auth login</code> done).
      </td>
    </tr>
    <tr>
      <td>GCP project + bucket</td>
      <td>any test project</td>
      <td>For the collector upload; the portal backend tests run fully local (no GCP access needed).</td>
    </tr>
  </tbody>
</table>

## End-to-end validation checklist

1. `./gradlew test` in `collector-java/deploy-info-collector` — unit tests pass (no gcloud needed).
2. `./gradlew test` in `portal/deploy-info-portal` — unit tests pass (requirements: see
   [running-locally.md](../portal/deploy-info-portal/documentation/development/running-locally.md)).
3. `./gradlew bootRun` in `portal/deploy-info-portal` — backend up on 8080 with a Datastore emulator on
   8090 (requirements: see
   [running-locally.md](../portal/deploy-info-portal/documentation/development/running-locally.md)).
4. `npm start` in `ui` — UI up on 4200, talking to the backend. Alternatively, `./gradlew bootJar` in
   `portal/deploy-info-portal` compiles the frontend, embeds it into the fat jar and serves it on 8080
   from the same origin as the API (see
   [frontend-serving.md](../portal/deploy-info-portal/documentation/development/frontend-serving.md)).
5. `npm test` in `ui` — frontend unit tests pass.

Authentication is **disabled in the local profile** — every step above runs without a Google sign-in.
To exercise authentication locally, follow the portal's
[authentication.md](../portal/deploy-info-portal/documentation/backend/authentication.md).
