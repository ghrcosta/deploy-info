package tests.infrastructure.gcp

import infrastructure.gcp.GcpProjectIdResolver
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GcpProjectIdResolverTests {

    private fun resolver(
        environmentProjectId: String? = null,
        metadataProjectId: String? = null,
    ) = GcpProjectIdResolver(
        environmentProjectId = { environmentProjectId },
        metadataProjectFetcher = { metadataProjectId },
    )

    @Test
    fun `Resolves the project id from the environment`() {
        assertEquals("portal-proj", resolver(environmentProjectId = "portal-proj").resolve())
    }

    @Test
    fun `Falls back to the metadata server when the environment variable is unset`() {
        assertEquals("portal-proj", resolver(metadataProjectId = "portal-proj").resolve())
    }

    @Test
    fun `A blank environment variable is ignored in favor of the metadata server`() {
        assertEquals("portal-proj", resolver(environmentProjectId = "  ", metadataProjectId = "portal-proj").resolve())
    }

    @Test
    fun `Returns null when neither source answers`() {
        assertNull(resolver().resolve())
    }

    @Test
    fun `A metadata-server failure is swallowed`() {
        val resolver = GcpProjectIdResolver(
            environmentProjectId = { null },
            metadataProjectFetcher = { throw IllegalStateException("no metadata outside GCP") },
        )
        assertNull(resolver.resolve())
    }

    @Test
    fun `Prefers the environment variable, which the App Engine runtime always sets`() {
        assertEquals(
            "env-proj",
            resolver(environmentProjectId = "env-proj", metadataProjectId = "metadata-proj").resolve(),
        )
    }
}
