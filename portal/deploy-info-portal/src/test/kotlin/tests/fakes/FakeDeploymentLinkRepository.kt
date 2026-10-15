package tests.fakes

import application.DeploymentLinkRepository
import domain.DeploymentLink
import domain.DeploymentType

/**
 * In-memory [DeploymentLinkRepository] used in unit tests instead of the real Datastore-backed
 * repository, so no external dependency is required. Saving a link with an existing identity
 * replaces it.
 */
class FakeDeploymentLinkRepository(
    private val deploymentLinks: MutableList<DeploymentLink> = mutableListOf(),
) : DeploymentLinkRepository {

    override fun get(projectId: String, deploymentType: DeploymentType, location: String?, serviceId: String, versionId: String): DeploymentLink? =
        deploymentLinks.firstOrNull {
            it.projectId == projectId &&
                it.deploymentType == deploymentType &&
                it.serviceId == serviceId &&
                it.versionId == versionId &&
                it.location == location
        }

    override fun getAll(): List<DeploymentLink> = deploymentLinks.toList()

    override fun getAllFor(projectId: String, deploymentType: DeploymentType): List<DeploymentLink> =
        deploymentLinks.filter { it.projectId == projectId && it.deploymentType == deploymentType }

    override fun save(deploymentLink: DeploymentLink) {
        delete(deploymentLink)
        deploymentLinks.add(deploymentLink)
    }

    override fun delete(deploymentLink: DeploymentLink) {
        deploymentLinks.removeAll {
            it.projectId == deploymentLink.projectId &&
                it.deploymentType == deploymentLink.deploymentType &&
                it.serviceId == deploymentLink.serviceId &&
                it.versionId == deploymentLink.versionId &&
                it.location == deploymentLink.location
        }
    }
}