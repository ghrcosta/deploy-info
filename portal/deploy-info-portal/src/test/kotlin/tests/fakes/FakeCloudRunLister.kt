package tests.fakes

import application.GcpCloudRunLister
import domain.CloudRunDeployment
import domain.GcpListingException
import domain.Project
import java.time.Instant

/**
 * In-memory [GcpCloudRunLister] used in unit tests (and later by the `local` profile) instead of
 * calling the real GCP Cloud Run Admin API. Deployments are returned newest first, matching the
 * contract of [infrastructure.gcp.cloudrun.GcpCloudRunApiClient].
 */
class FakeCloudRunLister(
    private var deploymentsByProject: MutableMap<String, List<CloudRunDeployment>> = mutableMapOf(),
) : GcpCloudRunLister {

    /** When true, the next (and every subsequent) listing throws instead of returning deployments. */
    var throwOnEveryList: Boolean = false

    override fun listAllDeployments(project: Project): List<CloudRunDeployment> {
        if (throwOnEveryList) {
            throw GcpListingException("transient listing failure")
        }
        return deploymentsByProject[project.projectId].orEmpty().sortedByDescending { it.createTime }
    }

    fun seed(projectId: String, deployments: List<CloudRunDeployment>) {
        deploymentsByProject[projectId] = deployments
    }

    companion object {
        fun deploy(
            projectId: String,
            location: String,
            serviceId: String,
            revisionId: String,
            createTime: Instant,
            createdBy: String? = null,
        ) = CloudRunDeployment(
            projectId = projectId,
            location = location,
            serviceId = serviceId,
            revisionId = revisionId,
            createTime = createTime,
            createdBy = createdBy,
        )
    }
}