package io.github.ghrcosta.action

import org.gradle.api.GradleException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Resolves the Cloud Storage bucket the collector uploads go to by asking the portal.
 *
 * Calls `GET {portalUrl}/collector/bucket` (see `documentation/api.md`) and parses the `bucket`
 * field from the JSON response. Implemented with plain `java.net.HttpURLConnection` so that no
 * HTTP client dependency is added to the published plugin jar.
 */
class BucketLookup(
    private val portalUrl: String,
    /** Seam for tests: how an [HttpURLConnection] is opened for a given [URL]. */
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {

    fun execute(): String {
        val url = toUrl()
        val connection = openConnection(url).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
        }

        try {
            val status = try {
                connection.responseCode
            } catch (e: IOException) {
                throw GradleException("Could not reach the portal at $url to resolve the upload bucket: ${e.message}", e)
            }
            if (status != HTTP_OK) {
                throw GradleException("The portal at $url returned HTTP $status when resolving the upload bucket.")
            }

            val body = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                throw GradleException("Could not read the bucket-lookup response from the portal at $url: ${e.message}", e)
            }

            return parseBucket(body)
                ?: throw GradleException(
                    "The portal at $url returned a malformed bucket-lookup response (no 'bucket' field): $body"
                )
        } finally {
            connection.disconnect()
        }
    }

    private fun toUrl(): URL =
        try {
            URI.create("${portalUrl.trimEnd('/')}/collector/bucket").toURL()
        } catch (e: Exception) {
            throw GradleException("'portalUrl' is not a valid URL: '$portalUrl' (${e.message})", e)
        }

    /** Extracts the `bucket` field from the JSON response without pulling in a JSON library. */
    private fun parseBucket(body: String): String? =
        BUCKET_FIELD_REGEX.find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    private companion object {
        const val HTTP_OK = 200
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 30_000
        val BUCKET_FIELD_REGEX = "\"bucket\"\\s*:\\s*\"([^\"]*)\"".toRegex()
    }
}