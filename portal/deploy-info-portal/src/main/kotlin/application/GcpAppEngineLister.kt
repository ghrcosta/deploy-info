package application

import domain.AppEngineDeployment
import domain.Project

/** Lists the App Engine deployments (services/versions) that exist in a GCP project. */
interface GcpAppEngineLister {

    /**
     * Returns every version of every service in the project, newest first.
     *
     * @throws domain.GcpListingException when the listing API cannot be reached or returns an error.
     */
    fun listAllDeployments(project: Project): List<AppEngineDeployment>
}
