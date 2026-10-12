package io.github.ghrcosta.action

import io.github.ghrcosta.util.Context
import io.github.ghrcosta.util.CsvUtils
import io.github.ghrcosta.util.Logger
import io.github.ghrcosta.util.createEmptyFile
import io.github.ghrcosta.util.executeCommandOrThrowException
import java.nio.charset.StandardCharsets

class Uploader(private val storageBucketName: String) {
    fun execute() {
        val gitUuidMap = Context.get().gitUuidMap
        if (gitUuidMap.isNotEmpty()) {
            val gitUuidCsvFile = createEmptyFile("uuid-git.csv")
            gitUuidCsvFile.writer(StandardCharsets.UTF_8).use { writer ->
                writer.write("uuid,filepath,status\n")
                gitUuidMap.forEach { (uuid, collectedFile) ->
                    writer.write("${CsvUtils.escape(uuid)},${CsvUtils.escape(collectedFile.filepath)},${CsvUtils.escape(collectedFile.status.wireValue)}\n")
                }
            }
        }

        val extraUuidProperties = Context.get().extraUuidMap.toProperties()
        if (extraUuidProperties.isNotEmpty()) {
            val extraUuidPropertiesFile = createEmptyFile("uuid-extra.properties")
            extraUuidPropertiesFile.writer(StandardCharsets.UTF_8).use {
                extraUuidProperties.store(it, null)
            }
        }

        Logger.i("Uploading files...")
        val origin = Context.get().outputDir.absolutePath
        val result = executeCommandOrThrowException("gcloud storage cp --recursive ${origin} gs://${storageBucketName}")

        val errors = result.stderr.lines().filter { it.startsWith("ERROR") }.distinct()
        if (errors.isNotEmpty()) {
            throw RuntimeException("Upload command returned errors:\n${errors.joinToString("\n")}")
        }
    }
}