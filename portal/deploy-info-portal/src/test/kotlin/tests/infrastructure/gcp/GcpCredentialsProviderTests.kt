package tests.infrastructure.gcp

import com.google.api.client.http.HttpTransport
import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.http.LowLevelHttpResponse
import com.google.auth.http.HttpTransportFactory
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ImpersonatedCredentials
import domain.CredentialsErrorCategory
import domain.CredentialsException
import domain.Project
import infrastructure.gcp.GcpCredentialsProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val SUCCESS_BODY =
    """{"accessToken":"token-1","expireTime":"2036-01-01T00:00:00Z"}"""

/**
 * Fake [HttpTransportFactory] recording every request URL and returning a canned response — keeps
 * the tests hermetic (no GCP credentials, no network).
 */
private class FakeTransportFactory : HttpTransportFactory {

    val requestUrls = mutableListOf<String>()
    var statusCode: Int = 200
    var body: String = SUCCESS_BODY

    override fun create(): HttpTransport = object : HttpTransport() {
        override fun buildRequest(method: String, url: String): LowLevelHttpRequest {
            requestUrls += url
            return object : LowLevelHttpRequest() {
                override fun addHeader(name: String, value: String) {}
                override fun execute(): LowLevelHttpResponse = FakeResponse(statusCode, body)
            }
        }
    }

    private inner class FakeResponse(private val status: Int, private val payload: String) : LowLevelHttpResponse() {
        override fun getContent(): InputStream = ByteArrayInputStream(payload.toByteArray())
        override fun getContentEncoding(): String? = null
        override fun getContentLength(): Long = payload.length.toLong()
        override fun getContentType(): String = "application/json"
        override fun getStatusLine(): String = "HTTP/1.1"
        override fun getStatusCode(): Int = status
        override fun getReasonPhrase(): String = ""
        override fun getHeaderCount(): Int = 0
        override fun getHeaderName(index: Int): String = ""
        override fun getHeaderValue(index: Int): String = ""
    }
}

private fun portalCredentials(): GoogleCredentials = GoogleCredentials.create(
    AccessToken("source-token", Date(System.currentTimeMillis() + 3_600_000)),
)

class GcpCredentialsProviderTests {

    private lateinit var transportFactory: FakeTransportFactory

    @BeforeEach
    fun setup() {
        transportFactory = FakeTransportFactory()
    }

    private fun provider(callerCredentials: () -> GoogleCredentials = ::portalCredentials) =
        GcpCredentialsProvider(callerCredentials, transportFactory)

    @Test
    fun `Return impersonated credentials for the project's service account`() {
        val project = Project(name = "proj", group = null, serviceAccount = "sa@proj.iam.gserviceaccount.com")

        val credentials = provider().credentialsFor(project) as ImpersonatedCredentials

        assertEquals("sa@proj.iam.gserviceaccount.com", credentials.account)
        assertEquals(
            listOf(
                "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/" +
                    "sa@proj.iam.gserviceaccount.com:generateAccessToken",
            ),
            transportFactory.requestUrls,
        )
    }

    @Test
    fun `Reuse the impersonated credentials of a service account across calls`() {
        val project = Project(name = "proj", group = null, serviceAccount = "sa@proj.iam.gserviceaccount.com")
        val other = Project(name = "proj", group = null, serviceAccount = "other@proj.iam.gserviceaccount.com")
        val credentialsProvider = provider()

        val credentials = credentialsProvider.credentialsFor(project)
        val sameCredentialsAgain = credentialsProvider.credentialsFor(project)
        val credentialsOfOtherAccount = credentialsProvider.credentialsFor(other)

        assertEquals(credentials, sameCredentialsAgain)
        assertEquals(
            "other@proj.iam.gserviceaccount.com",
            (credentialsOfOtherAccount as ImpersonatedCredentials).account,
        )
        // One impersonation call per distinct service account, none repeated for the same one.
        assertEquals(2, transportFactory.requestUrls.size)
    }

    @Test
    fun `Report a permission problem as a service account misconfiguration`() {
        transportFactory.statusCode = 403
        transportFactory.body = """{"error":{"code":403,"message":"Permission denied"}}"""
        val project = Project(name = "proj", group = null, serviceAccount = "sa@proj.iam.gserviceaccount.com")

        val exception = assertFailsWith<CredentialsException> { provider().credentialsFor(project) }

        assertEquals(CredentialsErrorCategory.SERVICE_ACCOUNT_MISCONFIGURATION, exception.category)
    }

    @Test
    fun `Report a server error as a portal issue`() {
        transportFactory.statusCode = 500
        val project = Project(name = "proj", group = null, serviceAccount = "sa@proj.iam.gserviceaccount.com")

        val exception = assertFailsWith<CredentialsException> { provider().credentialsFor(project) }

        assertEquals(CredentialsErrorCategory.PORTAL_ISSUE, exception.category)
    }

    @Test
    fun `Report missing portal credentials as a portal issue`() {
        val project = Project(name = "proj", group = null, serviceAccount = "sa@proj.iam.gserviceaccount.com")

        val exception = assertFailsWith<CredentialsException> {
            provider(callerCredentials = { throw IllegalStateException("Your default credentials were not found") })
                .credentialsFor(project)
        }

        assertEquals(CredentialsErrorCategory.PORTAL_ISSUE, exception.category)
    }
}