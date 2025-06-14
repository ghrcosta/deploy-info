package tests.application.settings

import application.ProjectRepository
import application.settings.EditProjectUseCase
import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import domain.Project
import infrastructure.DeployInfoPortalApplication
import infrastructure.gcp.datastore.ProjectEntity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.*

@SpringBootTest(classes = [DeployInfoPortalApplication::class])
@ActiveProfiles("test")
class EditProjectUseCaseTests {

    @Autowired
    private lateinit var datastoreTemplate: DatastoreTemplate

    @Autowired
    private lateinit var projectRepository: ProjectRepository

    private lateinit var editProjectUseCase: EditProjectUseCase

    @BeforeTest
    fun setup() {
        datastoreTemplate.deleteAll(ProjectEntity::class.java)

        editProjectUseCase = EditProjectUseCase(projectRepository)
    }

    @Test
    fun `Edit project without issues`() {
        val project = Project(name = "testProject1", category = "test", serviceAccount = "test@account.com")
        projectRepository.save(project)

        val modifiedProject = Project(name = "testProject1", category = "test2", serviceAccount = "test2@account.com")
        val output = editProjectUseCase.execute(modifiedProject)
        assertFalse(output.issuesFound())
        assertEquals(1, output.projectsInRepository?.size)

        val savedProject = projectRepository.get(project.name)
        assertNotNull(savedProject)
        assertEquals(modifiedProject.category, savedProject.category)
        assertEquals(modifiedProject.serviceAccount, savedProject.serviceAccount)
    }

    @Test
    fun `Notify issue when editing project that does not exist`() {
        val project = Project(name = "testProject", category = "test", serviceAccount = "test@account.com")

        val output = editProjectUseCase.execute(project)
        assertTrue(output.issuesFound())
        assertTrue(output.issueProjectNotFound)
        assertNull(output.projectsInRepository)
    }

    @Test
    fun `Notify issue when editing project with service account problem`() {
        val project = Project(name = "testProject1", category = "test", serviceAccount = "test@account.com")
        projectRepository.save(project)

        val modifiedProject = Project(name = "testProject1", category = "test", serviceAccount = "test2@account.com")
        val output = editProjectUseCase.execute(modifiedProject)
        assertTrue(output.issuesFound())
        assertTrue(output.issueServiceAccountError)
        assertNull(output.projectsInRepository)
    }
}