package tests.infrastructure.gcp

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ImpersonatedCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import infrastructure.gcp.GcpPortalIdentityResolver
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests of the portal's own service-account identity resolution — hermetic: the metadata
 * server fetch is injected, so no network is ever touched.
 */
class GcpPortalIdentityResolverTests {

    private val portalServiceAccount = "portal@portal-proj.iam.gserviceaccount.com"

    private fun resolver(
        credentials: GoogleCredentials,
        metadataEmail: () -> String? = { throw IllegalStateException("metadata must not be queried") },
    ) = GcpPortalIdentityResolver(callerCredentials = { credentials }, metadataEmailFetcher = metadataEmail)

    @Test
    fun `Return the client email of service-account credentials`() {
        val credentials = mock<ServiceAccountCredentials> {
            on { account } doReturn portalServiceAccount
        }

        assertEquals(portalServiceAccount, resolver(credentials).resolve())
    }

    @Test
    fun `Return the impersonated principal of impersonated credentials`() {
        val credentials = mock<ImpersonatedCredentials> {
            on { account } doReturn portalServiceAccount
        }

        assertEquals(portalServiceAccount, resolver(credentials).resolve())
    }

    @Test
    fun `Fall back to the metadata server for other credential types`() {
        val credentials = GoogleCredentials.create(AccessToken("token", Date(Long.MAX_VALUE)))

        assertEquals(
            portalServiceAccount,
            resolver(credentials, metadataEmail = { portalServiceAccount }).resolve(),
        )
    }

    @Test
    fun `Ignore a malformed metadata response`() {
        val credentials = GoogleCredentials.create(AccessToken("token", Date(Long.MAX_VALUE)))

        assertNull(resolver(credentials, metadataEmail = { "not-an-email" }).resolve())
    }

    @Test
    fun `Report no identity when a metadata lookup fails`() {
        val credentials = GoogleCredentials.create(AccessToken("token", Date(Long.MAX_VALUE)))

        assertNull(resolver(credentials, metadataEmail = { null }).resolve())
    }

    @Test
    fun `Report no identity when the credentials cannot be obtained`() {
        val resolver = GcpPortalIdentityResolver(callerCredentials = { throw IllegalStateException("no ADC") })

        assertNull(resolver.resolve())
    }
}
