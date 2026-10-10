package infrastructure.gcp.datastore

import application.DeployLinkRepository
import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import domain.DeployLink
import domain.DeployType
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class DeployLinkDatastoreRepository(
    private val datastoreTemplate: DatastoreTemplate,
) : DeployLinkRepository {

    override fun get(projectId: String, deployType: DeployType, location: String?, serviceId: String, versionId: String): DeployLink? =
        datastoreTemplate.findById(keyName(projectId, deployType, location, serviceId, versionId), DeployLinkEntity::class.java)?.toModel()

    override fun getAllFor(projectId: String, deployType: DeployType): List<DeployLink> =
        datastoreTemplate.findAll(DeployLinkEntity::class.java)
            .filter { it.projectId == projectId && it.deployType == deployType }
            .map { it.toModel() }

    override fun getAll(): List<DeployLink> =
        datastoreTemplate.findAll(DeployLinkEntity::class.java).map { it.toModel() }

    override fun save(deployLink: DeployLink) {
        datastoreTemplate.save(DeployLinkEntity(deployLink))
    }

    override fun delete(deployLink: DeployLink) {
        datastoreTemplate.deleteById(deployLink.keyName, DeployLinkEntity::class.java)
    }

    private fun keyName(projectId: String, deployType: DeployType, location: String?, serviceId: String, versionId: String): String =
        DeployLink(
            projectId = projectId,
            deployType = deployType,
            serviceId = serviceId,
            versionId = versionId,
            location = location,
            storageFolder = "",
            userEmail = "",
            collectTimestamp = Instant.EPOCH,
        ).keyName
}
