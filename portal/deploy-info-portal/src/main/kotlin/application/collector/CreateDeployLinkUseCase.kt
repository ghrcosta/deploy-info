package application.collector

import application.*
import domain.*
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * Links a collector upload (a Cloud Storage folder) to the GCP deploy it was built for.
 *
 * The collector cannot know exactly which GCP project the code was deployed to (e.g. PROD, DEV or
 * QA), so it sends a list of candidate projects; the listing is merged across all of them and the
 * winning deploy is chosen globally.
 *
 * Matching rules: only deploys created by the collector's user are candidates, the deploy must have
 * been created at (or before) the collection timestamp, and within the configured window before it;
 * among the candidates the closest one below the collection timestamp wins (candidates are sorted
 * newest first, so it is the first survivor of the filters).
 *
 * When no plausible match is found, the matching is retried once after a short delay (a deploy may
 * appear late, or the listing may fail transiently) before the upload's storage folder is deleted.
 *
 * A deploy identity that is already linked is handled by comparing the existing link's deploy
 * timestamp (the GCP deploy's creation time, stored on every link) with the matched deploy's:
 * when they are equal it is the very same deploy (e.g. a duplicate trigger) and the existing link
 * is left untouched; when they differ a new deploy reused the version id — the new link then
 * replaces the old one, and the old link's storage folder (the replaced deploy's upload) is deleted.
 *
 * The application layer stays Spring-free; all collaborators are constructor-injected interfaces.
 */
