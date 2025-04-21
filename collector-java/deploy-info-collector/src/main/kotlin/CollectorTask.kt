package io.github.ghrcosta

import io.github.ghrcosta.action.ExtraFilesCollector
import io.github.ghrcosta.action.GitCollector
import io.github.ghrcosta.action.PortalTrigger
import io.github.ghrcosta.action.Uploader
import io.github.ghrcosta.util.Context
import io.github.ghrcosta.util.Logger
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

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
        description = "Maximum size of files that can be included. Default is 50KB."
    )
    @get:Optional
    abstract val maxFileSize: Property<Int>

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
        description = "Name of the Cloud Storage bucket where files will be stored."
    )
    abstract val storageBucket: Property<String>

    @get:Input
    @get:Option(
        description = "Type of deploy. Supported values are 'GAE' and 'RUN'."
    )
    abstract val deployType: Property<DeployType>
    @Suppress("unused") enum class DeployType { GAE, RUN }

    @TaskAction
    fun run() {
        TaskImpl(
            project = project,
            maxFileSize = maxFileSize.orNull,
            collectGitStatus = collectGitStatus.getOrElse(true),
            extraFilesToCollect = extraFilesToCollect.orNull,
            storageBucketName = storageBucket.get(),
            deployType = deployType.get(),
        ).run()
    }

    class TaskImpl(
        private val project: Project,
        private val maxFileSize: Int?,
        private val collectGitStatus: Boolean,
        private val extraFilesToCollect: List<String>?,
        private val storageBucketName: String,
        private val deployType: DeployType,
    ) {
        fun run() {
            Logger.init(project)
            Context.init(project, deployType, maxFileSize)

            collectGitStatusData()
            collectExtraFiles()
            uploadFiles()
            triggerPortalProcessing()

            Logger.i("Done!")
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

        private fun uploadFiles() {
            Uploader(storageBucketName).execute()
        }

        private fun triggerPortalProcessing() {
            PortalTrigger().execute()
        }
    }
}