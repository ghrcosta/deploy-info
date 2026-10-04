package infrastructure.gcp.appengine

import application.GcpAppEngineLister
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import domain.AppEngineDeploy
import domain.GcpListingException
import domain.Project
import infrastructure.gcp.AccessTokenProvider
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Lists App Engine services/versions of a project through the App Engine Admin REST API
 * (https://appengine.googleapis.com), authenticating with a bearer access token.
 */
class GcpAppEngineApiClient(
    private val restTemplate: RestTemplate,
    private val accessTokenProvider: AccessTokenProvider,
) : GcpAppEngineLister {

    private val objectMapper = ObjectMapper().registerKotlinModule()

    override fun listAllDeploys(project: Project): List<AppEngineDeploy> {
        val serviceIds = listServiceIds(project)
        return serviceIds
            .flatMap { listVersions(project, it) }
            .sortedByDescending { it.createTime }
    }

    private fun listServiceIds(project: Project): List<String> {
        val firstPageUrl = "${API_BASE_URL}/${API_PATH}/${project.name}/services?pageSize=${PAGE_SIZE}"
        return fetchAllPages(project, firstPageUrl) { body ->
            val response = parseBody(body, ServicesResponse::class.java, project)
            ApiPage(response.services.map { it.id }, response.nextPageToken)
        }
    }

    private fun listVersions(project: Project, serviceId: String): List<AppEngineDeploy> {
        val firstPageUrl = "${API_BASE_URL}/${API_PATH}/${project.name}/services/${serviceId}/versions?pageSize=${PAGE_SIZE}"
        return fetchAllPages(project, firstPageUrl) { body ->
            val response = parseBody(body, VersionsResponse::class.java, project)
            ApiPage(response.versions.map { it.toAppEngineDeploy(project.name, serviceId) }, response.nextPageToken)
        }
    }

    /** Follows `nextPageToken` until every page of a paginated listing has been fetched. */
    private fun <T> fetchAllPages(
        project: Project,
        firstPageUrl: String,
        parsePage: (body: String) -> ApiPage<T>,
    ): List<T> {
        val allItems = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val url = pageUrl(firstPageUrl, pageToken)
            val page = parsePage(getResponseBody(project, url))
            allItems += page.items
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return allItems
    }

    private fun pageUrl(firstPageUrl: String, pageToken: String?): String =
        if (pageToken == null) firstPageUrl else "${firstPageUrl}&pageToken=${pageToken}"

    private fun getResponseBody(project: Project, url: String): String {
        val request = RequestEntity<Void>(authHeaders(project), HttpMethod.GET, URI(url))
        return try {
            restTemplate.exchange(request, String::class.java).body ?: ""
        } catch (e: HttpStatusCodeException) {
            throw GcpListingException(
                "GCP App Engine API returned ${e.statusCode} for project ${project.name}", e
            )
        } catch (e: RestClientException) {
            throw GcpListingException(
                "Could not call the GCP App Engine API for project ${project.name}", e
            )
        }
    }

    private fun authHeaders(project: Project): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        setBearerAuth(accessTokenProvider.tokenFor(project))
    }

    private fun <T> parseBody(body: String, type: Class<T>, project: Project): T =
        try {
            objectMapper.readValue(body, type)
        } catch (e: Exception) {
            throw GcpListingException("Could not parse the App Engine API response for project ${project.name}", e)
        }

    private fun VersionDto.toAppEngineDeploy(projectId: String, serviceId: String): AppEngineDeploy =
        AppEngineDeploy(
            projectId = projectId,
            serviceId = serviceId,
            versionId = id,
            createTime = parseCreateTime(createTime, projectId, serviceId, id),
            url = versionUrl,
        )

    private fun parseCreateTime(
        createTime: String,
        projectId: String,
        serviceId: String,
        versionId: String,
    ): Instant =
        try {
            Instant.parse(createTime)
        } catch (e: DateTimeParseException) {
            throw GcpListingException(
                "Version ${serviceId}/${versionId} of project ${projectId} has an unparsable createTime '${createTime}'", e
            )
        }

    /** One page of a paginated listing: its items plus the token for the next page, if any. */
    private data class ApiPage<T>(val items: List<T>, val nextPageToken: String?)

    companion object {
        private const val API_BASE_URL = "https://appengine.googleapis.com"
        private const val API_PATH = "v1/apps"
        private const val PAGE_SIZE = 100
    }
}