class CreateDeployLinkUseCase(
    private val projectRepository: ProjectRepository,
    private val appEngineLister: GcpAppEngineLister,
    private val cloudRunLister: GcpCloudRunLister,
    private val deployLinkRepository: DeployLinkRepository,
    private val storageCleaner: StorageCleaner,
    private val retryScheduler: RetryScheduler,
    private val window: Duration,
) {

    /**
     * The data the collector can provide; the rest is discovered through the GCP listing. The
     * collection timestamp is derived from the collector's output directory name, which follows the
     * `<user>_<deployType>_<epochMillis>` convention (see `collector-java`'s `Context`).
     */
    data class Input(
        /** The collector's output directory name, also used as the link's storage folder. */
        val directoryName: String,
        /**
         * Candidate GCP projects the code may have been deployed to (the collector cannot know
         * which one, e.g. PROD, DEV or QA). The deploys of all of them are matched together.
         */
        val projects: List<String>,
        val deployType: DeployType,
        val userEmail: String,
    )

    sealed class Output {
        /** A plausible deploy was found and linked. */
        data class Created(val deployLink: DeployLink) : Output()

        /** The matched deploy already had a link; the existing link was left untouched. */
        data class AlreadyLinked(val existing: DeployLink) : Output()

        /**
         * Terminal no-match (no candidate, or none inside the window): the matching was retried,
         * still found nothing, and the upload's storage folder has been deleted.
         */
        data object NotLinked : Output()

        /** The project is not configured in the portal; the upload is deleted after the retry. */
        data object UnknownProject : Output()
    }

    fun execute(input: Input): Output {
        val collectTimestamp = parseCollectTimestamp(input.directoryName, input.deployType)
        val projects = input.projects.mapNotNull { projectRepository.get(it) }
        if (projects.isEmpty()) return unknownProject(input)
        return try {
            when (val result = matchDeploy(projects, input, collectTimestamp)) {
                is MatchResult.Matched -> createLink(input, result.deploy, collectTimestamp, result.replaced)
                is MatchResult.AlreadyLinked -> Output.AlreadyLinked(result.existing)
                MatchResult.NoMatch -> scheduleRetry(input, collectTimestamp)
            }
        } catch (_: GcpListingException) {
            scheduleRetry(input, collectTimestamp)
        } catch (_: CredentialsException) {
            scheduleRetry(input, collectTimestamp)
        }
    }

    /** Re-runs the matching once. If it still finds no match, deletes the upload's storage folder. */
    private fun retry(input: Input, collectTimestamp: Instant) {
        val projects = input.projects.mapNotNull { projectRepository.get(it) }
        val result: MatchResult? = if (projects.isEmpty()) {
            null
        } else {
            try {
                matchDeploy(projects, input, collectTimestamp)
            } catch (_: GcpListingException) {
                null
            } catch (_: CredentialsException) {
                null
            }
        }
        when (result) {
            is MatchResult.Matched -> createLink(input, result.deploy, collectTimestamp, result.replaced)
            is MatchResult.AlreadyLinked -> Unit
            null, MatchResult.NoMatch -> storageCleaner.delete(input.directoryName)
        }
    }

    /**
     * Merges the deploy candidates of every configured project, then picks the closest survivor of
     * the filters regardless of which project it came from.
     */
    private fun matchDeploy(projects: List<Project>, input: Input, collectTimestamp: Instant): MatchResult {
        val candidates = projects.flatMap { project ->
            when (input.deployType) {
                DeployType.GAE ->
                    appEngineLister.listAllDeploys(project).map {
                        DeployCandidate(project.projectId, it.createTime, it.serviceId, it.versionId, it.url, it.createdBy)
                    }
                DeployType.RUN ->
                    cloudRunLister.listAllDeploys(project).map {
                        DeployCandidate(
                            project.projectId, it.createTime, it.serviceId, it.revisionId, it.url, it.createdBy, it.location,
                        )
                    }
            }
        }
        // Deployer-email filter (case-insensitive), then time direction: the upload always happens
        // AFTER the deploy, so a deploy created after the collection is the next deploy. The
        // closest match is the newest survivor, across all candidate projects.
        val closest = candidates
            .filter { it.createdBy.equals(input.userEmail, ignoreCase = true) }
            .filter { it.createTime.isBefore(collectTimestamp) }
            .maxByOrNull { it.createTime } ?: return MatchResult.NoMatch

        // Time window: distinguishes a plausible match from an unrelated old deploy.
        if (closest.createTime.isBefore(collectTimestamp.minus(window))) {
            return MatchResult.NoMatch
        }
        val existing = deployLinkRepository.get(
            closest.projectId, input.deployType, closest.location, closest.serviceId, closest.versionId,
        ) ?: return MatchResult.Matched(closest, replaced = null)
        if (existing.deployTimestamp == closest.createTime) return MatchResult.AlreadyLinked(existing)
        // Same identity, different deploy: a redeploy that reused the version id. The new link
        // replaces the existing one (createLink deletes the replaced link's storage folder).
        return MatchResult.Matched(closest, replaced = existing)
    }

    private fun createLink(
        input: Input,
        deploy: DeployCandidate,
        collectTimestamp: Instant,
        replaced: DeployLink?,
    ): Output.Created {
        val link = DeployLink(
            projectId = deploy.projectId,
            deployType = input.deployType,
            serviceId = deploy.serviceId,
            versionId = deploy.versionId,
            location = deploy.location,
            storageFolder = input.directoryName,
            userEmail = input.userEmail,
            collectTimestamp = collectTimestamp,
            deployTimestamp = deploy.createTime,
            url = deploy.url,
        )
        // Saving first: a failure must never leave a link pointing at an already-deleted folder.
        deployLinkRepository.save(link)
        replaced?.let { old ->
            if (old.storageFolder != link.storageFolder) {
                try {
                    storageCleaner.delete(old.storageFolder)
                } catch (e: RuntimeException) {
                    logger.warn(
                        "Replaced deploy link ${old.keyName} (a new deploy reused its version id), " +
                            "but deleting its old storage folder ${old.storageFolder} failed: ${e.message}",
                    )
                }
            }
        }
        return Output.Created(link)
    }

    /** No-match: schedule the delayed re-run that links or deletes; report not linked for now. */
    private fun scheduleRetry(input: Input, collectTimestamp: Instant): Output.NotLinked {
        retryScheduler.schedule(RETRY_DELAY) { retry(input, collectTimestamp) }
        return Output.NotLinked
    }

    private fun unknownProject(input: Input): Output.UnknownProject {
        retryScheduler.schedule(RETRY_DELAY) { storageCleaner.delete(input.directoryName) }
        return Output.UnknownProject
    }

    private sealed interface MatchResult {
        data class Matched(val deploy: DeployCandidate, val replaced: DeployLink?) : MatchResult
        data class AlreadyLinked(val existing: DeployLink) : MatchResult
        data object NoMatch : MatchResult
    }

    /** Deploy fields the link needs, shared between the App Engine and Cloud Run listers. */
    private data class DeployCandidate(
        val projectId: String,
        val createTime: Instant,
        val serviceId: String,
        val versionId: String,
        val url: String?,
        val createdBy: String?,
        val location: String? = null,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(CreateDeployLinkUseCase::class.java)

        /** Delay before the re-run that either links the upload or deletes its storage folder. */
        val RETRY_DELAY: Duration = Duration.ofMinutes(5)

        /**
         * Extracts the epoch-millis collection timestamp from a `<user>_<deployType>_<epochMillis>`
         * directory name (see `collector-java`'s `Context.createOutputDirectory`).
         *
         * @throws domain.InvalidDirectoryNameException when the name does not follow the convention.
         */
        internal fun parseCollectTimestamp(directoryName: String, deployType: DeployType): Instant {
            val parts = directoryName.split('_')
            if (parts.size < 3) {
                throw InvalidDirectoryNameException(
                    "Directory name \"$directoryName\" does not follow the <user>_<deployType>_<epochMillis> convention",
                )
            }
            val (user, nameDeployType, millis) = listOf(parts[0], parts[1], parts.last())
            if (!user.matches(Regex("[A-Za-z0-9._-]+")) || nameDeployType != deployType.name || millis.toLongOrNull() == null) {
                throw InvalidDirectoryNameException(
                    "Directory name \"$directoryName\" does not follow the <user>_<deployType>_<epochMillis> convention",
                )
            }
            return Instant.ofEpochMilli(millis.toLong())
        }
    }
}
