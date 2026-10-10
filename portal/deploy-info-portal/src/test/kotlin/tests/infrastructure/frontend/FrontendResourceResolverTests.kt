package tests.infrastructure.frontend

import infrastructure.frontend.FrontendResourceResolver
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.FileSystemResource
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrontendResourceResolverTests {

    @TempDir
    lateinit var staticDirectory: File

    private fun resolver(): FrontendResourceResolver =
        FrontendResourceResolver(listOf("collector", "portal", "settings"))

    private fun location() = FileSystemResource(staticDirectory.absolutePath + "/")

    @Test
    fun `Existing embedded file is served as it is`() {
        File(staticDirectory, "main-abc123.js").writeText("console.log('ui')")
        val resource = resolver().resolve(location(), "main-abc123.js")
        assertEquals("console.log('ui')", resource?.file?.readText())
    }

    @Test
    fun `Unknown root frontend route falls back to the index`() {
        File(staticDirectory, "index.html").writeText("<app-root></app-root>")
        val resource = resolver().resolve(location(), "some-route")
        assertEquals("<app-root></app-root>", resource?.file?.readText())
    }

    @Test
    fun `Unknown nested frontend route falls back to the index`() {
        File(staticDirectory, "index.html").writeText("<app-root></app-root>")
        val resource = resolver().resolve(location(), "some/route")
        assertEquals("<app-root></app-root>", resource?.file?.readText())
    }

    @Test
    fun `Missing asset (path with an extension) is not served the index`() {
        File(staticDirectory, "index.html").writeText("<app-root></app-root>")
        assertNull(resolver().resolve(location(), "missing.png"))
    }

    @Test
    fun `API paths are never served the index`() {
        File(staticDirectory, "index.html").writeText("<app-root></app-root>")
        assertNull(resolver().resolve(location(), "portal"))
        assertNull(resolver().resolve(location(), "portal/tree"))
        assertNull(resolver().resolve(location(), "settings/projects"))
        assertNull(resolver().resolve(location(), "collector/bucket"))
    }

    @Test
    fun `Frontend fallback needs an index file to exist`() {
        assertNull(resolver().resolve(location(), "some-route"))
    }
}
