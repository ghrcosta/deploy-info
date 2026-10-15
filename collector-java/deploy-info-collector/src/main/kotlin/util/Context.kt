package io.github.ghrcosta.util

import io.github.ghrcosta.CollectorTask
import java.io.File
import java.time.Instant

class Context(
    /** Base collector directory */
    val baseDir: File,
    /** Files in this directory will be uploaded to the portal */
    val outputDir: File,
    /** Root directory of the project, used as a base to search files to be collected */
    val projectRootDir: File,
    /** Files larger than this size will not be collected */
    val maxFileSize: Int,
    /** Maximum number of entries collected from `git log --oneline` by [io.github.ghrcosta.action.GitCollector] */
    val maxGitLogLines: Int,
    /** The gcloud account that performed the deployment and the collection */
    val userEmail: String,
    /** Map of UUID for files collected by [io.github.ghrcosta.action.GitCollector], their real path and git state (new/modified) */
    val gitUuidMap: MutableMap<String, GitCollectedFile> = mutableMapOf(),
    /** Map of UUID for files collected by [io.github.ghrcosta.action.ExtraFilesCollector] and their real path */
    val extraUuidMap: MutableMap<String,String> = mutableMapOf(),
) {

    companion object {
        private lateinit var instance: Context

        fun get(): Context = instance

        fun init(
            rootDir: File,
            buildDir: File,
            deployType: CollectorTask.DeployType,
            maxFileSizeInKb: Int? = null,
            maxGitLogLines: Int? = null,
        ) {
            val baseDir = createBaseDirectory(buildDir)

            val gcloudEmail = executeCommandOrThrowException("gcloud config get-value account", baseDir).stdout
            val now = Instant.now().toEpochMilli()

            val outputDir = createOutputDirectory(baseDir, deployType, gcloudEmail, now)

            instance = Context(
                baseDir = baseDir,
                outputDir = outputDir,
                projectRootDir = rootDir,
                maxFileSize = (maxFileSizeInKb ?: 50) * 1024, // value in KB, converted to bytes
                maxGitLogLines = maxGitLogLines ?: 30,
                userEmail = gcloudEmail
            )
        }

        private fun createBaseDirectory(buildDir: File): File =
            createEmptyDirectory("collector", buildDir)

        private fun createOutputDirectory(
            baseDir: File,
            deployType: CollectorTask.DeployType,
            gcloudEmail: String,
            now: Long,
        ): File {
            val gcloudUser = gcloudEmail.substringBefore("@")
            val dirName = "${gcloudUser}_${deployType.name}_${now}"
            return createEmptyDirectory(dirName, baseDir)
        }


    }
}
