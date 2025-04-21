package io.github.ghrcosta.action

import io.github.ghrcosta.util.Context
import io.github.ghrcosta.util.createEmptyFile
import io.github.ghrcosta.util.executeCommandOrThrowException
import java.nio.charset.StandardCharsets

class Uploader(private val storageBucketName: String) {
    fun execute() {
        val gitUuidProperties = Context.get().gitUuidMap.toProperties()
        if (gitUuidProperties.isNotEmpty()) {
            val gitUuidPropertiesFile = createEmptyFile("uuid-git.properties")
            gitUuidPropertiesFile.writer(StandardCharsets.UTF_8).use {
                gitUuidProperties.store(it, null)
            }
        }

        val extraUuidProperties = Context.get().extraUuidMap.toProperties()
        if (extraUuidProperties.isNotEmpty()) {
            val extraUuidPropertiesFile = createEmptyFile("uuid-extra.properties")
            extraUuidPropertiesFile.writer(StandardCharsets.UTF_8).use {
                extraUuidProperties.store(it, null)
            }
        }

        val origin = Context.get().outputDir.absolutePath
        val result = executeCommandOrThrowException("gcloud storage cp --recursive ${origin} gs://${storageBucketName}")

        val errors = result.stderr.lines().filter { it.startsWith("ERROR") }.distinct()
        if (errors.isNotEmpty()) {
            throw RuntimeException("Upload command returned errors:\n${errors.joinToString("\n")}")
        }
    }
}