package infrastructure.auth

import org.slf4j.LoggerFactory
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** Which kind of Google token is being asked about — the tokeninfo query parameter differs. */
enum class TokenInfoKind(val queryParameter: String) {
    ID_TOKEN("id_token"),
    ACCESS_TOKEN("access_token"),
}

/**
 * Asks Google's tokeninfo endpoint about a token. Both Google-issued token kinds the portal accepts
 * are verified this way: OpenID Connect ID tokens (the frontend's sign-in credential) and OAuth
 * access tokens (the collector's `gcloud auth print-access-token` credential) — Google validates
 * the token's signature and returns its metadata.
 *
 * Responses are cached per token for [GoogleTokenInfoClient.Companion.CACHE_TTL_SECONDS], because
 * verification is a network round-trip to Google and every API request carries the token again.
 * The cache is keyed by the token itself — held in memory only, never logged.
 */
class GoogleTokenInfoClient(
    /** Seam for tests: how an [HttpURLConnection] is opened for the tokeninfo endpoint. */
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    /** Overridable for tests; null uses the system clock. */
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {

    /** The tokeninfo metadata the verifiers judge. [expiresAt] is epoch seconds, when present. */
    data class TokenInfo(
        val email: String?,
        val audience: String?,
        val expiresAt: Long?,
        val emailVerified: Boolean?,
    )

    /** Returns the token's info, or null when Google does not know the token (invalid or expired). */
    fun fetch(token: String, kind: TokenInfoKind): TokenInfo? {
        cache[token]?.takeIf { it.validUntil > nowSeconds() }?.let { return it.info }
        val info = fetchFromGoogle(token, kind)
        if (info != null) {
            cache[token] = CacheEntry(info, nowSeconds() + CACHE_TTL_SECONDS)
        } else {
            cache.remove(token)
        }
        return info
    }

    private fun fetchFromGoogle(token: String, kind: TokenInfoKind): TokenInfo? =
        try {
            val url =
                URI.create("$TOKENINFO_URL?${kind.queryParameter}=${URLEncoder.encode(token, Charsets.UTF_8)}").toURL()
            val connection = openConnection(url).apply {
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
            }
            try {
                val status = connection.responseCode
                if (status != HTTP_OK) {
                    logger.debug("Google tokeninfo returned HTTP {} for a token", status)
                    return null
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                return parse(body)
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            // A transient failure to reach Google is reported like an unknown token: the request is
            // rejected, and the caller can simply retry.
            logger.warn("Could not reach the Google tokeninfo endpoint: {}", e.message)
            null
        }
    private fun parse(body: String): TokenInfo? =
        try {
            val dto = jsonMapper.readValue(body, TokenInfoDto::class.java)
            TokenInfo(
                email = dto.email,
                audience = dto.aud,
                expiresAt = dto.exp?.toLongOrNull(),
                emailVerified = dto.email_verified?.equals("true", ignoreCase = true),
            )
        } catch (e: Exception) {
            logger.warn("Could not parse the Google tokeninfo response: {}", e.message)
            null
        }

    /**
     * The tokeninfo response carries its fields as JSON strings (even `exp` and the boolean
     * `email_verified`), so everything is mapped as a string and converted afterward.
     */
    private data class TokenInfoDto(
        val email: String? = null,
        val aud: String? = null,
        val exp: String? = null,
        val email_verified: String? = null,
    )

    private data class CacheEntry(val info: TokenInfo, val validUntil: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private companion object {
        private val logger = LoggerFactory.getLogger(GoogleTokenInfoClient::class.java)
        private val jsonMapper = jacksonObjectMapper()

        /** Never log a token; the debug messages name only the outcome. */
        const val TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo"
        const val HTTP_OK = 200
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 10_000
        const val CACHE_TTL_SECONDS = 300L
    }
}
