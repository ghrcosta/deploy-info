package io.github.ghrcosta.action

import io.github.ghrcosta.util.*
import java.io.File
import java.io.IOException
import java.util.*


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
            val repoRoot = getGitRepoRoot()
            val filePaths = extractFilePaths(gitStatusPorcelain, repoRoot)
            filePaths.forEach { filePath ->
                val gitDiffOutput = executeCommand("git --no-pager diff \"${filePath}\"")
                if (gitDiffOutput.isBlank()) {
                    val fileContent = File(filePath).getContents()
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

    private fun extractFilePaths(gitStatusOutput: String, repoRoot: File): List<String> {
        val gitStatusFilePathPattern = "^[A-Z?!\\s]{2}\\s+(.*)$".toRegex(RegexOption.MULTILINE)
        val filePaths = mutableListOf<String>()

        gitStatusFilePathPattern.findAll(gitStatusOutput).forEach { match ->
            val filePath = match.groupValues[1].trim()
                .replace("\"", "") // To support names with spaces
            if (filePath.isNotEmpty()) {
                val fullFilePath = parsePathFromRepoRoot(filePath, repoRoot)
                if (File(fullFilePath).isFile) {
                    filePaths.add(fullFilePath)
                }
            }
        }
        return filePaths
    }

    /** Returns the root directory of the git repository, falling back to the project root. */
    private fun getGitRepoRoot(): File {
        val repoRootPath = executeCommand("git rev-parse --show-toplevel").trim()
        return if (repoRootPath.isNotEmpty()) File(repoRootPath) else Context.get().projectRootDir
    }

    /**
     * Converts a path relative to the git repository root (as reported by `git status --porcelain`) into an absolute
     * path. Porcelain paths always use "/" as separator, but [File] normalizes to the OS separator.
     */
    private fun parsePathFromRepoRoot(filePath: String, repoRoot: File): String =
        File(repoRoot, filePath).absolutePath

    private fun saveContentToUuidFile(filePath: String, content: String) {
        val uuid = UUID.randomUUID().toString()
        val gitFile = createEmptyFile(uuid)
        gitFile.writeText(content)
        val parsedPath = "/${filePath}".replace("\\", "/").replace("//", "/")
        Context.get().gitUuidMap[uuid] = parsedPath
    }
}