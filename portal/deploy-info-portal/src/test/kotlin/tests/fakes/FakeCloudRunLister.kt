package tests.infrastructure.gcp.cloudrun

import application.GcpCloudRunLister
import domain.CloudRunDeploy
import domain.Project
import java.time.Instant

/**
 * In-memory [GcpCloudRunLister] used in unit tests (and later by the `local` profile) instead of
 * calling the real GCP Cloud Run Admin API. Deploys are returned newest first, matching the
 * contract of [GcpCloudRunApiClient].
 */
class FakeCloudRunLister(
    private var deploysByProject: MutableMap<String, List<CloudRunDeploy>> = mutableMapOf(),
) : GcpCloudRunLister {

    override fun listAllDeploys(project: Project): List<CloudRunDeploy> =
        deploysByProject[project.name].orEmpty().sortedByDescending { it.createTime }

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
        ) = CloudRunDeploy(
            projectId = projectId,
            location = location,
            serviceId = serviceId,
            revisionId = revisionId,
            createTime = createTime,
        )
    }
}