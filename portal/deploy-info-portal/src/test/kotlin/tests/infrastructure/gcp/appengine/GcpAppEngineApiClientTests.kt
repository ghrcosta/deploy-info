package tests.infrastructure.gcp.appengine

import domain.GcpListingException
import domain.Project
import infrastructure.gcp.AccessTokenProvider
import infrastructure.gcp.appengine.GcpAppEngineApiClient
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
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

private const val BASE_URL = "https://appengine.googleapis.com"

class GcpAppEngineApiClientTests {

    private val project = Project(name = "testProject", group = "test", serviceAccount = "test@account.com")

    private lateinit var restTemplate: RestTemplate
    private lateinit var mockServer: MockRestServiceServer
    private lateinit var client: GcpAppEngineApiClient

    @BeforeEach
    fun setup() {
        restTemplate = RestTemplate()
        mockServer = MockRestServiceServer.bindTo(restTemplate).build()
        client = GcpAppEngineApiClient(restTemplate, AccessTokenProvider { "test-token" })
    }

    @Test
    fun `Return all versions of all services sorted newest first`() {
        expectServicesRequest()
            .andRespond(withSuccess("""{"services":[{"id":"default"},{"id":"worker"}]}""", MediaType.APPLICATION_JSON))
        expectVersionsRequest("default").andRespond(
            withSuccess(
                """{"versions":[
                    {"id":"v1","createTime":"2026-01-01T10:00:00Z","versionUrl":"https://v1.example.com"},
                    {"id":"v2","createTime":"2026-01-02T10:00:00Z"}
                ]}""",
                MediaType.APPLICATION_JSON,
            )
        )
        expectVersionsRequest("worker").andRespond(
            withSuccess(
                """{"versions":[{"id":"v3","createTime":"2026-01-03T10:00:00Z"}]}""",
                MediaType.APPLICATION_JSON,
            )
        )

        val deploys = client.listAllDeploys(project)

        assertEquals(
            listOf("worker/v3", "default/v2", "default/v1"),
            deploys.map { "${it.serviceId}/${it.versionId}" },
        )
        assertEquals(listOf(null, null, "https://v1.example.com"), deploys.map { it.url })
        assertEquals(
            listOf("2026-01-03", "2026-01-02", "2026-01-01"),
            deploys.map { it.createTime.toString().substring(0, 10) },
        )
        mockServer.verify()
    }

    @Test
    fun `Follow nextPageToken until all pages are fetched`() {
        expectServicesRequest()
            .andRespond(withSuccess("""{"services":[{"id":"default"}]}""", MediaType.APPLICATION_JSON))
        expectVersionsRequest("default").andRespond(
            withSuccess(
                """{"versions":[{"id":"v1","createTime":"2026-01-01T10:00:00Z"}],"nextPageToken":"page-2"}""",
                MediaType.APPLICATION_JSON,
            )
        )
        expectVersionsRequest("default", pageToken = "page-2").andRespond(
            withSuccess(
                """{"versions":[{"id":"v2","createTime":"2026-01-02T10:00:00Z"}]}""",
                MediaType.APPLICATION_JSON,
            )
        )

        val deploys = client.listAllDeploys(project)

        assertEquals(listOf("v2", "v1"), deploys.map { it.versionId })
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
    fun `Throw GcpListingException when a version has an unparsable createTime`() {
        expectServicesRequest()
            .andRespond(withSuccess("""{"services":[{"id":"default"}]}""", MediaType.APPLICATION_JSON))
        expectVersionsRequest("default").andRespond(
            withSuccess("""{"versions":[{"id":"v1","createTime":"not-a-date"}]}""", MediaType.APPLICATION_JSON)
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

    private fun expectServicesRequest() =
        mockServer.expect(requestTo("${BASE_URL}/v1/apps/testProject/services?pageSize=100"))
            .andExpect(method(HttpMethod.GET))

    private fun expectVersionsRequest(serviceId: String, pageToken: String? = null): ResponseActions {
        val versionsUrl = "${BASE_URL}/v1/apps/testProject/services/${serviceId}/versions?pageSize=100"
        val expectedUrl = if (pageToken == null) versionsUrl else "${versionsUrl}&pageToken=${pageToken}"
        return mockServer.expect(requestTo(expectedUrl)).andExpect(method(HttpMethod.GET))
    }
}

