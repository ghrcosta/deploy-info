package tests.infrastructure.security

import application.auth.AuthenticateUserUseCase
import application.auth.AuthenticationIssue
import application.collector.CreateDeploymentLinkUseCase
import application.collector.GetStorageBucketUseCase
import infrastructure.config.DeployInfoProperties
import infrastructure.security.AuthenticationFilter
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exercises the filter behavior end to end on a standalone MockMvc (filter + real
 * `GET /collector/bucket` handler): the token → use case → status/body mapping, and the
 * pass-throughs. The Spring wiring (URL patterns) is proven by
 * `AuthenticationFilterRegistrationTests`.
 */
class AuthenticationFilterTests {

    private val authenticateUserUseCase = mock<AuthenticateUserUseCase>()
    private val createDeploymentLinkUseCase = mock<CreateDeploymentLinkUseCase>()

    private fun properties(authEnabled: Boolean) = DeployInfoProperties(
        storageBucket = "bucket",
        linking = DeployInfoProperties.Linking(window = Duration.ofMinutes(15)),
        cleanup = DeployInfoProperties.Cleanup(enabled = false, interval = Duration.ofMinutes(10), gracePeriod = Duration.ofMinutes(10)),
        cors = DeployInfoProperties.Cors(allowedOrigins = emptyList()),
        auth = DeployInfoProperties.Auth(enabled = authEnabled, clientId = "client-1", collectorRequireEmailMatch = true),
    )

    private fun mockMvc(authEnabled: Boolean): MockMvc {
        val properties = properties(authEnabled)
        val builder = MockMvcBuilders.standaloneSetup(
            ui.collector.CollectorController(createDeploymentLinkUseCase, GetStorageBucketUseCase(properties), properties),
        )
        builder.addFilters<StandaloneMockMvcBuilder>(AuthenticationFilter(authenticateUserUseCase, properties))
        return builder.build()
    }

    private fun bucketRequest() = get("/collector/bucket")

    @Test
    fun `Passes through when authentication is disabled (local and test profiles)`() {
        val result = mockMvc(authEnabled = false).perform(bucketRequest()).andReturn()

        assertEquals(200, result.response.status)
        assertEquals("""{"bucket":"bucket"}""", result.response.contentAsString)
    }

    @Test
    fun `Rejects a request without a bearer token`() {
        val result = mockMvc(authEnabled = true).perform(bucketRequest()).andReturn()

        assertEquals(401, result.response.status)
        assertEquals("""{"error":"unauthenticated"}""", result.response.contentAsString)
    }

    @Test
    fun `Rejects a request with a non-bearer header`() {
        val result = mockMvc(authEnabled = true).perform(
            bucketRequest().header("Authorization", "Basic dXNlcjpwYXNz")
        ).andReturn()

        assertEquals(401, result.response.status)
    }

    @Test
    fun `Rejects an invalid token`() {
        whenever(authenticateUserUseCase.authenticate(any(), any()))
            .thenReturn(AuthenticateUserUseCase.AuthenticatedUser("", AuthenticationIssue.INVALID_TOKEN))

        val result = mockMvc(authEnabled = true).perform(
            bucketRequest().header("Authorization", "Bearer invalid")
        ).andReturn()

        assertEquals(401, result.response.status)
        assertEquals("""{"error":"unauthenticated"}""", result.response.contentAsString)
    }
    @Test
    fun `Rejects a token of an unpermitted user with the user's email in the body`() {
        whenever(authenticateUserUseCase.authenticate(any(), any()))
            .thenReturn(AuthenticateUserUseCase.AuthenticatedUser("user@example.com", AuthenticationIssue.NOT_PERMITTED))

        val result = mockMvc(authEnabled = true).perform(
            bucketRequest().header("Authorization", "Bearer some-token")
        ).andReturn()

        assertEquals(403, result.response.status)
        assertEquals("""{"error":"not-permitted","email":"user@example.com"}""", result.response.contentAsString)
    }

    @Test
    fun `Reports a failing IAM membership check as a portal issue`() {
        whenever(authenticateUserUseCase.authenticate(any(), any()))
            .thenReturn(AuthenticateUserUseCase.AuthenticatedUser("", AuthenticationIssue.PORTAL_ISSUE))

        val result = mockMvc(authEnabled = true).perform(
            bucketRequest().header("Authorization", "Bearer some-token")
        ).andReturn()

        assertEquals(503, result.response.status)
        assertEquals("""{"error":"portal-issue"}""", result.response.contentAsString)
    }

    @Test
    fun `Lets an authenticated request through to the handler`() {
        whenever(authenticateUserUseCase.authenticate(any(), any()))
            .thenReturn(AuthenticateUserUseCase.AuthenticatedUser("user@example.com", AuthenticationIssue.NONE))

        val result = mockMvc(authEnabled = true).perform(
            bucketRequest().header("Authorization", "Bearer some-token")
        ).andReturn()

        assertEquals(200, result.response.status)
        assertEquals("""{"bucket":"bucket"}""", result.response.contentAsString)
    }

    @Test
    fun `Lets CORS preflights through (browsers send no credentials on preflights)`() {
        val result = mockMvc(authEnabled = true).perform(options("/collector/bucket")).andReturn()

        assertEquals(200, result.response.status)
    }
}
