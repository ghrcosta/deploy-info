package io.github.ghrcosta

import io.github.ghrcosta.action.BucketLookup
import io.github.ghrcosta.action.GcloudAccessTokenProvider
import io.github.ghrcosta.action.ExtraFilesCollector
import io.github.ghrcosta.action.GitCollector
import io.github.ghrcosta.action.PortalTrigger
import io.github.ghrcosta.action.Uploader
import io.github.ghrcosta.util.Context
import io.github.ghrcosta.util.Logger
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Executes external tools (git, gcloud) and uploads data; not cacheable")
abstract class CollectorTask : DefaultTask() {
    init {
        description = "Execute collector"
        group = "deployInfo"
    }

    companion object {
        const val TASK_NAME = "deployInfoCollect"
    }

    @get:Input
    @get:Option(
        description = "Maximum size of files that can be included, in KB. Default is 50."
    )
    @get:Optional
    abstract val maxFileSize: Property<Int>

    @get:Input
    @get:Option(
        description = "Maximum number of lines collected from 'git log --oneline'. Default is 30."
    )
    @get:Optional
    abstract val maxGitLogLines: Property<Int>

    @get:Input
    @get:Option(
        description = "If git-related data must be collected. Default is 'true'."
    )
    @get:Optional
    abstract val collectGitStatus: Property<Boolean>

    @get:Input
    @get:Option(
        description = "Extra files to include in the collection. Each item in the list must be a path relative to " +
                "this projects' root directory. Directories are not supported. Note that only the file itself will " +
                "be collected, without its parent directories, so if two files have the same name, only one will " +
                "be included."
    )
    @get:Optional
    abstract val extraFilesToCollect: ListProperty<String>

    @get:Input
    @get:Option(
        description = "Type of deploy. Supported values are 'GAE' and 'RUN'."
    )
    abstract val deployType: Property<DeployType>
    @Suppress("unused") enum class DeployType { GAE, RUN }

    @get:Input
    @get:Option(
        description = "URL of the portal backend, from which the upload bucket is resolved."
    )
    abstract val portalUrl: Property<String>

    @get:Input
    @get:Option(
        description = "List of GCP projects where the code may have been deployed."
    )
    abstract val projects: ListProperty<String>

    // Set by CollectorPlugin at configuration time (not accessible as an @Option/@Input): reading
    // Task.project at execution time is deprecated by Gradle (fails in Gradle 10).
    @get:Internal
    abstract val projectRootDir: Property<File>

    @get:Internal
    abstract val buildDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val (trimmedPortalUrl, trimmedProjects, trimmedExtraFiles) = trimmedInputs()

        validateParameters(trimmedPortalUrl, trimmedProjects)
        Logger.init(logger)
        TaskImpl(
            rootDir = projectRootDir.get(),
            buildDir = buildDirectory.get().asFile,
            maxFileSizeInKb = maxFileSize.orNull,
            maxGitLogLines = maxGitLogLines.orNull,
            collectGitStatus = collectGitStatus.getOrElse(true),
            extraFilesToCollect = trimmedExtraFiles,
            portalUrl = trimmedPortalUrl,
            deployType = deployType.get(),
            projects = trimmedProjects,
        ).run()
    }

    /** Removes the surrounding blank space of every string input, so it is ignored by validation and the run. */
    internal fun trimmedInputs(): Triple<String, List<String>, List<String>?> = Triple(
        portalUrl.get().trim(),
        projects.get().map { it.trim() },
        extraFilesToCollect.orNull?.map { it.trim() },
    )

    internal fun validateParameters(portalUrl: String, projectList: List<String>) {
        if (portalUrl.isBlank())
            throw GradleException("'portalUrl' must not be empty.")
        if (projectList.isEmpty())
            throw GradleException("'projects' must not be empty: list at least one GCP project where the code may be deployed.")
        projectList.find { it.isBlank() }?.let {
            throw GradleException("'projects' must not contain empty values.")
        }
        maxFileSize.orNull?.let { maxSizeInKb ->
            if (maxSizeInKb < 1)
                throw GradleException("'maxFileSize' must be at least 1 (in KB), got: '${maxSizeInKb}'.")
        }
        maxGitLogLines.orNull?.let { maxLines ->
            if (maxLines < 1)
                throw GradleException("'maxGitLogLines' must be at least 1, got: '${maxLines}'.")
        }
    }

    class TaskImpl(
        private val rootDir: File,
        private val buildDir: File,
        private val maxFileSizeInKb: Int?,
        private val maxGitLogLines: Int?,
        private val collectGitStatus: Boolean,
        private val extraFilesToCollect: List<String>?,
        private val portalUrl: String,
        private val deployType: DeployType,
        private val projects: List<String>,
    ) {
        private val accessTokenProvider = GcloudAccessTokenProvider()

        fun run() {
            Context.init(rootDir, buildDir, deployType, maxFileSizeInKb, maxGitLogLines)

            collectGitStatusData()
            collectExtraFiles()
            // One token for both portal calls (see GcloudAccessTokenProvider).
            val accessToken = accessTokenProvider.fetch()
            val bucketName = lookupBucket(accessToken)
            uploadFiles(bucketName)
            triggerPortalProcessing(accessToken)

            Logger.d("Done!")
        }

        private fun collectGitStatusData() {
            if (collectGitStatus) {
                GitCollector().execute()
            }
        }

        private fun collectExtraFiles() {
            extraFilesToCollect?.let {
                ExtraFilesCollector(it).execute()
            }
        }

        private fun lookupBucket(accessToken: String?): String {
            val bucketName = BucketLookup(portalUrl, accessToken).execute()
            Logger.i("Uploads will go to Cloud Storage bucket '${bucketName}'.")
            return bucketName
        }

        private fun uploadFiles(bucketName: String) {
            Uploader(bucketName).execute()
        }

        private fun triggerPortalProcessing(accessToken: String?) {
            val context = Context.get()
            PortalTrigger(
                portalUrl = portalUrl,
                directoryName = context.outputDir.name,
                projects = projects,
                deployType = deployType.name,
                userEmail = context.userEmail,
                accessToken = accessToken,
            ).execute()
        }
    }
}
