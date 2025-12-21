package tests.application.settings

import application.ProjectRepository
import application.settings.AddProjectUseCase
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
class AddProjectUseCaseTests {

    @Autowired
    private lateinit var datastoreTemplate: DatastoreTemplate

    @Autowired
    private lateinit var projectRepository: ProjectRepository

    private lateinit var addProjectUseCase: AddProjectUseCase

    @BeforeTest
    fun setup() {
        datastoreTemplate.deleteAll(ProjectEntity::class.java)

        addProjectUseCase = AddProjectUseCase(projectRepository)
    }

    @Test
    fun `Add project without issues`() {
        val newProject1 = Project(name = "testProject1", group = "test", serviceAccount = "test@account.com")
        val output = addProjectUseCase.execute(newProject1)
        assertFalse(output.issuesFound())
        assertEquals(1, output.projectsInRepository.size)
    }

    @Test
    fun `Notify issue when adding project with same name twice`() {
        val newProject1 = Project(name = "testProject", group = "test", serviceAccount = "test@account.com")
        projectRepository.save(newProject1)

        val newProject2 = Project(name = "testProject", group = "test2", serviceAccount = "test2@account.com")
        val output = addProjectUseCase.execute(newProject2)
        assertTrue(output.issuesFound())
        assertTrue(output.issueNameConflict)
        assert(output.projectsInRepository.isEmpty())
    }

    @Test
    fun `Notify issue when adding project with service account problem`() {
        val newProject1 = Project(name = "testProject1", group = "test", serviceAccount = "test@account.com")

        val output = addProjectUseCase.execute(newProject1)
        assertTrue(output.issuesFound())
        assertTrue(output.issueServiceAccountError)
        assert(output.projectsInRepository.isEmpty())
    }
}