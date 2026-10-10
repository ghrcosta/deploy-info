package tests.infrastructure.frontend

import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import infrastructure.DeployInfoPortalApplication
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the real serving stack (resource handlers + frontend fallback) registered in
 * `FrontendServingConfiguration`, against an `index.html` that only exists on the test classpath —
 * the frontend is embedded in the bootJar only, so `test` keeps working without Node/npm.
 */
@SpringBootTest(classes = [DeployInfoPortalApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FrontendServingEndpointTests {

    @Autowired
    private lateinit var mvc: MockMvc

    @MockitoBean
    private lateinit var datastoreTemplate: DatastoreTemplate

    @Test
    fun `Root serves the frontend index`() {
        val result = mvc.perform(get("/")).andReturn()
        assertEquals(HttpStatus.OK.value(), result.response.status)
        // MockMvc leaves the forwarded body empty; the forward target proves the index is served.
        assertEquals("index.html", result.response.forwardedUrl)
    }

    @Test
    fun `Unknown frontend route serves the index`() {
        val result = mvc.perform(get("/some/frontend/route")).andReturn()
        assertEquals(HttpStatus.OK.value(), result.response.status)
        assertTrue(result.response.contentAsString.contains("<app-root"))
    }

    @Test
    fun `Missing asset is not served the frontend index`() {
        val result = mvc.perform(get("/missing.png")).andReturn()
        assertEquals(HttpStatus.NOT_FOUND.value(), result.response.status)
    }

    @Test
    fun `API paths are not served the frontend index`() {
        // GET /settings/projects reaches the (mocked) settings use case — any response coming from
        // the real handler proves the API takes precedence over the frontend fallback resource
        // handler.
        val result = mvc.perform(get("/settings/projects")).andReturn()
        assertTrue(result.response.contentAsString.isEmpty())
    }
}
