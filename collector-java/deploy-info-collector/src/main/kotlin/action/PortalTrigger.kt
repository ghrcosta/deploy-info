package io.github.ghrcosta.action

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.github.ghrcosta.util.Logger
import org.gradle.api.GradleException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Notifies the portal that a collection output directory has been uploaded to the bucket, so it can
 * link the upload to the GCP deploy it was built for.
 *
 * Calls `POST {portalUrl}/collector/handleNewDirectory` (see `documentation/api.md`) with a
 * [TriggerRequest] JSON body, implemented with plain `java.net.HttpURLConnection` so that no HTTP
 * client dependency is added to the published plugin jar, mirroring [BucketLookup]. Gson is used to
 * serialize the request and to parse the response.
 *
 * The request carries a gcloud access token ([accessToken]) as the `Authorization: Bearer`
 * credential, so the portal can verify the caller is a user present in the IAM of its project
 * (Phase 2.7 authentication). The portal owns the matching logic and its 5-minute retry: a
 * `pending` response means the portal may still link the upload (or delete its folder) later, so
 * it is logged but does not fail the build. Any other unexpected outcome does fail the build.
 */
class PortalTrigger(
    /** Base URL for to communicate with the backend. */
    private val portalUrl: String,
    /** Name of the uploaded output directory, `<user>_<deployType>_<epochMillis>`. */
    private val directoryName: String,
    /** Candidate GCP projects the code may have been deployed to. */
    private val projects: List<String>,
    /** `GAE` or `RUN`. */
    private val deployType: String,
    /** The gcloud account that performed the deploy and the collection. */
    private val userEmail: String,
    /**
     * gcloud access token sent as the `Authorization: Bearer` credential, or null when none could
     * be obtained (a portal that requires authentication then answers with an actionable error).
     * The token itself is never logged.
     */
    private val accessToken: String? = null,
    /** Seam for tests: how an [HttpURLConnection] is opened for a given [URL]. */
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {

    private val gson = Gson()

    fun execute() {
        val url = toUrl()
        val connection = openConnection(url).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        }

        try {
            writeBody(connection, url)

            val status = try {
                connection.responseCode
            } catch (e: IOException) {
                throw GradleException(
                    "Could not reach the portal at ${url} to trigger processing of '$directoryName': ${e.message}",
                    e,
                )
            }

            handleResponse(connection, status, url)
        } finally {
            connection.disconnect()
        }
    }

    private fun writeBody(connection: HttpURLConnection, url: URL) {
        try {
            connection.outputStream.use {
                it.write(gson.toJson(request()).toByteArray(Charsets.UTF_8))
            }
        } catch (e: IOException) {
            throw GradleException("Could not send the trigger request to the portal at ${url}: ${e.message}", e)
        }
    }

    private fun request() = TriggerRequest(
        directoryName = directoryName,
        projects = projects,
        deployType = deployType,
        userEmail = userEmail,
    )

    private fun handleResponse(connection: HttpURLConnection, status: Int, url: URL) {
        when (status) {
            HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_ACCEPTED -> reportOutcome(connection, status, url)
            HttpURLConnection.HTTP_UNAUTHORIZED -> throw GradleException(
                "The portal at ${url} rejected the trigger for '$directoryName' with HTTP 401: it requires " +
                    "authentication but no valid access token was presented. Run `gcloud auth login` (or " +
                    "`gcloud auth application-default login`) and retry."
            )
            HttpURLConnection.HTTP_FORBIDDEN -> throw GradleException(
                "The portal at ${url} rejected the trigger for '$directoryName' with HTTP 403: " +
                    describeForbidden(connection, url)
            )
            HTTP_UNPROCESSABLE -> throw GradleException(
                "The portal at ${url} rejected the trigger for '$directoryName': none of the listed projects " +
                        "(${projects}) is configured in the portal. Add at least one of them to the portal's projects."
            )
            else -> throw GradleException(
                "The portal at ${url} returned HTTP ${status} when triggering processing of '$directoryName'."
            )
        }
    }

    /**
     * Turns a 403 into a remediation message. The portal distinguishes why an authenticated caller
     * was denied (see `documentation/api.md`): not present in the portal project's IAM, or the
     * claimed [userEmail] not matching the token's account (gcloud service-account impersonation
     * setups), or a portal-side failure of the permission check.
     */
    private fun describeForbidden(connection: HttpURLConnection, url: URL): String {
        val body = try {
            connection.errorStream?.bufferedReader()?.use { it.readText() }
        } catch (_: IOException) {
            null
        }
        val response = body?.let { " Portal response: ${it.take(500)}" } ?: " (no response body)"
        return when {
            body != null && body.contains("\"email-mismatch\"") ->
                "the request's userEmail does not match the authenticated account. This happens with gcloud " +
                    "service-account impersonation setups (`gcloud config set auth/impersonate_service_account`): " +
                    "the portal cannot attribute the upload to the deploying account then." + response
            body != null && body.contains("\"not-permitted\"") ->
                "the authenticated account is not present in the IAM of the portal's project, neither directly " +
                    "nor through a group. Ask the portal administrator for access." + response
            body != null && body.contains("\"portal-issue\"") ->
                "the portal could not run its permission check (a portal-side problem, not yours)." + response
            else -> "the authenticated account is not permitted." + response
        }
    }

    private fun reportOutcome(connection: HttpURLConnection, status: Int, url: URL) {
        val response = try {
            gson.fromJson(readBody(connection, url), TriggerResponse::class.java)
        } catch (e: JsonSyntaxException) {
            throw GradleException(
                "The portal at ${url} returned a malformed trigger response for '$directoryName': ${e.message}",
                e,
            )
        } ?: throw GradleException(
            "The portal at ${url} returned an empty trigger response for '$directoryName'."
        )

        when (response.status) {
            TriggerResponse.STATUS_CREATED, TriggerResponse.STATUS_ALREADY_LINKED -> Logger.i(
                "Deploy link result: ${response.status} ('${ directoryName}' -->  " +
                        "[${response.project ?: "?"}, ${response.service ?: "?"}, ${response.version ?: "?"}])."
            )
            TriggerResponse.STATUS_PENDING -> Logger.i(
                "Portal accepted the trigger for '${directoryName}' as pending (HTTP ${status}): no deploy matched " +
                        "yet. The portal will retry matching and may still link the upload or delete its folder."
            )
            else -> throw GradleException(
                "The portal at ${url} returned an unexpected trigger status '${response.status}' " +
                        "for '${directoryName}'."
            )
        }
    }

    private fun readBody(connection: HttpURLConnection, url: URL): String =
        try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw GradleException("Could not read the trigger response from the portal at ${url}: ${e.message}", e)
        }

    private fun toUrl(): URL =
        try {
            URI.create("${portalUrl.trimEnd('/')}/collector/handleNewDirectory").toURL()
        } catch (e: Exception) {
            throw GradleException("'portalUrl' is not a valid URL: '$portalUrl' (${e.message})", e)
        }

    private companion object {
        const val HTTP_UNPROCESSABLE = 422
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 30_000
    }
}
