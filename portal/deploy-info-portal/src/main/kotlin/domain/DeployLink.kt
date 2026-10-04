package domain

import java.time.Instant

enum class DeployType { GAE, RUN }

/**
 * The link between a collector upload (a Cloud Storage folder) and the GCP deploy it was built for.
 *
 * Identity is the tuple (projectName, deployType, serviceId, versionId, location): location is part
 * of the identity because Cloud Run revision ids repeat across regions (null for App Engine).
 * The project/deployType/storage folder/user email/timestamp come from the collector's trigger
 * request; service/version/location/url are filled by the linking logic from the GCP listing.
 */
data class DeployLink(
    val projectName: String,
    val deployType: DeployType,
    val serviceId: String,
    val versionId: String,
    val location: String?,
    val storageFolder: String,
    val userEmail: String,
    val collectTimestamp: Instant,
    val url: String? = null,
) {
    /** Datastore key name for this deploy link's identity. */
    val keyName: String
        get() = listOf(
            projectName,
            deployType.name,
            location ?: "-",
            serviceId,
            versionId,
        ).joinToString("_")
}