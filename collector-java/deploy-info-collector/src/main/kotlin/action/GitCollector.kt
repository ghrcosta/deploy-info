package io.github.ghrcosta.action

import io.github.ghrcosta.util.*
import java.io.File
import java.io.IOException
import java.util.*


class GitCollector {
    fun execute() {
        Logger.i("Collecting diff data...")
        try {
            val gitStatusOutput = executeCommand("git status --long --branch --untracked-files=normal")
            val gitStatusOutputFile = createEmptyFile("git-status.txt")
            gitStatusOutputFile.writeText(gitStatusOutput)

            val gitLogOutput = executeCommand("git log --oneline")
            val gitLogOutputFile = createEmptyFile("git-log.txt")
            gitLogOutputFile.writeText(gitLogOutput)

            // "git status --porcelain" is better for automations, use it to list (and process) files
            val gitStatusPorcelain = executeCommand("git status --porcelain --untracked-files=normal --no-renames")
            val repoRoot = getGitRepoRoot()
            val changedFiles = extractChangedFiles(gitStatusPorcelain, repoRoot)
            changedFiles.forEach { changedFile ->
                val gitDiffOutput = if (changedFile.status == GitFileStatus.MODIFIED) {
                    executeCommand("git --no-pager diff \"${changedFile.fullFilePath}\"")
                } else ""
                if (gitDiffOutput.isBlank()) {
                    val fileContent = File(changedFile.fullFilePath).getContents()
                    saveContentToUuidFile(changedFile.repoRelativePath, changedFile.status, fileContent)
                } else {
                    saveContentToUuidFile(changedFile.repoRelativePath, changedFile.status, gitDiffOutput)
                }
            }
        } catch (e: IOException) {
            Logger.e("Error: ${e.message}")
            val errorOutputFile = createEmptyFile("error.txt")
            errorOutputFile.writeText("Error: ${e.message}")
        }
    }

    /** One entry of `git status --porcelain`: the file path plus its git state. */
    private data class ChangedFile(
        /** Absolute path of the file, used for the file operations (diff, read, existence check). */
        val fullFilePath: String,
        /** Path of the file relative to the git repository root, as reported by porcelain (with "/" separators). */
        val repoRelativePath: String,
        val status: GitFileStatus,
    )

    /** Parses `git status --porcelain` output (state classification: [GitFileStatus.fromPorcelainCode]). */
    private fun extractChangedFiles(gitStatusOutput: String, repoRoot: File): List<ChangedFile> {
        val gitStatusFilePathPattern = "^([A-Z?!\\s]{2})\\s+(.*)$".toRegex(RegexOption.MULTILINE)
        val changedFiles = mutableListOf<ChangedFile>()

        gitStatusFilePathPattern.findAll(gitStatusOutput).forEach { match ->
            val filePath = match.groupValues[2].trim()
                .replace("\"", "") // To support names with spaces
            if (filePath.isNotEmpty()) {
                val fullFilePath = parsePathFromRepoRoot(filePath, repoRoot)
                if (File(fullFilePath).isFile) {
                    changedFiles.add(ChangedFile(fullFilePath, repoRelativePath = filePath, status = GitFileStatus.fromPorcelainCode(match.groupValues[1])))
                }
            }
        }
        return changedFiles
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

    /**
     * Records the file content under a fresh UUID-named file in the output directory, mapping the UUID to the
     * file's path relative to the git repository root (with a leading "/", as `git status --porcelain` reports
     * it) and its git state. This relative path is what ends up in `uuid-git.csv` (and is displayed in the portal).
     */
    private fun saveContentToUuidFile(repoRelativePath: String, status: GitFileStatus, content: String) {
        val uuid = UUID.randomUUID().toString()
        val gitFile = createEmptyFile(uuid)
        gitFile.writeText(content)
        val parsedPath = "/${repoRelativePath}".replace("\\", "/").replace("//", "/")
        Context.get().gitUuidMap[uuid] = GitCollectedFile(filepath = parsedPath, status = status)
    }
}