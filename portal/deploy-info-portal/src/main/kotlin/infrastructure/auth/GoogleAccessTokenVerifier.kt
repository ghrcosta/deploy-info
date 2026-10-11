package infrastructure.auth

import application.auth.AccessTokenVerifier
import application.auth.VerifiedUser
import org.slf4j.LoggerFactory

/**
 * Verifies the collector's gcloud access token (`gcloud auth print-access-token`): access tokens
 * are opaque, so validation goes through Google's tokeninfo endpoint, which requires a future
 * expiry and reports the owning account's email.
 */
class GoogleAccessTokenVerifier(
    private val tokenInfoClient: GoogleTokenInfoClient,
    /** Overridable for tests; null uses the system clock. */
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : AccessTokenVerifier {

    override fun verify(accessToken: String): VerifiedUser? {
        val info = tokenInfoClient.fetch(accessToken, TokenInfoKind.ACCESS_TOKEN) ?: return null
        val email = info.email?.takeIf { info.emailVerified == true }
        if (email == null) {
            logger.debug("Rejected an access token: no verified email")
            return null
        }
        if ((info.expiresAt ?: 0) <= nowSeconds()) {
            logger.debug("Rejected an access token for {}: expired", email)
            return null
        }
        return VerifiedUser(email)
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(GoogleAccessTokenVerifier::class.java)
    }
}
