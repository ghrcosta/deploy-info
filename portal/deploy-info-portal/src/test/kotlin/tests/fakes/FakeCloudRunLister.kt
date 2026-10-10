package tests.fakes

import application.GcpCloudRunLister
import domain.CloudRunDeploy
import domain.GcpListingException
import domain.Project
import java.time.Instant

/**
 * In-memory [GcpCloudRunLister] used in unit tests (and later by the `local` profile) instead of
 * calling the real GCP Cloud Run Admin API. Deploys are returned newest first, matching the
 * contract of [infrastructure.gcp.cloudrun.GcpCloudRunApiClient].
 */
class FakeCloudRunLister(
    private var deploysByProject: MutableMap<String, List<CloudRunDeploy>> = mutableMapOf(),
) : GcpCloudRunLister {

    /** When true, the next (and every subsequent) listing throws instead of returning deploys. */
    var throwOnEveryList: Boolean = false

    override fun listAllDeploys(project: Project): List<CloudRunDeploy> {
        if (throwOnEveryList) {
            throw GcpListingException("transient listing failure")
        }
        return deploysByProject[project.projectId].orEmpty().sortedByDescending { it.createTime }
    }

    fun seed(projectId: String, deploys: List<CloudRunDeploy>) {
        deploysByProject[projectId] = deploys
    }

    companion object {
        fun deploy(
            projectId: String,
            location: String,
            serviceId: String,
            revisionId: String,
            createTime: Instant,
            createdBy: String? = null,
        ) = CloudRunDeploy(
            projectId = projectId,
            location = location,
            serviceId = serviceId,
            revisionId = revisionId,
            createTime = createTime,
            createdBy = createdBy,
        )
    }
}