package tests.application.settings

import application.ProjectRepository
import application.settings.GetAllProjectsUseCase
import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import domain.Project
import infrastructure.DeployInfoPortalApplication
import infrastructure.gcp.datastore.ProjectEntity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(classes = [DeployInfoPortalApplication::class])
@ActiveProfiles("test")
class GetAllProjectsUseCaseTests {

    @Autowired
    private lateinit var datastoreTemplate: DatastoreTemplate

    @Autowired
    private lateinit var projectRepository: ProjectRepository

    private lateinit var getAllProjectsUseCase: GetAllProjectsUseCase

    @BeforeTest
    fun setup() {
        datastoreTemplate.deleteAll(ProjectEntity::class.java)

        getAllProjectsUseCase = GetAllProjectsUseCase(projectRepository)
    }

    @Test
    fun `Return empty list when repository is empty`() {
        val output = getAllProjectsUseCase.execute()
        assertTrue(output.projectsInRepository.isEmpty())
    }

    @Test
    fun `Return all projects from the repository`() {
        val newProject1 = Project(name = "testProject1", category = "test", serviceAccount = "test@account.com")
        val newProject2 = Project(name = "testProject2", category = "test", serviceAccount = "test@account.com")
        val newProject3 = Project(name = "testProject3", category = "test", serviceAccount = "test@account.com")
        projectRepository.save(newProject1)
        projectRepository.save(newProject2)
        projectRepository.save(newProject3)

        val output = getAllProjectsUseCase.execute()
        assertEquals(3, output.projectsInRepository.size)
        assertEquals(newProject1, output.projectsInRepository[0])
        assertEquals(newProject2, output.projectsInRepository[1])
        assertEquals(newProject3, output.projectsInRepository[2])
    }
}