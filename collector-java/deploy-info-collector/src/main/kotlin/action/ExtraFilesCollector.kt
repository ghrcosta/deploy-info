package io.github.ghrcosta.action

import io.github.ghrcosta.util.Context
import io.github.ghrcosta.util.Logger
import io.github.ghrcosta.util.isBinary
import io.github.ghrcosta.util.isLarge
import java.io.File
import java.util.UUID
import java.util.regex.Matcher

class ExtraFilesCollector(private val extraFiles: List<String>) {
    fun execute() {
        extraFiles
            .map { "/${it}".replace("\\", "/").replace("//", "/") }
            .distinct()
            .forEach { filePath ->
                val file = parsePathFromProjectRoot(filePath)
                if (file.exists() && file.isFile && !file.isBinary() && !file.isLarge()) {
                    val uuid = UUID.randomUUID().toString()
                    val copyFile = File(Context.get().outputDir, uuid)
                    file.copyTo(copyFile, overwrite = true)
                    Context.get().extraUuidMap.put(uuid, filePath)
                } else {
                    Logger.e("File ${file.absolutePath} is invalid (" +
                            "exists=${file.exists()}, " +
                            "isFile=${file.isFile}, " +
                            "isBinary=${if (file.isFile) file.isBinary() else "---"}, " +
                            "isLarge=${if (file.isFile) file.isLarge() else "---"}" +
                            "), will be ignored!"
                    )
                }
            }
    }

    private fun parsePathFromProjectRoot(filePath: String): File {
        val escapedFileSeparator = Matcher.quoteReplacement(File.separator)
        val path = "${Context.get().projectRootDir.absolutePath}${File.separator}${filePath}"
        val parsedPath = path.replace("\\\\/".toRegex(), escapedFileSeparator)
        return File(parsedPath)
    }
}