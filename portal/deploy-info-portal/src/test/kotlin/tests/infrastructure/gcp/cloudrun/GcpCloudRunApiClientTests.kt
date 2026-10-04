package tests.infrastructure.gcp.cloudrun

import domain.GcpListingException
import domain.Project
import infrastructure.gcp.AccessTokenProvider
import infrastructure.gcp.cloudrun.GcpCloudRunApiClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseActions
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

private const val BASE_URL = "https://run.googleapis.com"

private const val WEB_API_SERVICE = "projects/testProject/locations/europe-west1/services/web-api"
private const val WEB_API_REV_1 = "${WEB_API_SERVICE}/revisions/rev-1"
private const val WEB_API_REV_2 = "${WEB_API_SERVICE}/revisions/rev-2"
private const val WORKER_REV_3 = "projects/testProject/locations/us-central1/services/worker/revisions/rev-3"

class GcpCloudRunApiClientTests {

    private val project = Project(name = "testProject", group = "test", serviceAccount = "test@account.com")

    private lateinit var restTemplate: RestTemplate
    private lateinit var mockServer: MockRestServiceServer
    private lateinit var client: GcpCloudRunApiClient

    @BeforeEach
    fun setup() {
        restTemplate = RestTemplate()
        mockServer = MockRestServiceServer.bindTo(restTemplate).build()
        client = GcpCloudRunApiClient(restTemplate, AccessTokenProvider { "test-token" })
    }

    @Test
    fun `Return all revisions of all services in all locations sorted newest first`() {
        expectServicesRequest().andRespond(
            withSuccess(
                """{"services":[
                    {"name":"${WEB_API_SERVICE}","uri":"https://web-api.example.com"},
                    {"name":"projects/testProject/locations/us-central1/services/worker"}
                ]}""",
                MediaType.APPLICATION_JSON,
            )
        )
        expectRevisionsRequest("europe-west1", "web-api").andRespond(
            withSuccess(
                """{"revisions":[
                    {"name":"${WEB_API_REV_1}","createTime":"2026-01-01T10:00:00Z"},
                    {"name":"${WEB_API_REV_2}","createTime":"2026-01-02T10:00:00Z"}
                ]}""",
                MediaType.APPLICATION_JSON,
            )
        )
        expectRevisionsRequest("us-central1", "worker").andRespond(
            withSuccess(
                """{"revisions":[{"name":"${WORKER_REV_3}","createTime":"2026-01-03T10:00:00Z"}]}""",
                MediaType.APPLICATION_JSON,
            )
        )

        val deploys = client.listAllDeploys(project)

        assertEquals(
            listOf("us-central1/worker/rev-3", "europe-west1/web-api/rev-2", "europe-west1/web-api/rev-1"),
            deploys.map { "${it.location}/${it.serviceId}/${it.revisionId}" },
        )
        assertEquals(
            listOf(null, "https://web-api.example.com", "https://web-api.example.com"),
            deploys.map { it.url },
        )
        assertEquals(
            listOf("2026-01-03", "2026-01-02", "2026-01-01"),
            deploys.map { it.createTime.toString().substring(0, 10) },
        )
        mockServer.verify()
    }

    @Test
    fun `Follow nextPageToken until all pages are fetched`() {
        expectServicesRequest().andRespond(
            withSuccess(
                """{"services":[{"name":"${WEB_API_SERVICE}"}],"nextPageToken":"svc-page-2"}""",
                MediaType.APPLICATION_JSON,
            )
        )
        expectServicesRequest(pageToken = "svc-page-2")
            .andRespond(withSuccess("""{"services":[]}""", MediaType.APPLICATION_JSON))
        expectRevisionsRequest("europe-west1", "web-api").andRespond(
            withSuccess(
                """{"revisions":[{"name":"${WEB_API_REV_1}","createTime":"2026-01-01T10:00:00Z"}],
                    "nextPageToken":"rev-page-2"}""",
                MediaType.APPLICATION_JSON,
            )
        )
        expectRevisionsRequest("europe-west1", "web-api", pageToken = "rev-page-2").andRespond(
            withSuccess(
                """{"revisions":[{"name":"${WEB_API_REV_2}","createTime":"2026-01-02T10:00:00Z"}]}""",
                MediaType.APPLICATION_JSON,
            )
        )

        val deploys = client.listAllDeploys(project)

        assertEquals(listOf("rev-2", "rev-1"), deploys.map { it.revisionId })
        mockServer.verify()
    }

