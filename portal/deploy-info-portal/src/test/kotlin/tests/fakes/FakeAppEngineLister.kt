package tests.fakes

import application.GcpAppEngineLister
import domain.AppEngineDeployment
import domain.GcpListingException
import domain.Project
import java.time.Instant

/**
 * In-memory [GcpAppEngineLister] used in unit tests (and later by the `local` profile) instead of
 * calling the real GCP App Engine Admin API. Deployments are returned newest first, matching the
 * contract of [infrastructure.gcp.appengine.GcpAppEngineApiClient].
 */
class FakeAppEngineLister(
    private var deploymentsByProject: MutableMap<String, List<AppEngineDeployment>> = mutableMapOf(),
) : GcpAppEngineLister {

    /** When true, the next (and every subsequent) listing throws instead of returning deployments. */
    var throwOnEveryList: Boolean = false

    /** When set, listings for the projects matching this predicate throw instead of returning deployments. */
    var throwOnProject: ((String) -> Boolean)? = null

    private var throwOnNextList: Boolean = false

    override fun listAllDeployments(project: Project): List<AppEngineDeployment> {
        if (throwOnEveryList || throwOnNextList || throwOnProject?.invoke(project.projectId) == true) {
            throwOnNextList = false
            throw GcpListingException("transient listing failure")
        }
        return deploymentsByProject[project.projectId].orEmpty().sortedByDescending { it.createTime }
    }

    /** Makes the next listing throw [GcpListingException] (simulating a transient failure). */
    fun throwOnNextList() {
        throwOnNextList = true
    }

    fun seed(projectId: String, deployments: List<AppEngineDeployment>) {
        deploymentsByProject[projectId] = deployments
    }

    companion object {
        fun deploy(
            projectId: String,
            serviceId: String,
            versionId: String,
            createTime: Instant,
            createdBy: String? = null,
        ) = AppEngineDeployment(
            projectId = projectId,
            serviceId = serviceId,
            versionId = versionId,
            createTime = createTime,
            createdBy = createdBy,
        )
    }
}
