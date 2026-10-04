package infrastructure.gcp.cloudrun

import application.GcpCloudRunLister
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import domain.CloudRunDeploy
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
 * Lists Cloud Run services/revisions of a project through the Cloud Run Admin REST API
 * (https://run.googleapis.com), authenticating with a bearer access token.
 */
class GcpCloudRunApiClient(
    private val restTemplate: RestTemplate,
    private val accessTokenProvider: AccessTokenProvider,
) : GcpCloudRunLister {

    private val objectMapper = ObjectMapper().registerKotlinModule()

    override fun listAllDeploys(project: Project): List<CloudRunDeploy> {
        val services = listServices(project)
        return services
            .flatMap { listRevisions(project, it) }
            .sortedByDescending { it.createTime }
    }

    /** Lists every service in every region, parsing its location and id out of the resource name. */
    private fun listServices(project: Project): List<ServiceRef> {
        val firstPageUrl =
            "${API_BASE_URL}/${API_PATH}/${project.name}/locations/${ALL_LOCATIONS}" +
                "/services?pageSize=${PAGE_SIZE}"
        return fetchAllPages(project, firstPageUrl) { body ->
            val response = parseBody(body, ServicesResponse::class.java, project)
            ApiPage(response.services.map { it.toServiceRef(project.name) }, response.nextPageToken)
        }
    }

    private fun listRevisions(project: Project, service: ServiceRef): List<CloudRunDeploy> {
        val firstPageUrl =
            "${API_BASE_URL}/${API_PATH}/${project.name}/locations/${service.location}" +
                "/services/${service.serviceId}/revisions?pageSize=${PAGE_SIZE}"
        return fetchAllPages(project, firstPageUrl) { body ->
            val response = parseBody(body, RevisionsResponse::class.java, project)
            ApiPage(response.revisions.map { it.toCloudRunDeploy(project.name, service) }, response.nextPageToken)
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
                "GCP Cloud Run API returned ${e.statusCode} for project ${project.name}", e
            )
        } catch (e: RestClientException) {
            throw GcpListingException(
                "Could not call the GCP Cloud Run API for project ${project.name}", e
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
            throw GcpListingException("Could not parse the Cloud Run API response for project ${project.name}", e)
        }

    private fun ServiceDto.toServiceRef(projectId: String): ServiceRef {
        val nameParts = parseResourceName(name, "services", projectId)
        return ServiceRef(location = nameParts.location, serviceId = nameParts.resourceId, url = uri)
    }

    private fun RevisionDto.toCloudRunDeploy(projectId: String, service: ServiceRef): CloudRunDeploy {
        val nameParts = parseResourceName(name, "revisions", projectId)
        return CloudRunDeploy(
            projectId = projectId,
            location = service.location,
            serviceId = service.serviceId,
            revisionId = nameParts.resourceId,
            createTime = parseCreateTime(createTime, projectId, service.serviceId, nameParts.resourceId),
            url = service.url,
        )
    }

    /** Parses a `projects/{p}/locations/{l}/.../{collection}/{resourceId}` resource name. */
    private fun parseResourceName(name: String, collection: String, projectId: String): ResourceNameParts {
        val segments = name.split("/")
        val collectionIndex = segments.indexOf(collection)
        val locationIndex = segments.indexOf("locations")
        val isWellFormed = segments.getOrNull(1) == projectId && locationIndex > 0 && collectionIndex > 0 &&
            collectionIndex + 1 < segments.size
        if (!isWellFormed) {
            throw GcpListingException(
                "Cloud Run resource of project ${projectId} has an unexpected name '${name}'"
            )
        }
        return ResourceNameParts(location = segments[locationIndex + 1], resourceId = segments[collectionIndex + 1])
    }

    private fun parseCreateTime(
        createTime: String,
        projectId: String,
        serviceId: String,
        revisionId: String,
    ): Instant =
        try {
            Instant.parse(createTime)
        } catch (e: DateTimeParseException) {
            throw GcpListingException(
                "Revision ${serviceId}/${revisionId} of project ${projectId} has an unparsable createTime '${createTime}'", e
            )
        }

    /** A Cloud Run service of the project, resolved from the listed resource name. */
    private data class ServiceRef(val location: String, val serviceId: String, val url: String? = null)

    /** Location and id parsed out of a Cloud Run resource name. */
    private data class ResourceNameParts(val location: String, val resourceId: String)

    /** One page of a paginated listing: its items plus the token for the next page, if any. */
    private data class ApiPage<T>(val items: List<T>, val nextPageToken: String?)

    companion object {
        private const val API_BASE_URL = "https://run.googleapis.com"
        private const val API_PATH = "v2/projects"
        private const val ALL_LOCATIONS = "-"
        private const val PAGE_SIZE = 100
    }
}