    @Test
    fun `Return empty list when the project has no services`() {
        expectServicesRequest().andRespond(withSuccess("""{"services":[]}""", MediaType.APPLICATION_JSON))

        val deploys = client.listAllDeploys(project)

        assertTrue(deploys.isEmpty())
        mockServer.verify()
    }

    @Test
    fun `Send the access token as a bearer authorization header`() {
        expectServicesRequest()
            .andExpect(header("Authorization", "Bearer test-token"))
            .andRespond(withSuccess("""{"services":[]}""", MediaType.APPLICATION_JSON))

        client.listAllDeploys(project)

        mockServer.verify()
    }

    @Test
    fun `Throw GcpListingException when the API returns an error status`() {
        expectServicesRequest()
            .andRespond(withStatus(HttpStatus.FORBIDDEN).body("""{"error":{"message":"no permission"}}"""))

        val exception = assertFailsWith<GcpListingException> { client.listAllDeploys(project) }

        assertTrue(exception.message!!.contains("FORBIDDEN"))
        assertTrue(exception.message!!.contains("testProject"))
    }


    @Test
    fun `Throw GcpListingException when the API cannot be reached`() {
        expectServicesRequest().andRespond(withException(IOException("connection refused")))

        val exception = assertFailsWith<GcpListingException> { client.listAllDeploys(project) }

        assertTrue(exception.message!!.contains("Could not call"))
    }

    @Test
    fun `Throw GcpListingException when a revision has an unparsable createTime`() {
        expectServicesRequest()
            .andRespond(withSuccess("""{"services":[{"name":"${WEB_API_SERVICE}"}]}""", MediaType.APPLICATION_JSON))
        expectRevisionsRequest("europe-west1", "web-api").andRespond(
            withSuccess(
                """{"revisions":[{"name":"${WEB_API_REV_1}","createTime":"not-a-date"}]}""",
                MediaType.APPLICATION_JSON,
            )
        )

        val exception = assertFailsWith<GcpListingException> { client.listAllDeploys(project) }

        assertTrue(exception.message!!.contains("unparsable createTime"))
    }

    @Test
    fun `Throw GcpListingException when the API response is unparsable`() {
        expectServicesRequest().andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON))

        val exception = assertFailsWith<GcpListingException> { client.listAllDeploys(project) }

        assertTrue(exception.message!!.contains("Could not parse"))
    }

    @Test
    fun `Throw GcpListingException when a service resource name is unexpected`() {
        expectServicesRequest().andRespond(
            withSuccess("""{"services":[{"name":"malformed-resource-name"}]}""", MediaType.APPLICATION_JSON)
        )

        val exception = assertFailsWith<GcpListingException> { client.listAllDeploys(project) }

        assertTrue(exception.message!!.contains("unexpected name"))
    }

    private fun expectServicesRequest(pageToken: String? = null): ResponseActions {
        val servicesUrl = "${BASE_URL}/v2/projects/testProject/locations/-/services?pageSize=100"
        val expectedUrl = if (pageToken == null) servicesUrl else "${servicesUrl}&pageToken=${pageToken}"
        return mockServer.expect(requestTo(expectedUrl)).andExpect(method(HttpMethod.GET))
    }

    private fun expectRevisionsRequest(
        location: String,
        serviceId: String,
        pageToken: String? = null,
    ): ResponseActions {
        val revisionsUrl =
            "${BASE_URL}/v2/projects/testProject/locations/${location}/services/${serviceId}/revisions?pageSize=100"
        val expectedUrl = if (pageToken == null) revisionsUrl else "${revisionsUrl}&pageToken=${pageToken}"
        return mockServer.expect(requestTo(expectedUrl)).andExpect(method(HttpMethod.GET))
    }
}

