package tests.infrastructure.auth

import infrastructure.auth.GoogleTokenInfoClient
import infrastructure.auth.TokenInfoKind
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A fake [HttpURLConnection] answering with a canned status/body, recording its own URL. */
private class StubConnection(
    val requestUrl: URL,
    private val status: Int,
    private val body: String,
) : HttpURLConnection(requestUrl) {

    var disconnects = 0

    override fun usingProxy(): Boolean = false
    override fun connect() {}
    override fun disconnect() {
        disconnects++
    }

    override fun getResponseCode(): Int = status
    override fun getInputStream(): java.io.InputStream = ByteArrayInputStream(body.toByteArray())
}

class GoogleTokenInfoClientTests {

    private var clock = 1000L
    private val connections = mutableListOf<StubConnection>()

    private fun client(status: Int = 200, body: String): GoogleTokenInfoClient {
        val connectionsRef = connections
        return GoogleTokenInfoClient(
            openConnection = { url ->
                StubConnection(url, status, body).also { connectionsRef += it }
            },
            nowSeconds = { clock },
        )
    }

    @Test
    fun `Parses the tokeninfo response of an ID token`() {
        val body = """{"iss":"https://accounts.google.com","aud":"client-1","email":"user@example.com",""" +
            """"exp":"2000000000","email_verified":"true"}"""

        val info = client(body = body).fetch("some-token", TokenInfoKind.ID_TOKEN)

        assertEquals("user@example.com", info?.email)
        assertEquals("client-1", info?.audience)
        assertEquals(2000000000L, info?.expiresAt)
        assertEquals(true, info?.emailVerified)
    }

    @Test
    fun `Parses the tokeninfo response of an access token`() {
        val body = """{"aud":"https://oauth2.googleapis.com/","scope":"https://www.googleapis.com/auth/cloud-platform",""" +
            """"exp":"2000000000","expires_in":"3599","email":"collector@example.com","email_verified":true}"""

        val info = client(body = body).fetch("some-token", TokenInfoKind.ACCESS_TOKEN)

        assertEquals("collector@example.com", info?.email)
        assertEquals(true, info?.emailVerified)
    }

    @Test
    fun `Asks the endpoint with the right query parameter`() {
        client(body = "{}").fetch("some-token", TokenInfoKind.ID_TOKEN)

        assertTrue(connections.single().requestUrl.toString().endsWith("tokeninfo?id_token=some-token"))
    }

    @Test
    fun `An unknown token (HTTP 400) yields null`() {
        val info = client(status = 400, body = """{"error_description":"Invalid Value"}""")
            .fetch("some-token", TokenInfoKind.ID_TOKEN)

        assertNull(info)
    }

    @Test
    fun `A malformed body yields null`() {
        val info = client(body = "not json").fetch("some-token", TokenInfoKind.ID_TOKEN)

        assertNull(info)
    }

    @Test
    fun `A connection failure yields null`() {
        val failingClient = GoogleTokenInfoClient(
            openConnection = { throw java.io.IOException("unreachable") },
            nowSeconds = { clock },
        )

        assertNull(failingClient.fetch("some-token", TokenInfoKind.ID_TOKEN))
    }

    @Test
    fun `Repeating a fetch within the TTL reuses the cached answer`() {
        val tokenInfoClient = client(status = 200, body = """{"email":"user@example.com"}""")

        tokenInfoClient.fetch("some-token", TokenInfoKind.ID_TOKEN)
        tokenInfoClient.fetch("some-token", TokenInfoKind.ID_TOKEN)

        assertEquals(1, connections.size)
    }

    @Test
    fun `After the TTL the answer is fetched again`() {
        val tokenInfoClient = client(status = 200, body = """{"email":"user@example.com"}""")

        tokenInfoClient.fetch("some-token", TokenInfoKind.ID_TOKEN)
        clock += 301
        tokenInfoClient.fetch("some-token", TokenInfoKind.ID_TOKEN)

        assertEquals(2, connections.size)
    }

    @Test
    fun `An unknown token is not cached`() {
        val tokenInfoClient = client(status = 400, body = "{}")

        tokenInfoClient.fetch("some-token", TokenInfoKind.ID_TOKEN)
        tokenInfoClient.fetch("some-token", TokenInfoKind.ID_TOKEN)

        assertEquals(2, connections.size)
    }

    @Test
    fun `Disconnects the connection`() {
        client(body = "{}").fetch("some-token", TokenInfoKind.ID_TOKEN)

        assertEquals(1, connections.single().disconnects)
    }
}
