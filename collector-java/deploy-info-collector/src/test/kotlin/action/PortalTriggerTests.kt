package action

import com.google.gson.Gson
import io.github.ghrcosta.action.PortalTrigger
import io.github.ghrcosta.action.TriggerRequest
import io.github.ghrcosta.action.TriggerResponse
import io.github.ghrcosta.util.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class PortalTriggerTests {

    private val gson = Gson()
    private val portalUrl = "https://deploy-info-portal.example.com"
    private val triggerUrl = URI.create("${portalUrl}/collector/handleNewDirectory").toURL()
    private val directoryName = "jdoe_GAE_1735689600000"
    private val projects = listOf("proj-prod", "proj-dev")
    private val deployType = "GAE"
    private val userEmail = "jdoe@example.com"

    @BeforeTest
    fun setup() {
        // PortalTrigger logs via the static Logger; init it with a relaxed mock (no backend involved).
        Logger.init(mockk<Project>(relaxed = true))
    }

    private class ConnectionFixture(
        val connection: HttpURLConnection,
        val writtenBody: ByteArrayOutputStream = ByteArrayOutputStream(),
    )

    private fun mockConnection(
        status: Int = 200,
        body: String = gson.toJson(TriggerResponse(TriggerResponse.STATUS_CREATED, "proj-dev", "web", "v42")),
        inputStreamError: Exception? = null,
        responseCodeError: Exception? = null,
        outputError: Exception? = null,
    ): ConnectionFixture {
        val writtenBody = ByteArrayOutputStream()
        val connection = mockk<HttpURLConnection>(relaxed = true)
        every { connection.outputStream } answers {
            outputError?.let { throw it }
            writtenBody
        }
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
        return ConnectionFixture(connection, writtenBody)
    }

    private fun triggerWith(connection: HttpURLConnection): PortalTrigger =
        PortalTrigger(portalUrl, directoryName, projects, deployType, userEmail) { connection }

    @Test
    fun `Posts the contract request body for a created link`() {
        val fixture = mockConnection()

        triggerWith(fixture.connection).execute()

        val sent = gson.fromJson(fixture.writtenBody.toString(), TriggerRequest::class.java)
        assertEquals(TriggerRequest(directoryName, projects, deployType, userEmail), sent)
        verify {
            fixture.connection.requestMethod = "POST"
            fixture.connection.doOutput = true
            fixture.connection.connectTimeout = any()
            fixture.connection.readTimeout = any()
            fixture.connection.disconnect()
        }
    }

    @Test
    fun `Special characters in the request survive the JSON round trip`() {
        val fixture = mockConnection()
        val trickyProjects = listOf("p\"1", "p\\2", "p\n3")
        val trickyEmail = """weird\name"@example.com"""

        PortalTrigger(portalUrl, directoryName, trickyProjects, deployType, trickyEmail) { fixture.connection }
            .execute()

        val sent = gson.fromJson(fixture.writtenBody.toString(), TriggerRequest::class.java)
        assertEquals(TriggerRequest(directoryName, trickyProjects, deployType, trickyEmail), sent)
    }

    @Test
    fun `Trims a trailing slash from the portal URL`() {
        val fixture = mockConnection()
        var requestedUrl: URL? = null

        PortalTrigger("${portalUrl}/", directoryName, projects, deployType, userEmail) {
            requestedUrl = it; fixture.connection
        }.execute()

        assertEquals(triggerUrl, requestedUrl)
    }

    @Test
    fun `already-linked and pending responses are accepted without failing the build`() {
        val alreadyLinked = mockConnection(
            body = gson.toJson(TriggerResponse(TriggerResponse.STATUS_ALREADY_LINKED, "p", "s", "v")),
        )
        triggerWith(alreadyLinked.connection).execute()
        verify { alreadyLinked.connection.disconnect() }

        val pending = mockConnection(status = 202, body = gson.toJson(TriggerResponse(TriggerResponse.STATUS_PENDING)))
        triggerWith(pending.connection).execute()
        verify { pending.connection.disconnect() }
    }

    @Test
    fun `HTTP 422 unknown-project fails with a message naming the projects`() {
        val fixture = mockConnection(status = 422, body = gson.toJson(TriggerResponse("unknown-project")))

        val exception = assertFailsWith<GradleException> {
            triggerWith(fixture.connection).execute()
        }
        assertTrue(exception.message!!.contains("none of the listed projects"))
        assertTrue(exception.message!!.contains("proj-prod"))
    }

    @Test
    fun `HTTP 400 or any other HTTP error status fails with an exception naming the portal URL`() {
        listOf(HttpURLConnection.HTTP_BAD_REQUEST, 500).forEach { status ->
            val fixture = mockConnection(status = status, body = "")
            val exception = assertFailsWith<GradleException> {
                triggerWith(fixture.connection).execute()
            }
            assertTrue(exception.message!!.contains(triggerUrl.toString()))
            assertTrue(exception.message!!.contains(status.toString()))
        }
    }

    @Test
    fun `Malformed response body fails with an exception naming the portal URL`() {
        val fixture = mockConnection(body = """{"status": <not valid json>""")

        val exception = assertFailsWith<GradleException> {
            triggerWith(fixture.connection).execute()
        }
        assertTrue(exception.message!!.contains(triggerUrl.toString()))
        assertTrue(exception.message!!.contains("malformed"))
    }

    @Test
    fun `Unexpected status field fails with an exception`() {
        val fixture = mockConnection(body = gson.toJson(TriggerResponse("something-else")))

        val exception = assertFailsWith<GradleException> {
            triggerWith(fixture.connection).execute()
        }
        assertTrue(exception.message!!.contains("something-else"))
    }

    @Test
    fun `Connection failure or timeout fails with an exception naming the portal URL`() {
        val fixture = mockConnection(responseCodeError = SocketTimeoutException("timed out"))

        val exception = assertFailsWith<GradleException> {
            triggerWith(fixture.connection).execute()
        }
        assertTrue(exception.message!!.contains(triggerUrl.toString()))
        assertTrue(exception.message!!.contains("Could not reach the portal"))
    }

    @Test
    fun `Unreadable response body fails with an exception naming the portal URL`() {
        val fixture = mockConnection(inputStreamError = SocketTimeoutException("read timed out"))

        val exception = assertFailsWith<GradleException> {
            triggerWith(fixture.connection).execute()
        }
        assertTrue(exception.message!!.contains(triggerUrl.toString()))
        assertTrue(exception.message!!.contains("Could not read"))
    }

    @Test
    fun `Unwritable request body fails with an exception naming the portal URL`() {
        val fixture = mockConnection(outputError = SocketTimeoutException("write timed out"))

        val exception = assertFailsWith<GradleException> {
            triggerWith(fixture.connection).execute()
        }
        assertTrue(exception.message!!.contains(triggerUrl.toString()))
        assertTrue(exception.message!!.contains("Could not send"))
    }

    @Test
    fun `Rejects an invalid portal URL`() {
        val exception = assertFailsWith<GradleException> {
            PortalTrigger("::not a url::", directoryName, projects, deployType, userEmail) { _ ->
                fail("openConnection must not be called for an invalid URL")
            }.execute()
        }
        assertTrue(exception.message!!.contains("'portalUrl' is not a valid URL"))
    }
}

