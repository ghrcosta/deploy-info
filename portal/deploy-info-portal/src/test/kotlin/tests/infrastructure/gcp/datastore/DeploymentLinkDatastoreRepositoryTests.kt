package tests.infrastructure.gcp.datastore

import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import domain.DeploymentLink
import domain.DeploymentType
import infrastructure.gcp.datastore.DeploymentLinkDatastoreRepository
import infrastructure.gcp.datastore.DeploymentLinkEntity
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [DeploymentLinkDatastoreRepository] using a mocked [DatastoreTemplate], so no Datastore
 * emulator (nor any other external dependency) is needed: they verify that the repository delegates
 * to the template and maps entities to domain models correctly.
 */
class DeploymentLinkDatastoreRepositoryTests {

    private val datastoreTemplate: DatastoreTemplate = mock()

    private val repository = DeploymentLinkDatastoreRepository(datastoreTemplate)

    private val gaeLink = DeploymentLink(
        projectId = "testProject1",
        deploymentType = DeploymentType.GAE,
        serviceId = "default",
        versionId = "20261004t120000",
        location = null,
        storageFolder = "john.doe_GAE_1746322088662",
        userEmail = "john.doe@example.com",
        collectTimestamp = Instant.ofEpochMilli(1746322088662),
        deploymentTimestamp = Instant.ofEpochMilli(1746322082000),
    )

    private val runLink = DeploymentLink(
        projectId = "testProject2",
        deploymentType = DeploymentType.RUN,
        serviceId = "my-service",
        versionId = "my-service-00001-abc",
        location = "europe-west1",
        storageFolder = "jane.doe_RUN_1746322089000",
        userEmail = "jane.doe@example.com",
        collectTimestamp = Instant.ofEpochMilli(1746322089000),
        deploymentTimestamp = Instant.ofEpochMilli(1746322085000),
        url = "https://my-service-00001-abc.run.app",
    )

    @Test
    fun `Save an App Engine deployment link through the datastore template`() {
        repository.save(gaeLink)

        argumentCaptor<DeploymentLinkEntity>().apply {
            verify(datastoreTemplate).save(capture())
            assertEquals(gaeLink, firstValue.toModel())
        }
    }

    @Test
    fun `Save a Cloud Run deployment link through the datastore template`() {
        repository.save(runLink)

        argumentCaptor<DeploymentLinkEntity>().apply {
            verify(datastoreTemplate).save(capture())
            assertEquals(runLink, firstValue.toModel())
        }
    }

    @Test
    fun `Get one deployment link from the datastore by its identity`() {
        whenever(datastoreTemplate.findById("testProject1_GAE_-_default_20261004t120000", DeploymentLinkEntity::class.java))
            .thenReturn(DeploymentLinkEntity(gaeLink))

        val link = repository.get(
            projectId = "testProject1",
            deploymentType = DeploymentType.GAE,
            location = null,
            serviceId = "default",
            versionId = "20261004t120000",
        )

        assertEquals(gaeLink, link)
    }

    @Test
    fun `Return null when getting a deployment link that does not exist`() {
        whenever(datastoreTemplate.findById("missing", DeploymentLinkEntity::class.java)).thenReturn(null)

        assertNull(
            repository.get(
                projectId = "missing",
                deploymentType = DeploymentType.GAE,
                location = null,
                serviceId = "default",
                versionId = "some-version",
            ),
        )
    }

    @Test
    fun `Get all deployment links of a project and deployment type from the datastore`() {
        whenever(datastoreTemplate.findAll(DeploymentLinkEntity::class.java)).thenReturn(
            listOf(DeploymentLinkEntity(gaeLink), DeploymentLinkEntity(runLink)),
        )

        val links = repository.getAllFor("testProject1", DeploymentType.GAE)

        assertEquals(listOf(gaeLink), links)
    }

    @Test
    fun `Delete a deployment link from the datastore`() {
        repository.delete(runLink)

        verify(datastoreTemplate).deleteById(eq("testProject2_RUN_europe-west1_my-service_my-service-00001-abc"), eq(DeploymentLinkEntity::class.java))
    }
}