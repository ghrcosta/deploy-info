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
 *
 * For every configured project and both deploy types, the deploys are listed from GCP and compared
 * against the links in the repository. A link is stale when its identity (service id, version id,
 * location) is not present in the listing. Deletion order matters: the Datastore entry goes first,
 * so a Cloud Storage failure (logged and swallowed) can never leave a link pointing at a folder
 * that was deleted underneath it — it only leaves an orphaned folder as debris.
 *
 * Safety rules:
 * - A listing that fails ([GcpListingException] / [CredentialsException]) skips that project and
 *   deploy type entirely: links are only ever deleted on a *successful* listing that proves they
 *   are stale, never on a transient failure.
 * - Links younger than the configured grace period are not judged. This is because their deploy
 *   may have been created after this sweep's listing snapshot was taken — a sweep snapshot is a
 *   point-in-time view, and a deploy that finishes while the sweep runs is live but absent from
 *   the snapshot.
 * - A deploy link whose project is no longer configured in the portal is left untouched (it cannot
 *   even be listed safely without the project's service-account configuration).
 *
 * The application layer stays Spring-free; all collaborators are constructor-injected interfaces.
 * Nothing is returned — the outcome is reported via log messages.
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
                // Grace period: a link younger than the grace period is never judged stale.
                val candidates = deployLinkRepository.getAllFor(project.name, deployType).filter {
                    it.collectTimestamp.isBefore(now.minus(gracePeriod))
                }
                if (candidates.isEmpty()) continue

                val existingIdentities = try {
                    listExistingIdentities(project, deployType)
                } catch (e: GcpListingException) {
                    skipped.add("${project.name}/${deployType.name} (${e.message})")
                    continue
                } catch (e: CredentialsException) {
                    skipped.add("${project.name}/${deployType.name} (${e.message})")
                    continue
                }

                for (link in candidates) {
                    if (identity(link.serviceId, link.versionId, link.location) in existingIdentities) continue
                    deployLinkRepository.delete(link)
                    try {
                        storageCleaner.delete(link.storageFolder)
                    } catch (e: RuntimeException) {
                        logger.warn(
                            "Deleted stale deploy link ${link.keyName} of ${project.name}, " +
                                "but deleting its storage folder ${link.storageFolder} failed: ${e.message}",
                        )
                    }
                    deleted.add("${project.name}/${deployType.name}/${link.keyName}")
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