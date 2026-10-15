package infrastructure.gcp.datastore

import com.google.cloud.spring.data.datastore.core.mapping.Entity
import domain.DeploymentLink
import domain.DeploymentType
import org.springframework.data.annotation.Id
import java.time.Instant

@Entity(name = "deploy-info/deploy-link")
class DeploymentLinkEntity(
    @Id
    val id: String,

    val projectId: String,
    val deployType: DeploymentType,
    val serviceId: String,
    val versionId: String,
    val location: String? = null,
    val storageFolder: String,
    val userEmail: String,
    val collectTimestamp: Instant,
    val deployTimestamp: Instant,
    val url: String? = null,
) {
    constructor(deploymentLink: DeploymentLink) : this(
        id = deploymentLink.keyName,
        projectId = deploymentLink.projectId,
        deployType = deploymentLink.deploymentType,
        serviceId = deploymentLink.serviceId,
        versionId = deploymentLink.versionId,
        location = deploymentLink.location,
        storageFolder = deploymentLink.storageFolder,
        userEmail = deploymentLink.userEmail,
        collectTimestamp = deploymentLink.collectTimestamp,
        deployTimestamp = deploymentLink.deploymentTimestamp,
        url = deploymentLink.url,
    )

    fun toModel(): DeploymentLink = DeploymentLink(
        projectId = projectId,
        deploymentType = deployType,
        serviceId = serviceId,
        versionId = versionId,
        location = location,
        storageFolder = storageFolder,
        userEmail = userEmail,
        collectTimestamp = collectTimestamp,
        deploymentTimestamp = deployTimestamp,
        url = url,
    )
}