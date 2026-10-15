package application.cleanup

import application.*
import domain.CloudRunDeployment
import domain.CredentialsException
import domain.DeploymentType
import domain.GcpListingException
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The validity/cleanup logic: periodically detects deployment links whose deployment no longer exists in
 * GCP and removes them — the Datastore entry and the collector upload's Cloud Storage folder.
 * Nothing is returned; the outcome is reported via log messages.
 *
 * The sweep rules (delete only on proof, grace period, delete order, unconfigured projects,
 * concurrency): documentation/backend/deployment-cleanup.md.
 */
class CleanupInvalidDeploymentLinksUseCase(
    private val projectRepository: ProjectRepository,
    private val appEngineLister: GcpAppEngineLister,
    private val cloudRunLister: GcpCloudRunLister,
    private val deploymentLinkRepository: DeploymentLinkRepository,
    private val storageCleaner: StorageCleaner,
    private val clock: Clock,
    private val gracePeriod: Duration,
) {

    fun execute() {
        val now: Instant = clock.instant()
        val checkedProjects = projectRepository.getAll()
        val deleted = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        for (project in checkedProjects) {
            for (deploymentType in DeploymentType.entries) {
                // Grace period: never judge links younger than it (snapshot race — see
                // documentation/backend/deployment-cleanup.md).
                val candidates = deploymentLinkRepository.getAllFor(project.projectId, deploymentType).filter {
                    it.collectTimestamp.isBefore(now.minus(gracePeriod))
                }
                if (candidates.isEmpty()) continue

                val existingIdentities = try {
                    listExistingIdentities(project, deploymentType)
                } catch (e: GcpListingException) {
                    skipped.add("${project.projectId}/${deploymentType.name} (${e.message})")
                    continue
                } catch (e: CredentialsException) {
                    skipped.add("${project.projectId}/${deploymentType.name} (${e.message})")
                    continue
                }

                for (link in candidates) {
                    if (identity(link.serviceId, link.versionId, link.location) in existingIdentities) continue
                    deploymentLinkRepository.delete(link)
                    try {
                        storageCleaner.delete(link.storageFolder)
                    } catch (e: RuntimeException) {
                        logger.warn(
                            "Deleted stale deployment link ${link.keyName} of ${project.projectId}, " +
                                "but deleting its storage folder ${link.storageFolder} failed: ${e.message}",
                        )
                    }
                    deleted.add("${project.projectId}/${deploymentType.name}/${link.keyName}")
                }
            }
        }

        val summary = "Cleanup sweep: checked ${checkedProjects.size} project(s) for GAE and RUN " +
            "deployments, deleted ${deleted.size} stale deployment link(s)"
        if (deleted.isEmpty() && skipped.isEmpty()) {
            logger.info(summary)
        } else {
            logger.info("${summary}; deleted: ${deleted}; skipped because the listing failed: ${skipped}")
        }
    }

    /** The (serviceId, versionId, location) identities that still exist in GCP, per deployment type. */
    private fun listExistingIdentities(project: domain.Project, deploymentType: DeploymentType): Set<String> =
        when (deploymentType) {
            DeploymentType.GAE ->
                appEngineLister.listAllDeployments(project).map { identity(it.serviceId, it.versionId, null) }
            DeploymentType.RUN ->
                cloudRunLister.listAllDeployments(project).map { deploy: CloudRunDeployment ->
                    identity(deploy.serviceId, deploy.revisionId, deploy.location)
                }
        }.toSet()

    private fun identity(serviceId: String, versionId: String, location: String?): String =
        "${location ?: "-"}|$serviceId|$versionId"

    companion object {
        private val logger = LoggerFactory.getLogger(CleanupInvalidDeploymentLinksUseCase::class.java)
    }
}