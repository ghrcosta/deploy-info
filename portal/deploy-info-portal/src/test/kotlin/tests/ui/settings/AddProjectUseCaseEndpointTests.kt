package tests.ui.settings

import application.settings.AddProjectUseCase
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.Gson
import domain.Project
import infrastructure.DeployInfoPortalApplication
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import ui.settings.AddProjectResultDTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(classes = [DeployInfoPortalApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AddProjectUseCaseEndpointTests {

    @Autowired
    private lateinit var mvc: MockMvc

    @MockitoBean
    private lateinit var useCase: AddProjectUseCase

    private val uri = "/settings/project"

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `Add project without issues`() {
        val project = Project(name = "testProject1", group = "test", serviceAccount = "test@account.com")
        val output = AddProjectUseCase.Output(
            issueNameConflict = false,
            issueServiceAccountError = false,
            projectsInRepository = listOf(project)
        )
        whenever(useCase.execute(any())).thenReturn(output)

        val body = Gson().toJson(project)
        val result = mvc.perform(
            post(uri)
                .content(body)
                .contentType(MediaType.APPLICATION_JSON)
        ).andReturn()

        assertEquals(HttpStatus.OK.value(), result.response.status)
        val dto = objectMapper.readValue(result.response.contentAsString, AddProjectResultDTO::class.java)
        assertNull(dto.issues)
        assertEquals(1, dto.projects?.size)
    }

    @Test
    fun `Notify issue when adding project with same name twice`() {
        val project = Project(name = "testProject1", group = "test", serviceAccount = "test@account.com")
        val output = AddProjectUseCase.Output(
            issueNameConflict = true,
            issueServiceAccountError = false,
            projectsInRepository = emptyList()
        )
        whenever(useCase.execute(any())).thenReturn(output)

        val body = Gson().toJson(project)
        val result = mvc.perform(
            post(uri)
                .content(body)
                .contentType(MediaType.APPLICATION_JSON)
        ).andReturn()

        assertEquals(HttpStatus.OK.value(), result.response.status)
        val dto = objectMapper.readValue(result.response.contentAsString, AddProjectResultDTO::class.java)
        assertTrue(dto.issues?.issueNameConflict == true)
        assertNull(dto.projects)
    }

    @Test
    fun `Notify issue when adding project with service account problem`() {
        val project = Project(name = "testProject1", group = "test", serviceAccount = "test@account.com")
        val output = AddProjectUseCase.Output(
            issueNameConflict = false,
            issueServiceAccountError = true,
            projectsInRepository = emptyList()
        )
        whenever(useCase.execute(any())).thenReturn(output)

        val body = Gson().toJson(project)
        val result = mvc.perform(
            post(uri)
                .content(body)
                .contentType(MediaType.APPLICATION_JSON)
        ).andReturn()

        assertEquals(HttpStatus.OK.value(), result.response.status)
        val dto = objectMapper.readValue(result.response.contentAsString, AddProjectResultDTO::class.java)
        assertTrue(dto.issues?.issueServiceAccountError == true)
        assertNull(dto.projects)
    }
}