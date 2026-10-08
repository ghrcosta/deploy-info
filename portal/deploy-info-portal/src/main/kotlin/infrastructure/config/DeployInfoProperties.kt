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
}
