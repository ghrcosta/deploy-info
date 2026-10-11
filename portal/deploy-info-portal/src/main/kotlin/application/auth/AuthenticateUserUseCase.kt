package application.auth

/**
 * Authenticates a caller presenting a Google token: verifies the token itself, then checks the
 * user's IAM membership in the portal's project. Pure orchestration — token verification and the
 * membership check (including their caches) live in the infrastructure implementations behind the
 * ports.
 */
class AuthenticateUserUseCase(
    private val idTokenVerifier: IdTokenVerifier,
    private val accessTokenVerifier: AccessTokenVerifier,
    private val userPermissionChecker: UserPermissionChecker,
) {

    /** How the token reached the portal: the browser signs in, the collector uses gcloud. */
    enum class TokenKind { BROWSER_ID_TOKEN, COLLECTOR_ACCESS_TOKEN }

    /** [email] is only meaningful when the issue is [AuthenticationIssue.NONE] or NOT_PERMITTED. */
    data class AuthenticatedUser(val email: String, val issue: AuthenticationIssue)

    fun authenticate(token: String, kind: TokenKind): AuthenticatedUser {
        val verified = when (kind) {
            TokenKind.BROWSER_ID_TOKEN -> idTokenVerifier.verify(token)
            TokenKind.COLLECTOR_ACCESS_TOKEN -> accessTokenVerifier.verify(token)
        } ?: return AuthenticatedUser(email = "", issue = AuthenticationIssue.INVALID_TOKEN)
        return AuthenticatedUser(verified.email, userPermissionChecker.check(verified.email))
    }
}
