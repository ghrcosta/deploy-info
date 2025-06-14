package tests.application.settings

import application.ProjectRepository
import application.settings.DeleteProjectUseCase
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
class DeleteProjectUseCaseTests {

    @Autowired
    private lateinit var datastoreTemplate: DatastoreTemplate

    @Autowired
    private lateinit var projectRepository: ProjectRepository

    private lateinit var deleteProjectUseCase: DeleteProjectUseCase

    @BeforeTest
    fun setup() {
        datastoreTemplate.deleteAll(ProjectEntity::class.java)

        deleteProjectUseCase = DeleteProjectUseCase(projectRepository)
    }

    @Test
    fun `Delete project without issues`() {
        val project = Project(name = "testProject1", category = "test", serviceAccount = "test@account.com")
        projectRepository.save(project)

        val output = deleteProjectUseCase.execute(project.name)
        assertNotNull(output.projectsInRepository)
        assertTrue(output.projectsInRepository.isEmpty())
    }

    @Test
    fun `Ignore when trying to delete project that does not exist`() {
        val project = Project(name = "testProject", category = "test", serviceAccount = "test@account.com")
        projectRepository.save(project)

        val output = deleteProjectUseCase.execute("wrong-name")
        assertEquals(1, output.projectsInRepository.size)
    }

    @Test
    fun `Ignore when trying to delete project with repository empty`() {
        val output = deleteProjectUseCase.execute("wrong-name")
        assertNotNull(output.projectsInRepository)
        assertTrue(output.projectsInRepository.isEmpty())
    }
}