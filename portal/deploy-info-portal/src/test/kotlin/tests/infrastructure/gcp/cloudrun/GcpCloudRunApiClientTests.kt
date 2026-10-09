package tests.infrastructure.gcp.cloudrun

import com.google.api.gax.rpc.ApiException
import com.google.api.gax.rpc.StatusCode
import com.google.cloud.run.v2.Revision
import com.google.cloud.run.v2.Service
import domain.GcpListingException
import domain.Project
import infrastructure.gcp.CredentialsProvider
import infrastructure.gcp.cloudrun.GcpCloudRunApiClient
import infrastructure.gcp.cloudrun.GcpCloudRunApiClient.CloudRunAdminClient
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val PROJECT_ID = "testProject"

class GcpCloudRunApiClientTests {

    private val project = Project(name = PROJECT_ID, group = null, serviceAccount = "sa@test.iam.gserviceaccount.com")

    private fun client(
        adminClient: CloudRunAdminClient,
        credentialsProvider: CredentialsProvider = stubCredentialsProvider(),
    ): GcpCloudRunApiClient = GcpCloudRunApiClient(credentialsProvider) { adminClient }

    @Test
    fun `Map every revision of every service into a deploy, newest first`() {
        val adminClient = mock<CloudRunAdminClient> {
            on { listServices(PROJECT_ID) } doReturn listOf(
                service(
                    name = "projects/${PROJECT_ID}/locations/europe-west1/services/web-api",
                    uri = "https://web-api.example.com",
                ),
                service(
                    name = "projects/${PROJECT_ID}/locations/us-central1/services/worker",
                    uri = "",
                ),
            )
            on { listRevisions("projects/${PROJECT_ID}/locations/europe-west1/services/web-api") } doReturn listOf(
                revision(
                    name = "projects/${PROJECT_ID}/locations/europe-west1/services/web-api/revisions/rev-2",
                    createTime = epoch(20),
                    creator = "deployer@example.com",
                ),
                revision(
                    name = "projects/${PROJECT_ID}/locations/europe-west1/services/web-api/revisions/rev-1",
                    createTime = epoch(10),
                    creator = "",
                ),
            )
            on { listRevisions("projects/${PROJECT_ID}/locations/us-central1/services/worker") } doReturn listOf(
                revision(
                    name = "projects/${PROJECT_ID}/locations/us-central1/services/worker/revisions/rev-3",
                    createTime = epoch(30),
                ),
            )
        }

        val deploys = client(adminClient).listAllDeploys(project)

        assertEquals(listOf("rev-3", "rev-2", "rev-1"), deploys.map { it.revisionId })
        assertEquals(listOf("worker", "web-api", "web-api"), deploys.map { it.serviceId })
        assertEquals(listOf("us-central1", "europe-west1", "europe-west1"), deploys.map { it.location })
        assertEquals(listOf(PROJECT_ID, PROJECT_ID, PROJECT_ID), deploys.map { it.projectId })
        assertEquals(
            listOf(instant(30), instant(20), instant(10)),
            deploys.map { it.createTime },
        )
        assertEquals(listOf(null, "https://web-api.example.com", "https://web-api.example.com"), deploys.map { it.url })
        assertEquals(listOf(null, "deployer@example.com", null), deploys.map { it.createdBy })
    }

    @Test
    fun `Reuse the admin client of a service account across calls`() {
        val adminClient = mock<CloudRunAdminClient> {
            on { listServices(PROJECT_ID) } doReturn emptyList()
        }
        val client = client(adminClient)

        client.listAllDeploys(project)
        client.listAllDeploys(project)

        org.mockito.kotlin.verify(adminClient, org.mockito.kotlin.times(2)).listServices(PROJECT_ID)
    }

    @Test
    fun `Throw GcpListingException when the Cloud Run API returns an error`() {
        val adminClient = mock<CloudRunAdminClient> {
            on { listServices(PROJECT_ID) } doThrow
                ApiException(RuntimeException("no permission"), statusCode(StatusCode.Code.PERMISSION_DENIED), false)
        }

        val exception = assertFailsWith<GcpListingException> { client(adminClient).listAllDeploys(project) }

        assertEquals(
            "GCP Cloud Run API returned PERMISSION_DENIED for project ${PROJECT_ID}",
            exception.message,
        )
        assertEquals("PERMISSION_DENIED", exception.statusCode)
        assertTrue(exception.isPermissionDenied)
    }

    @Suppress("SameParameterValue")
    private fun statusCode(code: StatusCode.Code): StatusCode = object : StatusCode {
        override fun getCode(): StatusCode.Code = code
        override fun getTransportCode(): Any = ""
    }

    private fun service(name: String, uri: String): Service =
        Service.newBuilder().setName(name).setUri(uri).build()

    private fun revision(name: String, createTime: com.google.protobuf.Timestamp, creator: String? = null): Revision {
        val builder = Revision.newBuilder().setName(name)
        if (creator != null) {
            builder.creator = creator
        }
        return builder.setCreateTime(createTime).build()
    }

    private fun epoch(seconds: Long): com.google.protobuf.Timestamp =
        com.google.protobuf.Timestamp.newBuilder().setSeconds(seconds).build()

    private fun instant(seconds: Long): Instant = Instant.ofEpochSecond(seconds)

    private fun stubCredentialsProvider(): CredentialsProvider = CredentialsProvider {
        com.google.auth.oauth2.GoogleCredentials.create(
            com.google.auth.oauth2.AccessToken("token", java.util.Date(Long.MAX_VALUE))
        )
    }
}