package tests.fakes

import application.DeployLinkRepository
import domain.DeployLink
import domain.DeployType

/**
 * In-memory [DeployLinkRepository] used in unit tests instead of the real Datastore-backed
 * repository, so no external dependency is required. Saving a link with an existing identity
 * replaces it.
 */
class FakeDeployLinkRepository(
    private val deployLinks: MutableList<DeployLink> = mutableListOf(),
) : DeployLinkRepository {

    override fun get(projectId: String, deployType: DeployType, location: String?, serviceId: String, versionId: String): DeployLink? =
        deployLinks.firstOrNull {
            it.projectId == projectId &&
                it.deployType == deployType &&
                it.serviceId == serviceId &&
                it.versionId == versionId &&
                it.location == location
        }

    override fun getAll(): List<DeployLink> = deployLinks.toList()

    override fun getAllFor(projectId: String, deployType: DeployType): List<DeployLink> =
        deployLinks.filter { it.projectId == projectId && it.deployType == deployType }

    override fun save(deployLink: DeployLink) {
        delete(deployLink)
        deployLinks.add(deployLink)
    }

    override fun delete(deployLink: DeployLink) {
        deployLinks.removeAll {
            it.projectId == deployLink.projectId &&
                it.deployType == deployLink.deployType &&
                it.serviceId == deployLink.serviceId &&
                it.versionId == deployLink.versionId &&
                it.location == deployLink.location
        }
    }
}