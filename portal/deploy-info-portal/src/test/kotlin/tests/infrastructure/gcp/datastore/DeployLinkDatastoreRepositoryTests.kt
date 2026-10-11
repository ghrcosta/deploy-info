package tests.infrastructure.gcp.datastore

import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import domain.DeployLink
import domain.DeployType
import infrastructure.gcp.datastore.DeployLinkDatastoreRepository
import infrastructure.gcp.datastore.DeployLinkEntity
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [DeployLinkDatastoreRepository] using a mocked [DatastoreTemplate], so no Datastore
 * emulator (nor any other external dependency) is needed: they verify that the repository delegates
 * to the template and maps entities to domain models correctly.
 */
class DeployLinkDatastoreRepositoryTests {

    private val datastoreTemplate: DatastoreTemplate = mock()

    private val repository = DeployLinkDatastoreRepository(datastoreTemplate)

    private val gaeLink = DeployLink(
        projectId = "testProject1",
        deployType = DeployType.GAE,
        serviceId = "default",
        versionId = "20261004t120000",
        location = null,
        storageFolder = "john.doe_GAE_1746322088662",
        userEmail = "john.doe@example.com",
        collectTimestamp = Instant.ofEpochMilli(1746322088662),
        deployTimestamp = Instant.ofEpochMilli(1746322082000),
    )

    private val runLink = DeployLink(
        projectId = "testProject2",
        deployType = DeployType.RUN,
        serviceId = "my-service",
        versionId = "my-service-00001-abc",
        location = "europe-west1",
        storageFolder = "jane.doe_RUN_1746322089000",
        userEmail = "jane.doe@example.com",
        collectTimestamp = Instant.ofEpochMilli(1746322089000),
        deployTimestamp = Instant.ofEpochMilli(1746322085000),
        url = "https://my-service-00001-abc.run.app",
    )

    @Test
    fun `Save an App Engine deploy link through the datastore template`() {
        repository.save(gaeLink)

        argumentCaptor<DeployLinkEntity>().apply {
            verify(datastoreTemplate).save(capture())
            assertEquals(gaeLink, firstValue.toModel())
        }
    }

    @Test
    fun `Save a Cloud Run deploy link through the datastore template`() {
        repository.save(runLink)

        argumentCaptor<DeployLinkEntity>().apply {
            verify(datastoreTemplate).save(capture())
            assertEquals(runLink, firstValue.toModel())
        }
    }

    @Test
    fun `Get one deploy link from the datastore by its identity`() {
        whenever(datastoreTemplate.findById("testProject1_GAE_-_default_20261004t120000", DeployLinkEntity::class.java))
            .thenReturn(DeployLinkEntity(gaeLink))

        val link = repository.get(
            projectId = "testProject1",
            deployType = DeployType.GAE,
            location = null,
            serviceId = "default",
            versionId = "20261004t120000",
        )

        assertEquals(gaeLink, link)
    }

    @Test
    fun `Return null when getting a deploy link that does not exist`() {
        whenever(datastoreTemplate.findById("missing", DeployLinkEntity::class.java)).thenReturn(null)

        assertNull(
            repository.get(
                projectId = "missing",
                deployType = DeployType.GAE,
                location = null,
                serviceId = "default",
                versionId = "some-version",
            ),
        )
    }

    @Test
    fun `Get all deploy links of a project and deploy type from the datastore`() {
        whenever(datastoreTemplate.findAll(DeployLinkEntity::class.java)).thenReturn(
            listOf(DeployLinkEntity(gaeLink), DeployLinkEntity(runLink)),
        )

        val links = repository.getAllFor("testProject1", DeployType.GAE)

        assertEquals(listOf(gaeLink), links)
    }

    @Test
    fun `Delete a deploy link from the datastore`() {
        repository.delete(runLink)

        verify(datastoreTemplate).deleteById(eq("testProject2_RUN_europe-west1_my-service_my-service-00001-abc"), eq(DeployLinkEntity::class.java))
    }
}