package infrastructure.auth

import application.auth.IdTokenVerifier
import application.auth.VerifiedUser
import org.slf4j.LoggerFactory

/**
 * Verifies the frontend's Google Identity Services ID token: Google's tokeninfo endpoint validates
 * the token's signature, and this class additionally requires the token's audience (the OAuth client
 * id configured for this portal), a future expiry and a verified email.
 */
class GoogleIdTokenVerifier(
    private val tokenInfoClient: GoogleTokenInfoClient,
    private val audience: String,
    /** Overridable for tests; null uses the system clock. */
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : IdTokenVerifier {

    override fun verify(idToken: String): VerifiedUser? {
        val info = tokenInfoClient.fetch(idToken, TokenInfoKind.ID_TOKEN) ?: return null
        val email = info.email?.takeIf { info.emailVerified == true }
        if (email == null) {
            logger.debug("Rejected an ID token: no verified email")
            return null
        }
        if (info.audience != audience) {
            logger.debug("Rejected an ID token for ${email}: audience ${info.audience} is not this portal's client id")
            return null
        }
        if ((info.expiresAt ?: 0) <= nowSeconds()) {
            logger.debug("Rejected an ID token for {}: expired", email)
            return null
        }
        return VerifiedUser(email)
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(GoogleIdTokenVerifier::class.java)
    }
}
