package domain

import java.time.Instant

enum class DeployType { GAE, RUN }

/**
 * The link between a collector upload (a Cloud Storage folder) and the GCP deploy it was built for.
 *
 * Identity is the tuple (projectId, deployType, serviceId, versionId, location): location is part
 * of the identity because Cloud Run revision ids repeat across regions (null for App Engine).
 * The project/deployType/storage folder/user email/timestamp come from the collector's trigger
 * request; service/version/location/deploy timestamp/url are filled by the linking logic from the
 * GCP listing.
 *
 * [deployTimestamp] is the GCP deploy's creation time. It disambiguates a version id that is
 * deployed again (the same identity tuple, but a new deploy): the linking logic uses it to tell
 * "the very same deploy is already linked" from "a new deploy reused an existing identity" — in the
 * latter case the new link replaces the old one (see `CreateDeployLinkUseCase`).
 */
data class DeployLink(
    val projectId: String,
    val deployType: DeployType,
    val serviceId: String,
    val versionId: String,
    val location: String?,
    val storageFolder: String,
    val userEmail: String,
    val collectTimestamp: Instant,
    val deployTimestamp: Instant,
    val url: String? = null,
) {
    /** Datastore key name for this deploy link's identity. */
    val keyName: String
        get() = listOf(
            projectId,
            deployType.name,
            location ?: "-",
            serviceId,
            versionId,
        ).joinToString("_")
}