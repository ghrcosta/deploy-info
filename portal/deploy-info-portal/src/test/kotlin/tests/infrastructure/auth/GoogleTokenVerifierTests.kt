package tests.infrastructure.auth

import application.auth.VerifiedUser
import infrastructure.auth.GoogleAccessTokenVerifier
import infrastructure.auth.GoogleIdTokenVerifier
import infrastructure.auth.GoogleTokenInfoClient
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises both verifiers over a fake tokeninfo endpoint (the same stub style as
 * `GoogleTokenInfoClientTests`, but with the response shaped per scenario).
 */
private fun clientWith(body: () -> String, clock: () -> Long): GoogleTokenInfoClient =
    GoogleTokenInfoClient(
        openConnection = { url ->
            object : HttpURLConnection(url) {
                override fun usingProxy(): Boolean = false
                override fun connect() {}
                override fun disconnect() {}
                override fun getResponseCode(): Int = 200
                override fun getInputStream(): java.io.InputStream = ByteArrayInputStream(body().toByteArray())
            }
        },
        nowSeconds = clock,
    )

class GoogleIdTokenVerifierTests {

    private var clock = 1000L
    private var body = ""

    private fun verifier() = GoogleIdTokenVerifier(clientWith({ body }, { clock }), audience = "client-1", nowSeconds = { clock })

    @Test
    fun `Verifies a token for the configured audience with a verified email`() {
        body = """{"email":"user@example.com","aud":"client-1","exp":"2000000000","email_verified":"true"}"""

        assertEquals(VerifiedUser("user@example.com"), verifier().verify("some-token"))
    }

    @Test
    fun `Rejects a token for another audience`() {
        body = """{"email":"user@example.com","aud":"someone-elses-client","exp":"2000000000","email_verified":"true"}"""

        assertNull(verifier().verify("some-token"))
    }

    @Test
    fun `Rejects an expired token`() {
        body = """{"email":"user@example.com","aud":"client-1","exp":"999","email_verified":"true"}"""

        assertNull(verifier().verify("some-token"))
    }

    @Test
    fun `Rejects a token without a verified email`() {
        body = """{"email":"user@example.com","aud":"client-1","exp":"2000000000","email_verified":"false"}"""

        assertNull(verifier().verify("some-token"))
    }

    @Test
    fun `Rejects a token Google does not know`() {
        body = "{}"

        assertNull(verifier().verify("some-token"))
    }
}

class GoogleAccessTokenVerifierTests {

    private var clock = 1000L
    private var body = ""

    private fun verifier() = GoogleAccessTokenVerifier(clientWith({ body }, { clock }), nowSeconds = { clock })

    @Test
    fun `Verifies a valid gcloud access token`() {
        body = """{"email":"collector@example.com","exp":"2000000000","email_verified":true}"""

        assertEquals(VerifiedUser("collector@example.com"), verifier().verify("some-token"))
    }

    @Test
    fun `Rejects an expired access token`() {
        body = """{"email":"collector@example.com","exp":"999","email_verified":true}"""

        assertNull(verifier().verify("some-token"))
    }

    @Test
    fun `Rejects an access token without an email (a service account token)`() {
        body = """{"exp":"2000000000","email_verified":true}"""

        assertNull(verifier().verify("some-token"))
    }

    @Test
    fun `Rejects a token Google does not know`() {
        body = "{}"

        assertNull(verifier().verify("some-token"))
    }
}
