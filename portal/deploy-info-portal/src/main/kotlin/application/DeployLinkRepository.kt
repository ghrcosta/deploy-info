package application

import domain.DeployLink
import domain.DeployType

interface DeployLinkRepository {
    fun get(projectName: String, deployType: DeployType, location: String?, serviceId: String, versionId: String): DeployLink?

    fun getAllFor(projectName: String, deployType: DeployType): List<DeployLink>

    /** Returns every deploy link of every project and deploy type (the lookup the tree uses). */
    fun getAll(): List<DeployLink>

    fun save(deployLink: DeployLink)

    fun delete(deployLink: DeployLink)
}
