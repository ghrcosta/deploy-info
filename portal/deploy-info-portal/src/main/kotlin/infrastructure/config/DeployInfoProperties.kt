package infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Binds the `deploy-info.*` configuration properties used by the linking logic (bucket name of the
 * collector uploads, matching window) and the validity/cleanup logic.
 */
@ConfigurationProperties("deploy-info")
data class DeployInfoProperties(
    val storageBucket: String,
    val linking: Linking,
    val cleanup: Cleanup,
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
}
