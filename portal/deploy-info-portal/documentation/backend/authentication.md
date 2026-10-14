# Authentication

How the portal authenticates its callers — both the browser users and the collectors.

## What it guarantees

Every authenticated request must present a Google token of an account that is **present in the IAM of
the project the portal is deployed in** — directly, or through a group listed there. There is no
allowlist and no separate user database: whoever can see the project's IAM (any role on it) can use the
portal; anyone else is rejected.

## Mechanism

1. **Token verification** — Google's tokeninfo endpoint validates the token's signature; the portal then
   checks expiry and, for browser tokens, the audience. Two token kinds are accepted:

   <table>
     <thead>
       <tr>
         <th>Caller</th>
         <th>Token</th>
         <th>Audience requirement</th>
       </tr>
     </thead>
     <tbody>
       <tr>
         <td>Browser</td>
         <td>OpenID Connect <strong>ID token</strong> from the Google Identity Services sign-in</td>
         <td>must equal <code>deploy-info.auth.client-id</code></td>
       </tr>
       <tr>
         <td>Collector</td>
         <td>OAuth <strong>access token</strong> from <code>gcloud auth print-access-token</code></td>
         <td>none</td>
       </tr>
     </tbody>
   </table>

   Verification results are cached per token (5 minutes); IAM results per email (10 minutes for granted
   access, 1 minute for denials — a newly granted user is not locked out for long).

2. **IAM membership check** — the portal runs the **Cloud Asset API's `AnalyzeIamPolicy`** on its own
   project with its own Application Default Credentials: the analysis is restricted to the user and
   expands the group-membership graph, so a user bound to the project only through a Google Group is
   found too (a plain IAM-policy read could not do this). Any non-empty analysis result counts as
   "present in the IAM".

   The portal's project id is resolved from the **`GOOGLE_CLOUD_PROJECT` environment variable**, which
   the App Engine runtime always sets; outside App Engine the instance metadata server is consulted as
   a fallback. When exercising authentication locally, set the variable by hand.

3. **HTTP enforcement** — Different HTTP status and error messages according to the issue detected.
   The full API contract is in [api.md](../../../../documentation/api.md). CORS preflights (`OPTIONS`)
   always pass through.

## IAM prerequisites (portal side)

The portal's service account needs to run the analysis on the portal project:

- `cloudasset.assets.analyzeIamPolicy` on the project — granted by `roles/cloudasset.viewer` (or a
  custom role with that permission);
- the **Cloud Asset API** must be enabled in the portal's project.

If either is missing, users are not told they lack access: the check fails and the HTTP layer answers
503 — the misconfiguration is visible in the portal's logs.

## OAuth client (browser sign-in)

The frontend signs in with Google Identity Services, which needs an **OAuth client id** (web application
type) whose *Authorized JavaScript origins* include the portal's serving URL (and
`http://localhost:4200` for the local dev setup). Set it as `deploy-info.auth.client-id` — the portal
refuses to start with auth enabled and the client id blank.

## Collector

The collector fetches the token with `gcloud auth print-access-token` (the same account that performs
the upload — gcloud is a hard prerequisite anyway) and sends it as the `Authorization: Bearer` header on
both portal calls. When no token can be obtained, the request is sent without the header — a portal with
auth disabled (local development) works unchanged, and a portal with auth enabled rejects the call.

### userEmail match (defense in depth)

In the standard flow, both the trigger request's `userEmail` and the token derive from the same active
gcloud account, so they always match. The portal still compares them
(`deploy-info.auth.collector-require-email-match`, default `true`) so a hand-crafted request cannot use a
valid token of an IAM-permitted account to attribute its upload to another user's deploy identity.
Environments that deploy via gcloud service-account impersonation (`gcloud config set
auth/impersonate_service_account`) produce a token whose email is the service account while `userEmail`
may report the user — set the property to `false` there.

## Configuration

<table>
  <thead>
    <tr>
      <th>Property</th>
      <th>Meaning</th>
      <th>Default</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>deploy-info.auth.enabled</code></td>
      <td>Whether authentication is active</td>
      <td><code>false</code> (<code>true</code> in the prod profile)</td>
    </tr>
    <tr>
      <td><code>deploy-info.auth.client-id</code></td>
      <td>OAuth client id (audience of the browser ID tokens)</td>
      <td>empty (required when authentication is enabled)</td>
    </tr>
    <tr>
      <td><code>deploy-info.auth.collector-require-email-match</code></td>
      <td>Trigger's <code>userEmail</code> must equal the token email</td>
      <td><code>true</code></td>
    </tr>
  </tbody>
</table>
