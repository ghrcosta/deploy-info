package tests.infrastructure.security

import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import infrastructure.DeployInfoPortalApplication
import infrastructure.security.AuthenticationFilter
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Proves the [infrastructure.security.SecurityConfiguration] registration: the filter is registered
 * on the exact protected prefixes (servlet wildcard convention). MockMvc does not apply registered
 * servlet filters, so the on/off behavior itself is covered by the standalone
 * `AuthenticationFilterTests`; this class proves the real context wires the filter in.
 */
@SpringBootTest(
    classes = [DeployInfoPortalApplication::class],
    properties = ["deploy-info.auth.enabled=true", "deploy-info.auth.client-id=test-client"],
)
@ActiveProfiles("test")
class AuthenticationFilterRegistrationTests {

    @Autowired(required = false)
    private var registration: FilterRegistrationBean<AuthenticationFilter>? = null

    @MockitoBean
    private lateinit var datastoreTemplate: DatastoreTemplate

    @Test
    fun `The authentication filter is registered in the real context`() {
        val filterRegistration = registration
        assertNotNull(filterRegistration)
        assertTrue(filterRegistration.isEnabled)
    }

    @Test
    fun `The filter covers the protected prefixes with servlet wildcards`() {
        assertEquals(
            setOf("/portal/*", "/project", "/project/*", "/collector/*", "/auth/user"),
            registration?.urlPatterns?.toSet(),
        )
    }
}
