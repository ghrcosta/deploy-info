package tests.ui.settings

import application.settings.DeleteProjectUseCase
import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import domain.Project
import infrastructure.DeployInfoPortalApplication
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import tools.jackson.core.type.TypeReference
import tools.jackson.module.kotlin.jacksonObjectMapper
import ui.settings.ProjectDTO
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(classes = [DeployInfoPortalApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeleteProjectUseCaseEndpointTests {

    @Autowired
    private lateinit var mvc: MockMvc

    @MockitoBean
    private lateinit var datastoreTemplate: DatastoreTemplate

    @MockitoBean
    private lateinit var useCase: DeleteProjectUseCase

    private val uriTemplate = "/settings/project/%s"

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `Return after delete project operation`() {
        val output = DeleteProjectUseCase.Output(
            projectsInRepository = listOf(
                Project(projectId = "testProject1", group = "test", serviceAccount = "test@account.com")
            )
        )
        whenever(useCase.execute(any())).thenReturn(output)

        val uri = String.format(uriTemplate, "project-name")
        val result = mvc.perform(delete(uri)).andReturn()

        assertEquals(HttpStatus.OK.value(), result.response.status)
        val dto = objectMapper.readValue(result.response.contentAsString, object : TypeReference<List<ProjectDTO>>(){})
        assertEquals(1, dto.size)
    }
}