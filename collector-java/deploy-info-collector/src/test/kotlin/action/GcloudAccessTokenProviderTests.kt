package action

import io.github.ghrcosta.action.GcloudAccessTokenProvider
import io.github.ghrcosta.util.Logger
import io.mockk.mockk
import org.gradle.api.logging.Logger as GradleLogger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GcloudAccessTokenProviderTests {

    @BeforeTest
    fun setup() {
        Logger.init(mockk<GradleLogger>(relaxed = true))
    }

    @Test
    fun `Returns the trimmed stdout of the gcloud token command`() {
        val provider = GcloudAccessTokenProvider(printAccessToken = { "  token-1  " })

        assertEquals("token-1", provider.fetch())
    }

    @Test
    fun `Returns null when the command outputs nothing`() {
        val provider = GcloudAccessTokenProvider(printAccessToken = { "   " })

        assertNull(provider.fetch())
    }

    @Test
    fun `Returns null when the gcloud command fails (the portal answers with its own error then)`() {
        val provider = GcloudAccessTokenProvider(printAccessToken = { throw RuntimeException("not logged in") })

        assertNull(provider.fetch())
    }
}
