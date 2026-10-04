package domain

import java.time.Instant

/** A single App Engine version of a single service, as listed from a GCP project. */
data class AppEngineDeploy(
    val projectId: String,
    val serviceId: String,
    val versionId: String,
    val createTime: Instant,
    val url: String? = null,
)
