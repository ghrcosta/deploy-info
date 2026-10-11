package ui.auth

/** Response body of `GET /auth/config` (see `documentation/api.md`). */
data class AuthConfigDTO(
    /** Whether the portal requires authentication — when false, the frontend skips the sign-in flow. */
    val enabled: Boolean,
    /** The OAuth client id the Google Identity Services sign-in must be initialized with. */
    val clientId: String,
)

/** Response body of `GET /auth/user` (see `documentation/api.md`). */
data class AuthUserDTO(
    /** The email of the account the presented token was issued for. */
    val email: String,
)
