package domain

import java.time.Instant

/** A single Cloud Run revision of a single service, as listed from a GCP project. */
data class CloudRunDeploy(
    val projectId: String,
    val location: String,
    val serviceId: String,
    val revisionId: String,
    val createTime: Instant,
    val url: String? = null,
)