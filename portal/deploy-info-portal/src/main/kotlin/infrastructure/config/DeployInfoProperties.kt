package infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

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
        /** Whether the on-request cleanup trigger is active. */
        val enabled: Boolean,

        /** How long after a completed sweep the on-request trigger may start the next one. */
        val interval: Duration,

        /**
         * How old a deploy link must be before the sweep may judge it stale — protects a link that
         * was created after the sweep's listing snapshot was taken from being deleted by that same
         * sweep.
         */
        val gracePeriod: Duration,
    )

    /**
     * CORS origins allowed to call the API cross-origin — only relevant when the UI runs on a
     * different origin than the backend (local dev: 4200 → 8080). An empty list disables CORS
     * processing entirely, which is what the same-origin production serving (UI built into the same
     * jar) needs.
     */
    data class Cors(
        val allowedOrigins: List<String>,
    )

    data class Auth(
        /** Whether the authentication filter is active. */
        val enabled: Boolean,

        /** Audience the portal uses to verify Google tokens when authentication is enabled. */
        val clientId: String,

        /**
         * Whether the collector trigger's `userEmail` must equal the email of the access token it
         * presents. This is only to support a corner case where gcloud is configured to use
         * impersonation, in which case the `userEmail` would be from one account and the token
         * from another. See: documentation/backend/authentication.md.
         */
        val collectorRequireEmailMatch: Boolean,
    )
}
