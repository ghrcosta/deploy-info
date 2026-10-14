package application.cleanup

import application.*
import domain.CloudRunDeploy
import domain.CredentialsException
import domain.DeployType
import domain.GcpListingException
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The validity/cleanup logic: periodically detects deploy links whose deploy no longer exists in
 * GCP and removes them — the Datastore entry and the collector upload's Cloud Storage folder.
 * Nothing is returned; the outcome is reported via log messages.
 *
 * The sweep rules (delete only on proof, grace period, delete order, unconfigured projects,
 * concurrency): documentation/backend/deploy-cleanup.md.
 */
class CleanupInvalidDeployLinksUseCase(
    private val projectRepository: ProjectRepository,
    private val appEngineLister: GcpAppEngineLister,
    private val cloudRunLister: GcpCloudRunLister,
    private val deployLinkRepository: DeployLinkRepository,
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
            for (deployType in DeployType.entries) {
                // Grace period: never judge links younger than it (snapshot race — see
                // documentation/backend/deploy-cleanup.md).
                val candidates = deployLinkRepository.getAllFor(project.projectId, deployType).filter {
                    it.collectTimestamp.isBefore(now.minus(gracePeriod))
                }
                if (candidates.isEmpty()) continue

                val existingIdentities = try {
                    listExistingIdentities(project, deployType)
                } catch (e: GcpListingException) {
                    skipped.add("${project.projectId}/${deployType.name} (${e.message})")
                    continue
                } catch (e: CredentialsException) {
                    skipped.add("${project.projectId}/${deployType.name} (${e.message})")
                    continue
                }

                for (link in candidates) {
                    if (identity(link.serviceId, link.versionId, link.location) in existingIdentities) continue
                    deployLinkRepository.delete(link)
                    try {
                        storageCleaner.delete(link.storageFolder)
                    } catch (e: RuntimeException) {
                        logger.warn(
                            "Deleted stale deploy link ${link.keyName} of ${project.projectId}, " +
                                "but deleting its storage folder ${link.storageFolder} failed: ${e.message}",
                        )
                    }
                    deleted.add("${project.projectId}/${deployType.name}/${link.keyName}")
                }
            }
        }

        val summary = "Cleanup sweep: checked ${checkedProjects.size} project(s) for GAE and RUN " +
            "deploys, deleted ${deleted.size} stale deploy link(s)"
        if (deleted.isEmpty() && skipped.isEmpty()) {
            logger.info(summary)
        } else {
            logger.info("$summary; deleted: $deleted; skipped because the listing failed: $skipped")
        }
    }

    /** The (serviceId, versionId, location) identities that still exist in GCP, per deploy type. */
    private fun listExistingIdentities(project: domain.Project, deployType: DeployType): Set<String> =
        when (deployType) {
            DeployType.GAE ->
                appEngineLister.listAllDeploys(project).map { identity(it.serviceId, it.versionId, null) }
            DeployType.RUN ->
                cloudRunLister.listAllDeploys(project).map { deploy: CloudRunDeploy ->
                    identity(deploy.serviceId, deploy.revisionId, deploy.location)
                }
        }.toSet()

    private fun identity(serviceId: String, versionId: String, location: String?): String =
        "${location ?: "-"}|$serviceId|$versionId"

    companion object {
        private val logger = LoggerFactory.getLogger(CleanupInvalidDeployLinksUseCase::class.java)
    }
}