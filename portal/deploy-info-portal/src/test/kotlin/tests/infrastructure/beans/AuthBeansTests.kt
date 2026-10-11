package tests.infrastructure.beans

import infrastructure.beans.AuthBeans
import infrastructure.config.DeployInfoProperties
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AuthBeansTests {

    private fun properties(enabled: Boolean, clientId: String) = DeployInfoProperties(
        storageBucket = "bucket",
        linking = DeployInfoProperties.Linking(window = Duration.ofMinutes(15)),
        cleanup = DeployInfoProperties.Cleanup(enabled = false, interval = Duration.ofMinutes(10), gracePeriod = Duration.ofMinutes(10)),
        cors = DeployInfoProperties.Cors(allowedOrigins = emptyList()),
        auth = DeployInfoProperties.Auth(enabled = enabled, clientId = clientId, collectorRequireEmailMatch = true),
    )

    @Test
    fun `Refuses to start with auth enabled and no client id`() {
        assertFailsWith<IllegalStateException> { AuthBeans(properties(enabled = true, clientId = "")) }
    }

    @Test
    fun `Starts with auth enabled and a client id`() {
        AuthBeans(properties(enabled = true, clientId = "client-1"))
    }

    @Test
    fun `Starts with auth disabled and no client id (local and test profiles)`() {
        AuthBeans(properties(enabled = false, clientId = ""))
    }
}
