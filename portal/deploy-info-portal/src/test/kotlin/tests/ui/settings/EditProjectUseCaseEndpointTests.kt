package tests.ui.settings

import application.PortalIdentityResolver
import application.ServiceAccountIssue
import application.settings.EditProjectUseCase
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import tools.jackson.module.kotlin.jacksonObjectMapper
import ui.settings.EditProjectResultDTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest(classes = [DeployInfoPortalApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EditProjectUseCaseEndpointTests {

    @Autowired
    private lateinit var mvc: MockMvc

    @MockitoBean
    private lateinit var datastoreTemplate: DatastoreTemplate

    @MockitoBean
    private lateinit var useCase: EditProjectUseCase

    @MockitoBean
    private lateinit var portalIdentityResolver: PortalIdentityResolver

    private val uri = "/settings/project"

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `Edit project without issues`() {
        val project = Project(name = "testProject1", group = "test", serviceAccount = "test@account.com")
        val output = EditProjectUseCase.Output(
            issueProjectNotFound = false,
            issueServiceAccountError = false,
            projectsInRepository = listOf(project)
        )
        whenever(useCase.execute(any())).thenReturn(output)

        val body = Gson().toJson(project)
        val result = mvc.perform(
            put(uri)
                .content(body)
                .contentType(MediaType.APPLICATION_JSON)
        ).andReturn()

        assertEquals(HttpStatus.OK.value(), result.response.status)
        val dto = objectMapper.readValue(result.response.contentAsString, EditProjectResultDTO::class.java)
        assertNull(dto.issues)
        assertEquals(1, dto.projects?.size)
    }

    @Test
    fun `Notify issue when editing project that does not exist`() {
        val project = Project(name = "testProject1", group = "test", serviceAccount = "test@account.com")
        val output = EditProjectUseCase.Output(
            issueProjectNotFound = true,
            issueServiceAccountError = false,
            projectsInRepository = emptyList()
        )
        whenever(useCase.execute(any())).thenReturn(output)

        val body = Gson().toJson(project)
        val result = mvc.perform(
            put(uri)
                .content(body)
                .contentType(MediaType.APPLICATION_JSON)
        ).andReturn()

        assertEquals(HttpStatus.OK.value(), result.response.status)
        val dto = objectMapper.readValue(result.response.contentAsString, EditProjectResultDTO::class.java)
        assertEquals(true, dto.issues?.issueProjectNotFound)
        assertNull(dto.issues?.serviceAccountIssue)
        assertNull(dto.projects)
    }

    @Test
    fun `Notify issue when editing project with service account problem`() {
        val project = Project(name = "testProject1", group = "test", serviceAccount = "test@account.com")
        val output = EditProjectUseCase.Output(
            issueProjectNotFound = false,
            issueServiceAccountError = true,
            serviceAccountIssue = ServiceAccountIssue.MISSING_LISTING_PERMISSION,
            projectsInRepository = emptyList()
        )
        whenever(useCase.execute(any())).thenReturn(output)
        whenever(portalIdentityResolver.resolve()).thenReturn("portal@test.iam.gserviceaccount.com")

        val body = Gson().toJson(project)
        val result = mvc.perform(
            put(uri)
                .content(body)
                .contentType(MediaType.APPLICATION_JSON)
        ).andReturn()

        assertEquals(HttpStatus.OK.value(), result.response.status)
        val dto = objectMapper.readValue(result.response.contentAsString, EditProjectResultDTO::class.java)
        assertEquals(true, dto.issues?.issueServiceAccountError)
        assertEquals("MISSING_LISTING_PERMISSION", dto.issues?.serviceAccountIssue)
        assertEquals("portal@test.iam.gserviceaccount.com", dto.issues?.portalServiceAccount)
        assertNull(dto.projects)
    }
}
