package tests.fakes

import application.GcpAppEngineLister
import domain.AppEngineDeploy
import domain.GcpListingException
import domain.Project
import java.time.Instant

/**
 * In-memory [GcpAppEngineLister] used in unit tests (and later by the `local` profile) instead of
 * calling the real GCP App Engine Admin API. Deploys are returned newest first, matching the
 * contract of [infrastructure.gcp.appengine.GcpAppEngineApiClient].
 */
class FakeAppEngineLister(
    private var deploysByProject: MutableMap<String, List<AppEngineDeploy>> = mutableMapOf(),
) : GcpAppEngineLister {

    /** When true, the next (and every subsequent) listing throws instead of returning deploys. */
    var throwOnEveryList: Boolean = false

    /** When set, listings for the projects matching this predicate throw instead of returning deploys. */
    var throwOnProject: ((String) -> Boolean)? = null

    private var throwOnNextList: Boolean = false

    override fun listAllDeploys(project: Project): List<AppEngineDeploy> {
        if (throwOnEveryList || throwOnNextList || throwOnProject?.invoke(project.name) == true) {
            throwOnNextList = false
            throw GcpListingException("transient listing failure")
        }
        return deploysByProject[project.name].orEmpty().sortedByDescending { it.createTime }
    }

    /** Makes the next listing throw [GcpListingException] (simulating a transient failure). */
    fun throwOnNextList() {
        throwOnNextList = true
    }

    fun seed(projectId: String, deploys: List<AppEngineDeploy>) {
        deploysByProject[projectId] = deploys
    }

    companion object {
        fun deploy(
            projectId: String,
            serviceId: String,
            versionId: String,
            createTime: Instant,
            createdBy: String? = null,
        ) = AppEngineDeploy(
            projectId = projectId,
            serviceId = serviceId,
            versionId = versionId,
            createTime = createTime,
            createdBy = createdBy,
        )
    }
}
