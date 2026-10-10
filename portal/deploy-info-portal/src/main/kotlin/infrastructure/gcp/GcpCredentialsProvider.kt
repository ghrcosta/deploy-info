package infrastructure.gcp

import com.google.auth.http.HttpTransportFactory
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ImpersonatedCredentials
import domain.CredentialsErrorCategory
import domain.CredentialsException
import domain.GcpListingException
import domain.Project
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides credentials for a project's service account through GCP impersonation: the portal's own
 * identity — supplied as Application Default Credentials — impersonates the target service account
 * via the IAM Credentials API, which requires the
 * `roles/iam.serviceAccountTokenCreator` permission on that account.
 *
 * The returned credentials have the `cloud-platform` scope, so they can be used to call the listing
 * APIs (App Engine Admin, Cloud Run Admin) on behalf of the impersonated service account.
 *
 * One [ImpersonatedCredentials] instance is kept per service account; token caching and
 * refresh-on-expiry are handled by the Google auth library itself.
 *
 * Failures are wrapped in [domain.CredentialsException] with a category telling whether the user
 * may be able to fix the problem (service account misconfiguration) or it is a portal-side issue.
 */
class GcpCredentialsProvider(
    /** Supplies the portal's own credentials used to authenticate the impersonation. */
    private val callerCredentials: () -> GoogleCredentials,
    /** Overridable for tests; null uses the library default (real network). */
    private val transportFactory: HttpTransportFactory? = null,
) : CredentialsProvider {

    private val credentialsByServiceAccount = ConcurrentHashMap<String, ImpersonatedCredentials>()

    override fun credentialsFor(project: Project): GoogleCredentials {
        val credentials = credentialsByServiceAccount.computeIfAbsent(project.serviceAccount) {
            impersonatedCredentials(project.serviceAccount)
        }
        return try {
            credentials.refreshIfExpired()
            // Never log the tokens themselves — only that a fresh token is ready for the account.
            logger.debug("Impersonated access token for ${project.serviceAccount} is ready (refreshed if expired)")
            credentials
        } catch (e: CredentialsException) {
            logger.warn(
                "Could not obtain credentials for ${project.serviceAccount}: category=${e.category}, " +
                    "CredentialsException causeChain=\"${GcpListingException.causeChainMessage(e)}\"",
            )
            throw e
        } catch (e: Exception) {
            val category = categorize(e)
            logger.warn(
                "Could not obtain credentials for ${project.serviceAccount}: category=$category, " +
                    "Exception causeChain=\"${GcpListingException.causeChainMessage(e)}\"",
            )
            throw CredentialsException(
                "Could not obtain credentials for ${project.serviceAccount}",
                category,
                e,
            )
        }
    }

    private fun impersonatedCredentials(serviceAccount: String): ImpersonatedCredentials = try {
        val builder = ImpersonatedCredentials.newBuilder()
            .setSourceCredentials(callerCredentials())
            .setTargetPrincipal(serviceAccount)
            .setScopes(listOf(CLOUD_PLATFORM_SCOPE))
            .setLifetime(TOKEN_LIFETIME_SECONDS)
        transportFactory?.let { builder.setHttpTransportFactory(it) }
        builder.build()
    } catch (e: Exception) {
        throw CredentialsException(
            "Could not obtain the portal's own credentials (Application Default Credentials)",
            CredentialsErrorCategory.PORTAL_ISSUE,
            e,
        )
    }

    /**
     * Maps an underlying failure to the category that tells who can act on it.
     *
     * The auth library surfaces the IAM API error text (including the status code) in the exception
     * message, so permission problems are recognized by their message — the library does not expose
     * typed exceptions per HTTP status. Everything else is treated as a portal-side issue.
     */
    private fun categorize(e: Exception): CredentialsErrorCategory {
        val messages = generateSequence(e as Throwable?) { it.cause }.joinToString(" ") { it.message ?: "" }
        val message = messages.lowercase()
        return if (PERMISSION_DENIED_MARKERS.any { it in message }) {
            CredentialsErrorCategory.SERVICE_ACCOUNT_MISCONFIGURATION
        } else {
            CredentialsErrorCategory.PORTAL_ISSUE
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GcpCredentialsProvider::class.java)

        private const val CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
        private const val TOKEN_LIFETIME_SECONDS = 3600

        /** Message fragments that indicate a user-fixable service account permission problem. */
        private val PERMISSION_DENIED_MARKERS = listOf("403", "permission denied", "forbidden")
    }
}