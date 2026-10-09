package infrastructure.gcp

import application.PortalIdentityResolver
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ImpersonatedCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import java.net.HttpURLConnection
import java.net.URI

/**
 * Resolves the portal's own service account email from its Application Default Credentials, so the
 * UI can name the principal to grant the IAM roles to in its remediation instructions.
 *
 * Covers the deployment shapes the portal runs in:
 * - a service-account key file behind ADC → the credentials' client email;
 * - credentials that themselves impersonate a service account → the impersonated principal;
 * - a GCE / App Engine / Cloud Run workload → the email reported by the instance metadata server.
 *
 * Returns null when the identity is not a service account (e.g. gcloud user credentials during
 * local development) or cannot be determined — the UI then shows a placeholder instead.
 */
class GcpPortalIdentityResolver(
    private val callerCredentials: () -> GoogleCredentials,
    /** Overridable for tests; null fetches the metadata server of the GCP instance. */
    private val metadataEmailFetcher: (() -> String?)? = null,
) : PortalIdentityResolver {

    override fun resolve(): String? =
        try {
            when (val credentials = callerCredentials()) {
                is ServiceAccountCredentials -> credentials.account
                is ImpersonatedCredentials -> credentials.account
                else -> (metadataEmailFetcher ?: ::metadataServerEmail)()
                    ?.takeIf { EMAIL_PATTERN.matches(it) }
            }
        } catch (_: Exception) {
            null
        }

    private fun metadataServerEmail(): String? =
        try {
            val connection = URI.create(METADATA_EMAIL_URL).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = METADATA_TIMEOUT_MILLIS
                connection.readTimeout = METADATA_TIMEOUT_MILLIS
                connection.setRequestProperty("Metadata-Flavor", "Google")
                val body = connection.inputStream.use { it.readBytes().decodeToString() }.trim()
                body.takeIf { connection.responseCode == 200 && EMAIL_PATTERN.matches(it) }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }

    companion object {
        private const val METADATA_EMAIL_URL =
            "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/email"
        private const val METADATA_TIMEOUT_MILLIS = 2000

        private val EMAIL_PATTERN = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
    }
}
