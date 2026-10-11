package infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Binds the `deploy-info.*` configuration properties used by the linking logic (bucket name of the
 * collector uploads, matching window), the validity/cleanup logic and CORS.
 */
@ConfigurationProperties("deploy-info")
data class DeployInfoProperties(
    val storageBucket: String,
    val linking: Linking,
    val cleanup: Cleanup,
    val cors: Cors,
    val auth: Auth,
) {

    data class Linking(
        /** How far back from the collect timestamp a deploy may still be matched. */
        val window: Duration,
    )

    data class Cleanup(
        /** Whether the on-request cleanup trigger is active (`deploy-info.cleanup.enabled`). */
        val enabled: Boolean,

        /** How long after a completed sweep the on-request trigger may start the next one. */
        val interval: Duration,

        /**
         * How old a deploy link must be before the sweep may judge it stale — protects a link that
         * was created after the sweep's listing snapshot was taken (its deploy is of course live,
         * but it is not in the snapshot) from being deleted by that same sweep.
         */
        val gracePeriod: Duration,
    )

    /**
     * CORS origins (`deploy-info.cors.allowed-origins`) allowed to call the API cross-origin — only
     * relevant when the UI runs on a different origin than the backend (local dev: 4200 → 8080).
     * An empty list disables CORS processing entirely, which is what the same-origin production
     * serving (UI built into the same jar) needs: browsers send an `Origin` header even on
     * same-origin non-GET requests, and any request carrying an unlisted origin would be rejected.
     */
    data class Cors(
        val allowedOrigins: List<String>,
    )

    /**
     * Authentication (`deploy-info.auth.*`): requests to the API must present a Google token of a
     * user who is present in the IAM of the project the portal runs in — directly or through a
     * group (group membership is expanded with the Cloud Asset API's `AnalyzeIamPolicy`). See
     * `portal/deploy-info-portal/documentation/authentication.md`.
     */
    data class Auth(
        /** Whether the authentication filter is active (`deploy-info.auth.enabled`). */
        val enabled: Boolean,

        /**
         * The OAuth client id the frontend's Google Identity Services sign-in issues ID tokens for
         * (`deploy-info.auth.client-id`) — the audience the portal verifies on those tokens. Only
         * meaningful when [enabled]; the portal refuses to start with auth enabled and this blank.
         */
        val clientId: String,

        /**
         * Whether the collector trigger's `userEmail` must equal the email of the access token it
         * presents (`deploy-info.auth.collector-require-email-match`). In the standard flow both
         * derive from the same active gcloud account, so they always match and the check never
         * fires for a real collector run — its only purpose is defense in depth against
         * hand-crafted requests: a client presenting a valid token of an IAM-permitted account A
         * while claiming a different `userEmail` could otherwise have its upload folder linked to
         * another user's deploy identity (hijacking the link and misattributing the collected
         * files). The check costs nothing — the token email is already returned by the tokeninfo
         * verification. It is configurable because gcloud service-account impersonation setups
         * (`gcloud config set auth/impersonate_service_account`) produce a token whose email is
         * the service account while `userEmail` may report the user, which would false-positive
         * on every deploy — such environments should set this to false.
         */
        val collectorRequireEmailMatch: Boolean,
    )
}
