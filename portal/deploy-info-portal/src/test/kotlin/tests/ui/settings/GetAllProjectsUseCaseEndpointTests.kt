package tests.ui.settings

import application.settings.GetAllProjectsUseCase
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import domain.Project
import infrastructure.DeployInfoPortalApplication
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import ui.settings.ProjectDTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(classes = [DeployInfoPortalApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GetAllProjectsUseCaseEndpointTests {

    @Autowired
    private lateinit var mvc: MockMvc

    @MockitoBean
    private lateinit var useCase: GetAllProjectsUseCase

    private val uri = "/settings/projects"

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `Happy path with repository empty`() {
        val output = GetAllProjectsUseCase.Output(
            projectsInRepository = listOf()
        )
        whenever(useCase.execute()).thenReturn(output)

        val result = mvc.perform(get(uri)).andReturn()
        assertEquals(HttpStatus.NO_CONTENT.value(), result.response.status)
        assertTrue(result.response.contentAsString.isEmpty())
    }

    @Test
    fun `Happy path with repository not empty`() {
        val output = GetAllProjectsUseCase.Output(
            projectsInRepository = listOf(
                Project(name = "testProject1", category = "test", serviceAccount = "test@account.com"),
                Project(name = "testProject2", category = "test", serviceAccount = "test@account.com")
            )
        )
        whenever(useCase.execute()).thenReturn(output)

        val result = mvc.perform(get(uri)).andReturn()
        assertEquals(HttpStatus.OK.value(), result.response.status)

        val dto = objectMapper.readValue(result.response.contentAsString, object : TypeReference<List<ProjectDTO>>(){})
        assertEquals(2, dto.size)
    }
}