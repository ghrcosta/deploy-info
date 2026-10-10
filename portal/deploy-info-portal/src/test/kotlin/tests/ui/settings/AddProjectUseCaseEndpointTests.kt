package tests.ui.settings

import application.PortalIdentityResolver
import application.ServiceAccountIssue
import application.settings.AddProjectUseCase
import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import com.google.gson.Gson
import domain.Project
import infrastructure.DeployInfoPortalApplication
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.module.kotlin.jacksonObjectMapper
import ui.settings.AddProjectResultDTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest(classes = [DeployInfoPortalApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AddProjectUseCaseEndpointTests {

    @Autowired
    private lateinit var mvc: MockMvc

    @MockitoBean
    private lateinit var datastoreTemplate: DatastoreTemplate

    @MockitoBean
    private lateinit var useCase: AddProjectUseCase

    @MockitoBean
    private lateinit var portalIdentityResolver: PortalIdentityResolver

    private val uri = "/settings/project"

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `Add project without issues`() {
        val project = Project(projectId = "testProject1", group = "test", serviceAccount = "test@account.com")
        val output = AddProjectUseCase.Output(
            issueProjectIdConflict = false,
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
        val project = Project(projectId = "testProject1", group = "test", serviceAccount = "test@account.com")
        val output = AddProjectUseCase.Output(
            issueProjectIdConflict = true,
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
        assertEquals(true, dto.issues?.issueProjectIdConflict)
        assertNull(dto.issues?.serviceAccountIssue)
        assertNull(dto.projects)
    }

    @Test
    fun `Notify issue when adding project with service account problem`() {
        val project = Project(projectId = "testProject1", group = "test", serviceAccount = "test@account.com")
        val output = AddProjectUseCase.Output(
            issueProjectIdConflict = false,
            issueServiceAccountError = true,
            serviceAccountIssue = ServiceAccountIssue.MISSING_IMPERSONATION_PERMISSION,
            projectsInRepository = emptyList()
        )
        whenever(useCase.execute(any())).thenReturn(output)
        whenever(portalIdentityResolver.resolve()).thenReturn("portal@test.iam.gserviceaccount.com")

        val body = Gson().toJson(project)
        val result = mvc.perform(
            post(uri)
                .content(body)
                .contentType(MediaType.APPLICATION_JSON)
        ).andReturn()

        assertEquals(HttpStatus.OK.value(), result.response.status)
        val dto = objectMapper.readValue(result.response.contentAsString, AddProjectResultDTO::class.java)
        assertEquals(true, dto.issues?.issueServiceAccountError)
        assertEquals("MISSING_IMPERSONATION_PERMISSION", dto.issues?.serviceAccountIssue)
        assertEquals("portal@test.iam.gserviceaccount.com", dto.issues?.portalServiceAccount)
        assertNull(dto.projects)
    }

    @Test
    fun `Notify issue when adding project that the listing APIs cannot resolve`() {
        val project = Project(projectId = "testProject1", group = "test", serviceAccount = "test@account.com")
        val output = AddProjectUseCase.Output(
            issueProjectIdConflict = false,
            issueServiceAccountError = true,
            serviceAccountIssue = ServiceAccountIssue.PROJECT_NOT_FOUND,
            projectsInRepository = emptyList()
        )
        whenever(useCase.execute(any())).thenReturn(output)
        whenever(portalIdentityResolver.resolve()).thenReturn("portal@test.iam.gserviceaccount.com")

        val body = Gson().toJson(project)
        val result = mvc.perform(
            post(uri)
                .content(body)
                .contentType(MediaType.APPLICATION_JSON)
        ).andReturn()

        assertEquals(HttpStatus.OK.value(), result.response.status)
        val dto = objectMapper.readValue(result.response.contentAsString, AddProjectResultDTO::class.java)
        assertEquals(true, dto.issues?.issueServiceAccountError)
        assertEquals("PROJECT_NOT_FOUND", dto.issues?.serviceAccountIssue)
        assertEquals("portal@test.iam.gserviceaccount.com", dto.issues?.portalServiceAccount)
        assertNull(dto.projects)
    }
}
