package application.content

import application.FileContentReader
import domain.*
import java.util.*

/**
 * Reads the file content (GIT section and extras) of a collector upload's Cloud Storage folder,
 * identified by the folder name the tree endpoint carries on its version nodes — so this use case
 * touches Cloud Storage only, never Datastore.
 *
 * The folder layout is the one produced by the collector; every part is optional (git files only
 * exist when the collector was configured to collect them, the uuid maps only when something was
 * collected). Layout contract: documentation/backend/deployment-content.md and
 * collector-java's documentation/collector-plugin.md.
 *
 * The application layer stays Spring-free (see CreateDeploymentLinkUseCase).
 */
class GetDeploymentContentUseCase(
    private val fileContentReader: FileContentReader,
) {

    /** The upload folder whose collected files should be read (the tree's version-node value). */
    data class Input(
        val storageFolder: String,
    )

    sealed class Output {
        /** The collected file content of the upload folder. */
        data class Content(val deploymentContent: DeploymentContent) : Output()

        /** The upload folder does not exist (or is empty) — e.g. deleted by the cleanup sweep. */
        data object FolderNotFound : Output()

        /** The Cloud Storage folder could not be read (a portal issue, not a user issue). */
        data object StorageError : Output()
    }

    fun execute(input: Input): Output {
        return try {
            if (fileContentReader.listFileNames(input.storageFolder).isEmpty()) {
                return Output.FolderNotFound
            }
            Output.Content(readContent(input.storageFolder))
        } catch (_: StorageReadException) {
            Output.StorageError
        }
    }

    /** Reads the whole folder; every missing part is simply absent from the result. */
    private fun readContent(storageFolder: String): DeploymentContent {
        val gitLog = fileContentReader.readText(storageFolder, GIT_LOG_FILE)
        val gitStatus = fileContentReader.readText(storageFolder, GIT_STATUS_FILE)
        val changes = readGitChanges(storageFolder)
        val extras = readMappedFiles(storageFolder)
        val git = if (gitLog == null && gitStatus == null && changes.isEmpty()) {
            null
        } else {
            GitContent(gitLog = gitLog, gitStatus = gitStatus, changes = changes)
        }
        return DeploymentContent(git = git, extras = extras)
    }

    /**
     * Reads the git-collected files: parses `uuid-git.csv` (header `uuid,filepath,status`; RFC 4180 —
     * file paths containing commas/quotes are quoted) and reads each uuid file's text. Rows with a
     * wrong shape or an unknown status, and entries referencing files that are missing from the
     * folder, are skipped (partial uploads must not fail the whole content read).
     */
    private fun readGitChanges(storageFolder: String): List<ContentFile> {
        val csvText = fileContentReader.readText(storageFolder, GIT_UUID_CSV) ?: return emptyList()
        return parseCsvRows(csvText)
            .drop(1) // the header row
            .mapNotNull { fields ->
                val uuid = fields.getOrNull(0)
                val filepath = fields.getOrNull(1)
                if (uuid.isNullOrEmpty() || filepath.isNullOrEmpty()) return@mapNotNull null
                val status = GitFileStatus.fromWireValue(fields.getOrNull(2)) ?: return@mapNotNull null
                val content = fileContentReader.readText(storageFolder, uuid) ?: return@mapNotNull null
                ContentFile(filepath = filepath, content = content, status = status)
            }
    }

    /**
     * Parses RFC 4180 CSV rows: fields separated by commas, optionally wrapped in double quotes with
     * embedded quotes doubled (line breaks inside quotes kept) — so a filepath containing a comma, a
     * quote or a line break cannot break the parsing.
     */
    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder()
        var currentRow = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val char = text[i]
            when {
                inQuotes && char == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"'); i += 1
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    currentRow.add(field.toString()); field = StringBuilder()
                }
                (char == '\n' || char == '\r') && !inQuotes -> {
                    if (char == '\r' && i + 1 < text.length && text[i + 1] == '\n') i += 1
                    currentRow.add(field.toString()); field = StringBuilder()
                    rows.add(currentRow); currentRow = mutableListOf()
                }
                else -> field.append(char)
            }
            i += 1
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            rows.add(currentRow)
        }
        return rows
    }

    /**
     * Reads the uuid-mapped files of one section: parses the `<uuid>=<path>` properties file and
     * reads each uuid file's text. Entries referencing files that are missing from the folder are
     * skipped (partial uploads must not fail the whole content read).
     */
    private fun readMappedFiles(storageFolder: String): List<ContentFile> {
        val propertiesText = fileContentReader.readText(storageFolder, EXTRA_UUID_PROPERTIES) ?: return emptyList()
        val uuidToPath = Properties().apply { load(propertiesText.byteInputStream()) }
        return uuidToPath.stringPropertyNames().mapNotNull { uuid ->
            val content = fileContentReader.readText(storageFolder, uuid) ?: return@mapNotNull null
            ContentFile(filepath = uuidToPath.getProperty(uuid), content = content)
        }
    }

    private companion object {
        /** File names as written by the collector into each upload folder. */
        const val GIT_LOG_FILE = "git-log.txt"
        const val GIT_STATUS_FILE = "git-status.txt"
        const val GIT_UUID_CSV = "uuid-git.csv"
        const val EXTRA_UUID_PROPERTIES = "uuid-extra.properties"
    }
}
