package infrastructure.gcp.appengine

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** Root JSON document of `GET /v1/apps/{projectId}/services`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ServicesResponse(
    val services: List<ServiceDto> = emptyList(),
    val nextPageToken: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ServiceDto(val id: String = "")

/** Root JSON document of `GET /v1/apps/{projectId}/services/{serviceId}/versions`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class VersionsResponse(
    val versions: List<VersionDto> = emptyList(),
    val nextPageToken: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VersionDto(
    val id: String = "",
    val createTime: String = "",
    val versionUrl: String? = null,
)
