package tests.infrastructure.gcp.datastore

import application.ProjectRepository
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
class ProjectDatastoreRepositoryTests {

    @Autowired
    private lateinit var datastoreTemplate: DatastoreTemplate

    @Autowired
    private lateinit var projectRepository: ProjectRepository

    @BeforeTest
    fun setup() {
        datastoreTemplate.deleteAll(ProjectEntity::class.java)
    }

    @Test
    fun `Return empty list when repository is empty`() {
        val projectsInRepository = projectRepository.getAll()
        assertTrue(projectsInRepository.isEmpty())
    }

    @Test
    fun `Return all projects from the repository`() {
        val newProject1 = Project(name = "testProject1", category = "test", serviceAccount = "test@account.com")
        val newProject2 = Project(name = "testProject2", category = "test", serviceAccount = "test@account.com")
        val newProject3 = Project(name = "testProject3", category = "test", serviceAccount = "test@account.com")
        projectRepository.save(newProject1)
        projectRepository.save(newProject2)
        projectRepository.save(newProject3)

        val projectsInRepository = projectRepository.getAll()
        assertEquals(3, projectsInRepository.size)
        assertEquals(newProject1, projectsInRepository[0])
        assertEquals(newProject2, projectsInRepository[1])
        assertEquals(newProject3, projectsInRepository[2])
    }

    @Test
    fun `Return one project from the repository`() {
        val newProject1 = Project(name = "testProject1", category = "test", serviceAccount = "test@account.com")
        val newProject2 = Project(name = "testProject2", category = "test", serviceAccount = "test@account.com")
        val newProject3 = Project(name = "testProject3", category = "test", serviceAccount = "test@account.com")
        projectRepository.save(newProject1)
        projectRepository.save(newProject2)
        projectRepository.save(newProject3)

        val savedProject1 = projectRepository.get(newProject1.name)
        assertTrue(savedProject1 == newProject1)
    }

    @Test
    fun `Delete a project from the repository`() {
        val newProject1 = Project(name = "testProject1", category = "test", serviceAccount = "test@account.com")
        val newProject2 = Project(name = "testProject2", category = "test", serviceAccount = "test@account.com")
        val newProject3 = Project(name = "testProject3", category = "test", serviceAccount = "test@account.com")
        projectRepository.save(newProject1)
        projectRepository.save(newProject2)
        projectRepository.save(newProject3)

        projectRepository.delete(newProject1.name)

        val projectsInRepository = projectRepository.getAll()
        assertEquals(2, projectsInRepository.size)
        assertEquals(newProject2, projectsInRepository[0])
        assertEquals(newProject3, projectsInRepository[1])
    }
}