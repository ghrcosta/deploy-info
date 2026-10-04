package infrastructure.gcp.cloudrun

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** Root JSON document of `GET /v2/projects/{projectId}/locations/-/services`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ServicesResponse(
    val services: List<ServiceDto> = emptyList(),
    val nextPageToken: String? = null,
)

/** Resource name format: `projects/{projectId}/locations/{location}/services/{serviceId}`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ServiceDto(
    val name: String = "",
    val uri: String? = null,
)

/** Root JSON document of `GET /v2/projects/{projectId}/locations/{location}/services/{serviceId}/revisions`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RevisionsResponse(
    val revisions: List<RevisionDto> = emptyList(),
    val nextPageToken: String? = null,
)

/** Resource name format: `projects/{projectId}/locations/{location}/services/{serviceId}/revisions/{revisionId}`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RevisionDto(
    val name: String = "",
    val createTime: String = "",
)