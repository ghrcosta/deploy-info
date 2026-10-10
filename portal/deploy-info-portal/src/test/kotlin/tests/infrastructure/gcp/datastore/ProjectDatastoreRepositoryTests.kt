package tests.infrastructure.gcp.datastore

import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import domain.Project
import infrastructure.gcp.datastore.ProjectDatastoreRepository
import infrastructure.gcp.datastore.ProjectEntity
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNull


/**
 * Unit tests for [ProjectDatastoreRepository] using a mocked [DatastoreTemplate], so no Datastore
 * emulator (nor any other external dependency) is needed: they verify that the repository delegates
 * to the template and maps entities to domain models correctly.
 */
class ProjectDatastoreRepositoryTests {

    private val datastoreTemplate: DatastoreTemplate = mock()

    private val repository = ProjectDatastoreRepository(datastoreTemplate)

    @Test
    fun `Save a project through the datastore template`() {
        val project = Project(projectId = "testProject1", group = "test", serviceAccount = "test@account.com")

        repository.save(project)

        argumentCaptor<ProjectEntity>().apply {
            verify(datastoreTemplate).save(capture())
            assertEquals(project, firstValue.toModel())
        }
    }

    @Test
    fun `Get one project from the datastore`() {
        val entity = ProjectEntity(projectId = "testProject1", group = "test", serviceAccount = "test@account.com")
        whenever(datastoreTemplate.findById("testProject1", ProjectEntity::class.java)).thenReturn(entity)

        val project = repository.get("testProject1")

        assertEquals(entity.toModel(), project)
    }

    @Test
    fun `Return null when getting a project that does not exist`() {
        whenever(datastoreTemplate.findById("missing", ProjectEntity::class.java)).thenReturn(null)

        assertNull(repository.get("missing"))
    }

    @Test
    fun `Get all projects from the datastore`() {
        val entities = listOf(
            ProjectEntity(projectId = "testProject1", group = "group1", serviceAccount = "test@account.com"),
            ProjectEntity(projectId = "testProject2", group = null, serviceAccount = "test@account.com"),
        )
        whenever(datastoreTemplate.findAll(ProjectEntity::class.java)).thenReturn(entities)

        val projects = repository.getAll()

        assertEquals(
            listOf(
                Project(projectId = "testProject1", group = "group1", serviceAccount = "test@account.com"),
                Project(projectId = "testProject2", group = null, serviceAccount = "test@account.com"),
            ),
            projects,
        )
    }

    @Test
    fun `Delete a project from the datastore`() {
        repository.delete("testProject1")

        verify(datastoreTemplate).deleteById(eq("testProject1"), eq(ProjectEntity::class.java))
    }
}
