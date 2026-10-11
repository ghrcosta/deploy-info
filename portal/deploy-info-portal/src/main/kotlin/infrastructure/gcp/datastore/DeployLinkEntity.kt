package infrastructure.gcp.datastore

import com.google.cloud.spring.data.datastore.core.mapping.Entity
import domain.DeployLink
import domain.DeployType
import org.springframework.data.annotation.Id
import java.time.Instant

@Entity(name = "deploy-info/deploy-link")
class DeployLinkEntity(
    @Id
    val id: String,

    val projectId: String,
    val deployType: DeployType,
    val serviceId: String,
    val versionId: String,
    val location: String? = null,
    val storageFolder: String,
    val userEmail: String,
    val collectTimestamp: Instant,
    val deployTimestamp: Instant,
    val url: String? = null,
) {
    constructor(deployLink: DeployLink) : this(
        id = deployLink.keyName,
        projectId = deployLink.projectId,
        deployType = deployLink.deployType,
        serviceId = deployLink.serviceId,
        versionId = deployLink.versionId,
        location = deployLink.location,
        storageFolder = deployLink.storageFolder,
        userEmail = deployLink.userEmail,
        collectTimestamp = deployLink.collectTimestamp,
        deployTimestamp = deployLink.deployTimestamp,
        url = deployLink.url,
    )

    fun toModel(): DeployLink = DeployLink(
        projectId = projectId,
        deployType = deployType,
        serviceId = serviceId,
        versionId = versionId,
        location = location,
        storageFolder = storageFolder,
        userEmail = userEmail,
        collectTimestamp = collectTimestamp,
        deployTimestamp = deployTimestamp,
        url = url,
    )
}