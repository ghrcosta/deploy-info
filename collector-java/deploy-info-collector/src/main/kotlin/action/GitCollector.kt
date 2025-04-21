package io.github.ghrcosta.action

import io.github.ghrcosta.util.*
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.regex.Matcher


class GitCollector {
    fun execute() {
        try {
            // Save the "git status" devs are used to, will be visible on the portal
            val gitStatusOutput = executeCommand("git status --long --branch --untracked-files=normal")
            val gitStatusOutputFile = createEmptyFile("git-status.txt")
            gitStatusOutputFile.writeText(gitStatusOutput)

            // Save the "git log" devs are used to, will be visible on the portal
            val gitLogOutput = executeCommand("git log --oneline")
            val gitLogOutputFile = createEmptyFile("git-log.txt")
            gitLogOutputFile.writeText(gitLogOutput)

            // "git status --porcelain" is better for automations, use it to list (and process) modified files
            val gitStatusPorcelain = executeCommand("git status --porcelain --untracked-files=normal --no-renames")
            val filePaths = extractFilePaths(gitStatusPorcelain)
            filePaths.forEach { filePath ->
                val fullFilePath = parsePathFromProjectRootParent(filePath)
                val gitDiffOutput = executeCommand("git --no-pager diff \"${fullFilePath}\"")
                if (gitDiffOutput.isBlank()) {
                    val fileContent = File(fullFilePath).getContents()
                    saveContentToUuidFile(filePath, fileContent)
                } else {
                    saveContentToUuidFile(filePath, gitDiffOutput)
                }
            }
        } catch (e: IOException) {
            Logger.e("Error: ${e.message}")
            val errorOutputFile = createEmptyFile("error.txt")
            errorOutputFile.writeText("Error: ${e.message}")
        }
    }

    private fun extractFilePaths(gitStatusOutput: String): List<String> {
        val gitStatusFilePathPattern = "^[A-Z?!\\s]{2}\\s+(.*)$".toRegex(RegexOption.MULTILINE)
        val filePaths = mutableListOf<String>()

        gitStatusFilePathPattern.findAll(gitStatusOutput).forEach { match ->
            val filePath = match.groupValues[1].trim()
                .replace("\"", "") // To support names with spaces
            if (filePath.isNotEmpty() && File(parsePathFromProjectRootParent(filePath)).isFile) {
                filePaths.add(filePath)
            }
        }
        return filePaths
    }

    private fun parsePathFromProjectRootParent(filePath: String): String {
        val escapedFileSeparator = Matcher.quoteReplacement(File.separator)
        val basePath = "${Context.get().projectRootDir.parent}${File.separator}${filePath}"
        return basePath.replace("\\\\/".toRegex(), escapedFileSeparator)
    }

    private fun saveContentToUuidFile(filePath: String, content: String) {
        val uuid = UUID.randomUUID().toString()
        val gitFile = createEmptyFile(uuid)
        gitFile.writeText(content)
        val parsedPath = "/${filePath}".replace("\\", "/").replace("//", "/")
        Context.get().gitUuidMap.put(uuid, parsedPath)
    }
}