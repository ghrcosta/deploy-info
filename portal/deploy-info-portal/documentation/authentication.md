# Authentication (Phase 2.7)

How the portal authenticates its callers — both the browser users and the Gradle collector.

## What it guarantees

Every authenticated request must present a Google token of an account that is **present in the IAM
of the project the portal is deployed in** — directly, or **through a group** listed there. There
is no allowlist and no separate user database: whoever can see the project's IAM (any role on it)
can use the portal; anyone else is rejected.

## Mechanism

No Spring Security dependency — a servlet filter (`infrastructure/security/AuthenticationFilter.kt`)
protects the API prefixes and delegates to `application/auth/`:

1. **Token verification** — Google's tokeninfo endpoint (`oauth2.googleapis.com/tokeninfo`) validates
   the token's signature; the portal then checks expiry and, for browser tokens, the audience.
   Two token kinds are accepted:

   <table>
     <tr><th>Caller</th><th>Token</th><th>Audience requirement</th></tr>
     <tr>
       <td>Browser</td>
       <td>OpenID Connect <strong>ID token</strong> from the Google Identity Services sign-in (the frontend's
         <code>login</code> component)</td>
       <td>must equal <code>deploy-info.auth.client-id</code></td>
     </tr>
     <tr>
       <td>Collector</td>
       <td>OAuth <strong>access token</strong> from <code>gcloud auth print-access-token</code></td>
       <td>none</td>
     </tr>
   </table>

   Verification results are cached per token (5 minutes); IAM results per email (10 minutes for
   granted access, 1 minute for denials — a newly granted user is not locked out for long).

2. **IAM membership check** — `infrastructure/gcp/GcpIamMembershipChecker.kt` runs the **Cloud Asset
   API's `AnalyzeIamPolicy`** on the portal's own project with the portal's own Application Default
   Credentials:

   - `identitySelector` restricts the analysis to the user;
   - `expandGroups` traverses the group-membership graph, so a user bound to the project only
     through a Google Group is found too (this is why a plain `getIamPolicy` check would not do —
     it cannot expand groups);
   - any non-empty analysis result counts as "present in the IAM" (the query has no access
     restriction).

   The portal's project id is resolved from the **`GOOGLE_CLOUD_PROJECT` environment variable**,
   which the App Engine runtime always sets (the same resolution the Cloud Logging stack relies
   on, see `logging.md`); outside App Engine the instance metadata server is consulted as a
   fallback. When exercising authentication locally, set the variable by hand.

3. **HTTP enforcement** — 401 (`{"error":"unauthenticated"}`) for a missing/invalid token, 403
   (`{"error":"not-permitted","email":…}`) for an authenticated user without access, 503
   (`{"error":"portal-issue"}`) when the IAM check itself cannot run. CORS preflights (`OPTIONS`)
   always pass through.

## IAM prerequisites (portal side)

The portal's service account needs to run the analysis on the portal project:

- `cloudasset.assets.analyzeIamPolicy` on the project — granted by `roles/cloudasset.viewer` (or
  a custom role with that permission);
- the **Cloud Asset API** must be enabled in the portal's project.

If either is missing, users are not told they lack access: the check fails with
`AuthenticationIssue.PORTAL_IAM_CONFIG` and the HTTP layer answers 503 — the misconfiguration is
visible in the portal's logs.

## OAuth client (browser sign-in)

The frontend signs in with Google Identity Services, which needs an **OAuth client id** (web
application type) whose *Authorized JavaScript origins* include the portal's serving URL (and
`http://localhost:4200` for the `ng serve` setup). Set it as `deploy-info.auth.client-id` — the
portal refuses to start with auth enabled and the client id blank. The id is served publicly by
`GET /auth/config`; it is not a secret (only the client's *origins* restrict where the sign-in is
offered), while every API request is verified portal-side.

## Collector

The collector fetches the token with `gcloud auth print-access-token` (the same account that
performs the upload — gcloud is a hard prerequisite anyway) and sends it as the `Authorization:
Bearer` header on `GET /collector/bucket` and `POST /collector/handleNewDirectory`. When no token
can be obtained, the request is sent without the header — a portal with auth disabled (local
development) works unchanged, and a portal with auth enabled answers with a `GradleException` that
names the fix (login, IAM grant, or — for the email-mismatch case below — the impersonation setup).

### userEmail match (defense in depth)

In the standard flow, both the request body's `userEmail` and the token derive from the same active
gcloud account, so they always match. The portal still compares them
(`deploy-info.auth.collector-require-email-match`, default `true`) so a hand-crafted request cannot
use a valid token of an IAM-permitted account to attribute its upload to another user's deploy
identity. Environments that deploy via gcloud service-account impersonation
(`gcloud config set auth/impersonate_service_account`) produce a token whose email is the service
account while `userEmail` may report the user — set the property to `false` there.

## Configuration

<table>
  <tr><th>Property</th><th>Meaning</th><th>Default</th></tr>
  <tr>
    <td><code>deploy-info.auth.enabled</code></td>
    <td>Whether the filter is active</td>
    <td><code>false</code> (<code>true</code> in the prod profile)</td>
  </tr>
  <tr>
    <td><code>deploy-info.auth.client-id</code></td>
    <td>OAuth client id (audience of the browser ID tokens)</td>
    <td>empty (required when enabled)</td>
  </tr>
  <tr>
    <td><code>deploy-info.auth.collector-require-email-match</code></td>
    <td>Trigger's <code>userEmail</code> must equal the token email</td>
    <td><code>true</code></td>
  </tr>
</table>

## API additions

- `GET /auth/config` — public; `{enabled, clientId}` for the frontend's sign-in screen.
- `GET /auth/user` — protected; `{email}` of the authenticated user.

See `documentation/api.md` for the full contracts (all protected endpoints now require the
`Authorization: Bearer <token>` header).
