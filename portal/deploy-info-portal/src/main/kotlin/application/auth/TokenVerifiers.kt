package application.auth

/** A Google token the portal could verify: the email of the account it was issued for. */
data class VerifiedUser(val email: String)

/**
 * Verifies a Google-issued OpenID Connect ID token — the credential the frontend's Google Identity
 * Services sign-in produces. Verification covers the signature (checked by Google's tokeninfo
 * endpoint, see `infrastructure/auth/GoogleTokenInfoClient`), the audience (this portal's OAuth
 * client id) and the expiry.
 */
fun interface IdTokenVerifier {

    /** Returns the verified user, or null when the token is invalid, expired or for another audience. */
    fun verify(idToken: String): VerifiedUser?
}

/**
 * Verifies a Google-issued OAuth access token — the credential the collector obtains with
 * `gcloud auth print-access-token`. Access tokens are opaque, so verification goes through
 * Google's tokeninfo endpoint (see `infrastructure/auth/GoogleTokenInfoClient`).
 */
fun interface AccessTokenVerifier {

    /** Returns the verified user, or null when the token is invalid or expired. */
    fun verify(accessToken: String): VerifiedUser?
}
