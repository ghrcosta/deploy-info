package application

import domain.DeploymentLink
import domain.DeploymentType

interface DeploymentLinkRepository {
    fun get(projectId: String, deploymentType: DeploymentType, location: String?, serviceId: String, versionId: String): DeploymentLink?

    fun getAllFor(projectId: String, deploymentType: DeploymentType): List<DeploymentLink>

    /** Returns every deployment link of every project and deployment type (the lookup the tree uses). */
    fun getAll(): List<DeploymentLink>

    fun save(deploymentLink: DeploymentLink)

    fun delete(deploymentLink: DeploymentLink)
}
