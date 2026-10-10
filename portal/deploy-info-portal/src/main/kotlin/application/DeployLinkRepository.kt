package application

import domain.DeployLink
import domain.DeployType

interface DeployLinkRepository {
    fun get(projectId: String, deployType: DeployType, location: String?, serviceId: String, versionId: String): DeployLink?

    fun getAllFor(projectId: String, deployType: DeployType): List<DeployLink>

    /** Returns every deploy link of every project and deploy type (the lookup the tree uses). */
    fun getAll(): List<DeployLink>

    fun save(deployLink: DeployLink)

    fun delete(deployLink: DeployLink)
}
