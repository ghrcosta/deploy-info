package domain

import java.time.Instant

enum class DeploymentType { GAE, RUN }

/**
 * The link between a collector upload (a Cloud Storage folder) and the GCP deployment it was built for.
 *
 * Identity is the tuple (projectId, deploymentType, serviceId, versionId, location): location is part
 * of the identity because Cloud Run revision ids repeat across regions (null for App Engine).
 * The project/deploymentType/storage folder/user email/timestamp come from the collector's trigger
 * request; service/version/location/deploy timestamp/url are filled by the linking logic from the
 * GCP listing.
 *
 * [deploymentTimestamp] is the GCP deployment's creation time. It disambiguates a version id that is
 * deployed again (the same identity tuple, but a new deployment): the linking logic uses it to tell
 * "the very same deployment is already linked" from "a new deployment reused an existing identity" — in the
 * latter case the new link replaces the old one (see `CreateDeploymentLinkUseCase`).
 */
data class DeploymentLink(
    val projectId: String,
    val deploymentType: DeploymentType,
    val serviceId: String,
    val versionId: String,
    val location: String?,
    val storageFolder: String,
    val userEmail: String,
    val collectTimestamp: Instant,
    val deploymentTimestamp: Instant,
    val url: String? = null,
) {
    /** Datastore key name for this deployment link's identity. */
    val keyName: String
        get() = listOf(
            projectId,
            deploymentType.name,
            location ?: "-",
            serviceId,
            versionId,
        ).joinToString("_")
}