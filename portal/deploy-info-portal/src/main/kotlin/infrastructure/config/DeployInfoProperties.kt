package infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Binds the `deploy-info.*` configuration properties used by the linking logic (bucket name of the
 * collector uploads, matching window).
 */
@ConfigurationProperties("deploy-info")
data class DeployInfoProperties(
    val storageBucket: String,
    val linking: Linking,
) {

    data class Linking(
        /** How far back from the collect timestamp a deploy may still be matched. */
        val window: Duration,
    )
}
