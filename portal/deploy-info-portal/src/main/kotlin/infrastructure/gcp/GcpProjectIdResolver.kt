package infrastructure.gcp

import java.net.HttpURLConnection
import java.net.URI

/**
 * Resolves the id of the project the portal itself runs in — the project whose IAM decides who may
 * use the portal. The App Engine runtime always sets the `GOOGLE_CLOUD_PROJECT` environment
 * variable (the same resolution the Cloud Logging stack relies on, see
 * `documentation/logging.md`); when it is absent — outside the App Engine runtime — the instance
 * metadata server is consulted as a fallback.
 *
 * Returns null when neither source answers, so callers can degrade to their portal-issue handling
 * instead of failing at startup: like every GCP interaction in the portal, the resolution is lazy
 * and only happens when a check actually needs it.
 */
class GcpProjectIdResolver(
    private val environmentProjectId: () -> String? = { System.getenv(ENVIRONMENT_VARIABLE) },
    /** Overridable for tests; null fetches the metadata server of the GCP instance. */
    private val metadataProjectFetcher: (() -> String?)? = null,
) {

    /** Returns the project id, or null when it cannot be determined. */
    fun resolve(): String? =
        try {
            environmentProjectId()?.takeIf { it.isNotBlank() }
                ?: (metadataProjectFetcher ?: ::metadataServerProject)()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

    private fun metadataServerProject(): String? =
        try {
            val connection = URI.create(METADATA_PROJECT_URL).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = METADATA_TIMEOUT_MILLIS
                connection.readTimeout = METADATA_TIMEOUT_MILLIS
                connection.setRequestProperty("Metadata-Flavor", "Google")
                val body = connection.inputStream.use { it.readBytes().decodeToString() }.trim()
                body.takeIf { connection.responseCode == 200 && PROJECT_ID_PATTERN.matches(it) }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }

    companion object {
        /** Set by the App Engine runtime (and settable by hand when exercising auth locally). */
        const val ENVIRONMENT_VARIABLE = "GOOGLE_CLOUD_PROJECT"

        private const val METADATA_PROJECT_URL =
            "http://metadata.google.internal/computeMetadata/v1/project/project-id"
        private const val METADATA_TIMEOUT_MILLIS = 2000
        private val PROJECT_ID_PATTERN = Regex("[a-z][a-z0-9-]{4,28}[a-z0-9]")
    }
}
