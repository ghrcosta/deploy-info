package application

import domain.CloudRunDeploy
import domain.Project

/** Lists the Cloud Run deploys (services/revisions) that exist in a GCP project. */
interface GcpCloudRunLister {

    /**
     * Returns every revision of every service in every region of the project, newest first.
     *
     * @throws domain.GcpListingException when the listing API cannot be reached or returns an error.
     */
    fun listAllDeploys(project: Project): List<CloudRunDeploy>
}