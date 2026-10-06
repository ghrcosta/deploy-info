package action

import io.github.ghrcosta.action.BucketLookup
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gradle.api.GradleException
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail
import kotlin.test.assertTrue

class BucketLookupTests {

    private val portalUrl = "https://deploy-info-portal.example.com"
    private val bucketUrl = URI.create("$portalUrl/collector/bucket").toURL()

    private fun mockConnection(
        status: Int = 200,
        body: String = """{"bucket":"my-bucket"}""",
        inputStreamError: Exception? = null,
        responseCodeError: Exception? = null,
    ): HttpURLConnection {
        val connection = mockk<HttpURLConnection>(relaxed = true)
        if (responseCodeError != null) {
            every { connection.responseCode } throws responseCodeError
        } else {
            every { connection.responseCode } returns status
            if (inputStreamError != null) {
                every { connection.inputStream } throws inputStreamError
            } else {
                every { connection.inputStream } returns ByteArrayInputStream(body.toByteArray())
            }
        }
        return connection
    }

    private fun lookupWith(connection: HttpURLConnection): BucketLookup =
        BucketLookup(portalUrl) { connection }

    @Test
    fun `Returns the bucket parsed from the portal response`() {
        val connection = mockConnection(body = """{"bucket":"my-bucket-name"}""")

        assertEquals("my-bucket-name", lookupWith(connection).execute())

        verify {
            connection.requestMethod = "GET"
            connection.connectTimeout = any()
            connection.readTimeout = any()
            connection.disconnect()
        }
    }

    @Test
    fun `Trims a trailing slash from the portal URL`() {
        val connection = mockConnection()
        var requestedUrl: URL? = null

        BucketLookup("$portalUrl/") { requestedUrl = it; connection }.execute()

        assertEquals(bucketUrl, requestedUrl)
    }

    @Test
    fun `Rejects an invalid portal URL`() {
        val exception = assertFailsWith<GradleException> {
            BucketLookup("::not a url::") { _ -> fail("openConnection must not be called for an invalid URL") }.execute()
        }
        assertTrue(exception.message!!.contains("'portalUrl' is not a valid URL"))
    }

    @Test
    fun `HTTP error status fails with an exception naming the portal URL`() {
        val exception = assertFailsWith<GradleException> {
            lookupWith(mockConnection(status = 500)).execute()
        }
        assertTrue(exception.message!!.contains(bucketUrl.toString()))
        assertTrue(exception.message!!.contains("500"))
    }

    @Test
    fun `Malformed response body fails with an exception naming the portal URL`() {
        val exception = assertFailsWith<GradleException> {
            lookupWith(mockConnection(body = """{"notTheBucket":"oops"}""")).execute()
        }
        assertTrue(exception.message!!.contains(bucketUrl.toString()))
        assertTrue(exception.message!!.contains("malformed"))
    }

    @Test
    fun `Blank bucket field is treated as a malformed response`() {
        val exception = assertFailsWith<GradleException> {
            lookupWith(mockConnection(body = """{"bucket":""}""")).execute()
        }
        assertTrue(exception.message!!.contains("malformed"))
    }

    @Test
    fun `Connection failure or timeout fails with an exception naming the portal URL`() {
        val exception = assertFailsWith<GradleException> {
            lookupWith(mockConnection(responseCodeError = SocketTimeoutException("timed out"))).execute()
        }
        assertTrue(exception.message!!.contains(bucketUrl.toString()))
        assertTrue(exception.message!!.contains("Could not reach the portal"))
    }

    @Test
    fun `Unreadable response body fails with an exception naming the portal URL`() {
        val exception = assertFailsWith<GradleException> {
            lookupWith(mockConnection(inputStreamError = SocketTimeoutException("read timed out"))).execute()
        }
        assertTrue(exception.message!!.contains(bucketUrl.toString()))
        assertTrue(exception.message!!.contains("Could not read"))
    }
}
