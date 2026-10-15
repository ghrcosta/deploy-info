package infrastructure.gcp.datastore

import application.DeploymentLinkRepository
import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import domain.DeploymentLink
import domain.DeploymentType
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class DeploymentLinkDatastoreRepository(
    private val datastoreTemplate: DatastoreTemplate,
) : DeploymentLinkRepository {

    override fun get(projectId: String, deploymentType: DeploymentType, location: String?, serviceId: String, versionId: String): DeploymentLink? =
        datastoreTemplate.findById(keyName(projectId, deploymentType, location, serviceId, versionId), DeploymentLinkEntity::class.java)?.toModel()

    override fun getAllFor(projectId: String, deploymentType: DeploymentType): List<DeploymentLink> =
        datastoreTemplate.findAll(DeploymentLinkEntity::class.java)
            .filter { it.projectId == projectId && it.deployType == deploymentType }
            .map { it.toModel() }

    override fun getAll(): List<DeploymentLink> =
        datastoreTemplate.findAll(DeploymentLinkEntity::class.java).map { it.toModel() }

    override fun save(deploymentLink: DeploymentLink) {
        datastoreTemplate.save(DeploymentLinkEntity(deploymentLink))
    }

    override fun delete(deploymentLink: DeploymentLink) {
        datastoreTemplate.deleteById(deploymentLink.keyName, DeploymentLinkEntity::class.java)
    }

    private fun keyName(projectId: String, deploymentType: DeploymentType, location: String?, serviceId: String, versionId: String): String =
        DeploymentLink(
            projectId = projectId,
            deploymentType = deploymentType,
            serviceId = serviceId,
            versionId = versionId,
            location = location,
            storageFolder = "",
            userEmail = "",
            collectTimestamp = Instant.EPOCH,
            deploymentTimestamp = Instant.EPOCH,
        ).keyName
}
