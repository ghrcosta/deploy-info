package tests.infrastructure.gcp.appengine

import com.google.api.gax.rpc.ApiException
import com.google.api.gax.rpc.StatusCode
import com.google.appengine.v1.Version
import domain.GcpListingException
import domain.Project
import infrastructure.gcp.CredentialsProvider
import infrastructure.gcp.appengine.GcpAppEngineApiClient
import infrastructure.gcp.appengine.GcpAppEngineApiClient.AppEngineAdminClient
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val PROJECT_ID = "testProject"

class GcpAppEngineApiClientTests {

    private val project = Project(name = PROJECT_ID, group = null, serviceAccount = "sa@test.iam.gserviceaccount.com")

    private fun client(
        adminClient: AppEngineAdminClient,
        credentialsProvider: CredentialsProvider = stubCredentialsProvider(),
    ): GcpAppEngineApiClient = GcpAppEngineApiClient(credentialsProvider) { adminClient }

    @Test
    fun `Map every version of every service into a deploy, newest first`() {
        val adminClient = mock<AppEngineAdminClient> {
            on { listServices(PROJECT_ID) } doReturn listOf(
                service(id = "web"), service(id = "worker"),
            )
            on { listVersions(PROJECT_ID, "web") } doReturn listOf(
                version(id = "2", createTime = epoch(20), versionUrl = "https://web-2.example.com"),
                version(id = "1", createTime = epoch(10), versionUrl = ""),
            )
            on { listVersions(PROJECT_ID, "worker") } doReturn listOf(
                version(id = "1", createTime = epoch(30), versionUrl = null),
            )
        }

        val deploys = client(adminClient).listAllDeploys(project)

        assertEquals(listOf("1", "2", "1"), deploys.map { it.versionId })
        assertEquals(listOf("worker", "web", "web"), deploys.map { it.serviceId })
        assertEquals(listOf(PROJECT_ID, PROJECT_ID, PROJECT_ID), deploys.map { it.projectId })
        assertEquals(
            listOf(instant(30), instant(20), instant(10)),
            deploys.map { it.createTime },
        )
        assertEquals(listOf<String?>(null, "https://web-2.example.com", null), deploys.map { it.url })
    }

    @Test
    fun `Reuse the admin client of a service account across calls`() {
        val adminClient = mock<AppEngineAdminClient> {
            on { listServices(PROJECT_ID) } doReturn emptyList()
        }
        val client = client(adminClient)

        client.listAllDeploys(project)
        client.listAllDeploys(project)

        org.mockito.kotlin.verify(adminClient, org.mockito.kotlin.times(2)).listServices(PROJECT_ID)
    }

    @Test
    fun `Throw GcpListingException when the App Engine API returns an error`() {
        val adminClient = mock<AppEngineAdminClient> {
            on { listServices(PROJECT_ID) } doThrow
                ApiException(RuntimeException("no permission"), statusCode(StatusCode.Code.PERMISSION_DENIED), false)
        }

        val exception = assertFailsWith<GcpListingException> { client(adminClient).listAllDeploys(project) }

        assertEquals(
            "GCP App Engine API returned PERMISSION_DENIED for project ${PROJECT_ID}",
            exception.message,
        )
    }

    @Suppress("SameParameterValue")
    private fun statusCode(code: StatusCode.Code): StatusCode = object : StatusCode {
        override fun getCode(): StatusCode.Code = code
        override fun getTransportCode(): Any = ""
    }

    private fun service(id: String): com.google.appengine.v1.Service =
        com.google.appengine.v1.Service.newBuilder().setId(id).build()

    private fun version(id: String, createTime: com.google.protobuf.Timestamp, versionUrl: String?): Version {
        val builder = Version.newBuilder().setId(id)
        if (versionUrl != null) {
            builder.versionUrl = versionUrl
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