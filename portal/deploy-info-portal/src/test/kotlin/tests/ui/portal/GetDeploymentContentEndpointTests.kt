package tests.ui.portal

import application.content.GetDeploymentContentUseCase
import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import domain.ContentFile
import domain.DeploymentContent
import domain.GitContent
import infrastructure.DeployInfoPortalApplication
import org.mockito.kotlin.whenever
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
import kotlin.test.assertNull

/**
 * Proves the `GET /portal/deploy/content` contract of [ui.portal.PortalController]: successful
 * responses are browser-cacheable (`Cache-Control: private, max-age=604800, immutable` — upload
 * folders are immutable until the cleanup sweep removes the whole folder), while error responses
 * must stay uncached.
 */
@SpringBootTest(classes = [DeployInfoPortalApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GetDeploymentContentEndpointTests {

    @Autowired
    private lateinit var mvc: MockMvc

    /** Unused by this endpoint, but the test profile provides no `DatastoreTemplate` bean. */
    @MockitoBean
    private lateinit var datastoreTemplate: DatastoreTemplate

    @MockitoBean
    private lateinit var useCase: GetDeploymentContentUseCase

    private val folder = "jdoe_GAE_1735689600000"

    private val uri = "/portal/deploy/content"

    @Test
    fun `Happy path - 200 response carries the browser cache header`() {
        val content = DeploymentContent(
            git = GitContent(
                gitLog = "log output",
                gitStatus = "status output",
                changes = listOf(ContentFile("/src/main.kt", "diff --git a/src/main.kt")),
            ),
            extras = listOf(ContentFile("/docs/readme.md", "# readme")),
        )
        whenever(useCase.execute(GetDeploymentContentUseCase.Input(folder)))
            .thenReturn(GetDeploymentContentUseCase.Output.Content(content))

        val result = mvc.perform(get(uri).param("folder", folder)).andReturn()

        assertEquals(HttpStatus.OK.value(), result.response.status)
        assertEquals("private, max-age=604800, immutable", result.response.getHeader("Cache-Control"))
    }

    @Test
    fun `Folder not found - 404 response carries no cache header`() {
        whenever(useCase.execute(GetDeploymentContentUseCase.Input(folder)))
            .thenReturn(GetDeploymentContentUseCase.Output.FolderNotFound)

        val result = mvc.perform(get(uri).param("folder", folder)).andReturn()

        assertEquals(HttpStatus.NOT_FOUND.value(), result.response.status)
        assertNull(result.response.getHeader("Cache-Control"))
    }
}