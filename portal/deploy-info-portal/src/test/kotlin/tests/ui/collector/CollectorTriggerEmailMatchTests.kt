package tests.ui.collector

import application.auth.AuthenticateUserUseCase
import application.auth.AuthenticationIssue
import application.collector.CreateDeployLinkUseCase
import application.collector.GetStorageBucketUseCase
import infrastructure.config.DeployInfoProperties
import infrastructure.security.AuthenticationFilter
import org.mockito.kotlin.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The email-match defense in depth: a valid token presenting a different `userEmail` than its own
 * account must not link the upload (see `DeployInfoProperties.Auth`). The filter supplies the
 * token's email as a request attribute — reproduced here with the real filter in front of the
 * real controller.
 */
class CollectorTriggerEmailMatchTests {

    private val authenticateUserUseCase = mock<AuthenticateUserUseCase>()
    private val createDeployLinkUseCase = mock<CreateDeployLinkUseCase>()

    private fun properties(requireEmailMatch: Boolean, authEnabled: Boolean = true) = DeployInfoProperties(
        storageBucket = "bucket",
        linking = DeployInfoProperties.Linking(window = Duration.ofMinutes(15)),
        cleanup = DeployInfoProperties.Cleanup(enabled = false, interval = Duration.ofMinutes(10), gracePeriod = Duration.ofMinutes(10)),
        cors = DeployInfoProperties.Cors(allowedOrigins = emptyList()),
        auth = DeployInfoProperties.Auth(
            enabled = authEnabled,
            clientId = "client-1",
            collectorRequireEmailMatch = requireEmailMatch,
        ),
    )

    private fun mockMvc(requireEmailMatch: Boolean, authEnabled: Boolean = true): MockMvc {
        val properties = properties(requireEmailMatch, authEnabled)
        val builder = MockMvcBuilders.standaloneSetup(
            ui.collector.CollectorController(createDeployLinkUseCase, GetStorageBucketUseCase(properties), properties),
        )
        builder.addFilters<StandaloneMockMvcBuilder>(AuthenticationFilter(authenticateUserUseCase, properties))
        return builder.build()
    }

    private fun trigger(mvc: MockMvc, userEmail: String, withToken: Boolean = true) =
        mvc.perform(
            post("/collector/handleNewDirectory")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"directoryName":"jdoe_GAE_1735689600000","projects":["proj"],"deployType":"GAE",""" +
                        """"userEmail":"$userEmail"}""",
                )
                .let { if (withToken) it.header("Authorization", "Bearer some-token") else it }
        )

    private fun permit(email: String) {
        whenever(authenticateUserUseCase.authenticate(any(), any()))
            .thenReturn(AuthenticateUserUseCase.AuthenticatedUser(email, AuthenticationIssue.NONE))
        whenever(createDeployLinkUseCase.execute(any())).thenReturn(CreateDeployLinkUseCase.Output.NotLinked)
    }

    @Test
    fun `A matching userEmail (case-insensitive) links the upload`() {
        permit("Collector@Example.com")

        val result = trigger(mockMvc(requireEmailMatch = true), "collector@example.com").andReturn()

        assertEquals(202, result.response.status)
        assertTrue(result.response.contentAsString.contains("\"status\":\"pending\""))
    }

    @Test
    fun `A mismatched userEmail is rejected without linking`() {
        permit("other@example.com")

        val result = trigger(mockMvc(requireEmailMatch = true), "victim@example.com").andReturn()

        assertEquals(403, result.response.status)
        assertEquals(
            """{"error":"email-mismatch","tokenEmail":"other@example.com","requestUserEmail":"victim@example.com"}""",
            result.response.contentAsString,
        )
        verify(createDeployLinkUseCase, never()).execute(any())
    }

    @Test
    fun `With the check disabled (impersonation setups) a mismatched userEmail is accepted`() {
        permit("other@example.com")

        val result = trigger(mockMvc(requireEmailMatch = false), "victim@example.com").andReturn()

        assertEquals(202, result.response.status)
    }

    @Test
    fun `Without authentication enabled there is nothing to compare against (local and test)`() {
        permit("other@example.com")

        val result = trigger(mockMvc(requireEmailMatch = true, authEnabled = false), "whoever@example.com", withToken = false)
            .andReturn()

        assertEquals(202, result.response.status)
        assertTrue(result.response.contentAsString.contains("pending"))
    }
}
