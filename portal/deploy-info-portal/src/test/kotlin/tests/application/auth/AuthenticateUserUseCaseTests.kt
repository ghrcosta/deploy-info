package tests.application.auth

import application.auth.*
import application.auth.AuthenticateUserUseCase.TokenKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AuthenticateUserUseCaseTests {

    private class Ports(
        val idTokenVerified: Boolean = true,
        val accessTokenVerified: Boolean = true,
        val issue: AuthenticationIssue = AuthenticationIssue.NONE,
    ) {
        var checkCalls = 0

        val idTokenVerifier = IdTokenVerifier { token ->
            (if (token == "id-token" && idTokenVerified) VerifiedUser("id@example.com") else null)
        }
        val accessTokenVerifier = AccessTokenVerifier { token ->
            (if (token == "access-token" && accessTokenVerified) VerifiedUser("collector@example.com") else null)
        }
        val userPermissionChecker = UserPermissionChecker { checkCalls++; issue }
    }

    private fun useCase(ports: Ports) =
        AuthenticateUserUseCase(ports.idTokenVerifier, ports.accessTokenVerifier, ports.userPermissionChecker)

    @Test
    fun `Authenticates a browser ID token of a permitted user`() {
        val ports = Ports()

        val user = useCase(ports).authenticate("id-token", TokenKind.BROWSER_ID_TOKEN)

        assertEquals("id@example.com", user.email)
        assertEquals(AuthenticationIssue.NONE, user.issue)
        assertEquals(1, ports.checkCalls)
    }

    @Test
    fun `Authenticates a collector access token of a permitted user`() {
        val ports = Ports()

        val user = useCase(ports).authenticate("access-token", TokenKind.COLLECTOR_ACCESS_TOKEN)

        assertEquals("collector@example.com", user.email)
        assertEquals(AuthenticationIssue.NONE, user.issue)
    }

    @Test
    fun `An invalid token is reported without running the permission check`() {
        val ports = Ports(idTokenVerified = false)

        val user = useCase(ports).authenticate("some-other-token", TokenKind.BROWSER_ID_TOKEN)

        assertEquals("", user.email)
        assertEquals(AuthenticationIssue.INVALID_TOKEN, user.issue)
        assertEquals(0, ports.checkCalls)
    }

    @Test
    fun `A token of an unpermitted user is reported with the checker's issue`() {
        val ports = Ports(issue = AuthenticationIssue.NOT_PERMITTED)

        val user = useCase(ports).authenticate("id-token", TokenKind.BROWSER_ID_TOKEN)

        assertEquals("id@example.com", user.email)
        assertEquals(AuthenticationIssue.NOT_PERMITTED, user.issue)
    }

    @Test
    fun `A portal-side check failure is propagated`() {
        val ports = Ports(issue = AuthenticationIssue.PORTAL_IAM_CONFIG)

        val user = useCase(ports).authenticate("id-token", TokenKind.BROWSER_ID_TOKEN)

        assertEquals(AuthenticationIssue.PORTAL_IAM_CONFIG, user.issue)
    }
}
