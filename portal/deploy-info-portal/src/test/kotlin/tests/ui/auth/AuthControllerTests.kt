package tests.ui.auth

import infrastructure.config.DeployInfoProperties
import infrastructure.security.AuthenticationFilter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthControllerTests {

    private fun properties(enabled: Boolean, clientId: String = "client-1") = DeployInfoProperties(
        storageBucket = "bucket",
        linking = DeployInfoProperties.Linking(window = Duration.ofMinutes(15)),
        cleanup = DeployInfoProperties.Cleanup(enabled = false, interval = Duration.ofMinutes(10), gracePeriod = Duration.ofMinutes(10)),
        cors = DeployInfoProperties.Cors(allowedOrigins = emptyList()),
        auth = DeployInfoProperties.Auth(enabled = enabled, clientId = clientId, collectorRequireEmailMatch = true),
    )

    private fun mockMvc(enabled: Boolean, clientId: String = "client-1"): MockMvc =
        MockMvcBuilders.standaloneSetup(ui.auth.AuthController(properties(enabled, clientId))).build()

    @Test
    fun `Reports the sign-in configuration (auth disabled)`() {
        val result = mockMvc(enabled = false).perform(get("/auth/config")).andReturn()

        assertEquals(200, result.response.status)
        assertEquals("""{"enabled":false,"clientId":"client-1"}""", result.response.contentAsString)
    }

    @Test
    fun `Reports the sign-in configuration (auth enabled)`() {
        val result = mockMvc(enabled = true).perform(get("/auth/config")).andReturn()

        assertEquals("""{"enabled":true,"clientId":"client-1"}""", result.response.contentAsString)
    }

    @Test
    fun `Reports the authenticated user's email from the filter's request attribute`() {
        val result = mockMvc(enabled = true).perform(
            get("/auth/user").requestAttr(AuthenticationFilter.AUTHENTICATED_EMAIL_ATTRIBUTE, "user@example.com")
        ).andReturn()

        assertEquals("""{"email":"user@example.com"}""", result.response.contentAsString)
    }

    @Test
    fun `Without the attribute (auth disabled) the user endpoint reports an empty email`() {
        val result = mockMvc(enabled = false).perform(get("/auth/user")).andReturn()

        assertEquals("""{"email":""}""", result.response.contentAsString)
    }
}
