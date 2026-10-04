package tests.infrastructure.gcp.appengine

import application.GcpAppEngineLister
import domain.AppEngineDeploy
import domain.Project
import java.time.Instant

/**
 * In-memory [GcpAppEngineLister] used in unit tests (and later by the `local` profile) instead of
 * calling the real GCP App Engine Admin API. Deploys are returned newest first, matching the
 * contract of [GcpAppEngineApiClient].
 */
class FakeAppEngineLister(
    private var deploysByProject: MutableMap<String, List<AppEngineDeploy>> = mutableMapOf(),
) : GcpAppEngineLister {

    override fun listAllDeploys(project: Project): List<AppEngineDeploy> =
        deploysByProject[project.name].orEmpty().sortedByDescending { it.createTime }

    fun seed(projectId: String, deploys: List<AppEngineDeploy>) {
        deploysByProject[projectId] = deploys
    }

    companion object {
        fun deploy(
            projectId: String,
            serviceId: String,
            versionId: String,
            createTime: Instant,
        ) = AppEngineDeploy(
            projectId = projectId,
            serviceId = serviceId,
            versionId = versionId,
            createTime = createTime,
        )
    }
}
