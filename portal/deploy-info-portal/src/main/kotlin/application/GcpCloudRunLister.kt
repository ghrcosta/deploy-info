package application

import domain.CloudRunDeployment
import domain.Project

/** Lists the Cloud Run deployments (services/revisions) that exist in a GCP project. */
interface GcpCloudRunLister {

    /**
     * Returns every revision of every service in every region of the project, newest first.
     *
     * @throws domain.GcpListingException when the listing API cannot be reached or returns an error.
     */
    fun listAllDeployments(project: Project): List<CloudRunDeployment>
}