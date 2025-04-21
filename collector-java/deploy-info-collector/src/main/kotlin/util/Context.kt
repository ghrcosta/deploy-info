package io.github.ghrcosta.util

import io.github.ghrcosta.CollectorTask
import org.gradle.api.Project
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*

class Context(
    /** Base collector directory */
    val baseDir: File,
    /** Files in this directory will be uploaded to the portal */
    val outputDir: File,
    /** Root directory of the project, used as a base to search files to be collected */
    val projectRootDir: File,
    /** Files larger than this size will not be collected */
    val maxFileSize: Int,
    /** Map of UUID for files collected by [io.github.ghrcosta.action.GitCollector] and their real path */
    val gitUuidMap: MutableMap<String,String> = mutableMapOf(),
    /** Map of UUID for files collected by [io.github.ghrcosta.action.ExtraFilesCollector] and their real path */
    val extraUuidMap: MutableMap<String,String> = mutableMapOf(),
) {

    companion object {
        private lateinit var instance: Context

        fun get(): Context = instance

        fun init(project: Project, deployType: CollectorTask.DeployType, maxFileSize: Int? = null) {
            val baseDir = createBaseDirectory(project)

            val gcloudEmail = executeCommandOrThrowException("gcloud config get-value account", baseDir).stdout
            val now = Instant.now().toEpochMilli()

            val outputDir = createOutputDirectory(baseDir, deployType, gcloudEmail, now)

            createPropertiesFile(outputDir, deployType, gcloudEmail, now)

            instance = Context(
                baseDir = baseDir,
                outputDir = outputDir,
                projectRootDir = project.rootDir,
                maxFileSize = maxFileSize ?: (50 * 1024) // 50KB
            )
        }

        private fun createBaseDirectory(project: Project): File =
            createEmptyDirectory("collector", project.layout.buildDirectory.asFile.get())

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

        private fun createPropertiesFile(
            outputDir: File,
            deployType: CollectorTask.DeployType,
            gcloudEmail: String,
            now: Long,
        ) {
            val properties = Properties().apply {
                set("email", gcloudEmail)
                set("deploy", deployType.name)
                set("timestamp", now.toString())
            }
            val propertiesFile = createEmptyFile("collector.properties", outputDir)
            propertiesFile.writer(StandardCharsets.UTF_8).use {
                properties.store(it, null)
            }
        }
    }
}