package application.linking

import application.*
import domain.*
import java.time.Duration
import java.time.Instant

/**
 * Links a collector upload (a Cloud Storage folder) to the GCP deploy it was built for.
 *
 * Matching rules: only deploys created by the collector's user are candidates, the deploy must have
 * been created at (or before) the collection timestamp, and within the configured window before it;
 * among the candidates the closest one below the collection timestamp wins (the listers return
 * deploys newest first, so it is the first survivor of the filters).
 *
 * When no plausible match is found, the matching is retried once after a short delay (a deploy may
 * appear late, or the listing may fail transiently) before the upload's storage folder is deleted.
 * A deploy identity that is already linked is never re-linked or overwritten.
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

    /** The data the collector can provide; the rest is discovered through the GCP listing. */
    data class Input(
        val projectName: String,
        val deployType: DeployType,
        val storageFolder: String,
        val userEmail: String,
        val collectTimestamp: Instant,
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
        val project = projectRepository.get(input.projectName) ?: return unknownProject(input)
        return try {
            when (val result = matchDeploy(project, input)) {
                is MatchResult.Matched -> createLink(project.name, input, result.deploy)
                is MatchResult.AlreadyLinked -> Output.AlreadyLinked(result.existing)
                MatchResult.NoMatch -> scheduleRetry(input)
            }
        } catch (_: GcpListingException) {
            scheduleRetry(input)
        } catch (_: CredentialsException) {
            scheduleRetry(input)
        }
    }

    /** Re-runs the matching once. If it still finds no match, deletes the upload's storage folder. */
    private fun retry(input: Input) {
        val project = projectRepository.get(input.projectName)
        val result: MatchResult? = if (project == null) {
            null
        } else {
            try {
                matchDeploy(project, input)
            } catch (_: GcpListingException) {
                null
            } catch (_: CredentialsException) {
                null
            }
        }
        when (result) {
            is MatchResult.Matched -> createLink(project!!.name, input, result.deploy)
            is MatchResult.AlreadyLinked -> Unit
            null, MatchResult.NoMatch -> storageCleaner.delete(input.storageFolder)
        }
    }

    private fun matchDeploy(project: Project, input: Input): MatchResult {
        val candidates = when (input.deployType) {
            DeployType.GAE ->
                appEngineLister.listAllDeploys(project).map {
                    DeployCandidate(it.createTime, it.serviceId, it.versionId, it.url, it.createdBy)
                }
            DeployType.RUN ->
                cloudRunLister.listAllDeploys(project).map {
                    DeployCandidate(
                        it.createTime, it.serviceId, it.revisionId, it.url, it.createdBy, it.location,
                    )
                }
        }
        // Deployer-email filter (case-insensitive), then time direction: the upload always happens
        // AFTER the deploy, so a deploy created after the collection is the next deploy. The
        // listers return deploys newest first, so the first survivor is the closest match.
        val closest = candidates
            .filter { it.createdBy.equals(input.userEmail, ignoreCase = true) }
            .firstOrNull { !it.createTime.isAfter(input.collectTimestamp) } ?: return MatchResult.NoMatch

        // Time window: distinguishes a plausible match from an unrelated old deploy.
        if (closest.createTime.isBefore(input.collectTimestamp.minus(window))) {
            return MatchResult.NoMatch
        }
        val existing = deployLinkRepository.get(
            input.projectName, input.deployType, closest.location, closest.serviceId, closest.versionId,
        ) ?: return MatchResult.Matched(closest)
        return MatchResult.AlreadyLinked(existing)
    }

    private fun createLink(
        projectName: String,
        input: Input,
        deploy: DeployCandidate,
    ): Output.Created {
        val link = DeployLink(
            projectName = projectName,
            deployType = input.deployType,
            serviceId = deploy.serviceId,
            versionId = deploy.versionId,
            location = deploy.location,
            storageFolder = input.storageFolder,
            userEmail = input.userEmail,
            collectTimestamp = input.collectTimestamp,
            url = deploy.url,
        )
        deployLinkRepository.save(link)
        return Output.Created(link)
    }

    /** No-match: schedule the delayed re-run that links or deletes; report not linked for now. */
    private fun scheduleRetry(input: Input): Output.NotLinked {
        retryScheduler.schedule(RETRY_DELAY) { retry(input) }
        return Output.NotLinked
    }

    private fun unknownProject(input: Input): Output.UnknownProject {
        retryScheduler.schedule(RETRY_DELAY) { storageCleaner.delete(input.storageFolder) }
        return Output.UnknownProject
    }

    private sealed interface MatchResult {
        data class Matched(val deploy: DeployCandidate) : MatchResult
        data class AlreadyLinked(val existing: DeployLink) : MatchResult
        data object NoMatch : MatchResult
    }

    /** Deploy fields the link needs, shared between the App Engine and Cloud Run listers. */
    private data class DeployCandidate(
        val createTime: Instant,
        val serviceId: String,
        val versionId: String,
        val url: String?,
        val createdBy: String?,
        val location: String? = null,
    )

    companion object {
        /** Delay before the re-run that either links the upload or deletes its storage folder. */
        val RETRY_DELAY: Duration = Duration.ofMinutes(5)
    }
}
