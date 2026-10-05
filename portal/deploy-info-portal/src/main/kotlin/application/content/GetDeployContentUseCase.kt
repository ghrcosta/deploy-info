package application.content

import application.FileContentReader
import domain.ContentFile
import domain.DeployContent
import domain.GitContent
import domain.StorageReadException
import java.util.*

/**
 * Reads the file content (GIT section and extras) of a collector upload's Cloud Storage folder,
 * identified by the folder name the tree endpoint carries on its version nodes — so this use case
 * touches Cloud Storage only, never Datastore.
 *
 * The folder layout is the one produced by the collector (see the collector plugin documentation):
 * `git-log.txt` / `git-status.txt` at the top level, uuid-named files with their `uuid → path`
 * mappings in `uuid-git.properties` / `uuid-extra.properties`. Every part is optional: git files
 * only exist when the collector was configured to collect them, and the uuid maps only when
 * something was collected.
 *
 * The application layer stays Spring-free; all collaborators are constructor-injected interfaces.
 */
class GetDeployContentUseCase(
    private val fileContentReader: FileContentReader,
) {

    /** The upload folder whose collected files should be read (the tree's version-node value). */
    data class Input(
        val storageFolder: String,
    )

    sealed class Output {
        /** The collected file content of the upload folder. */
        data class Content(val deployContent: DeployContent) : Output()

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
    private fun readContent(storageFolder: String): DeployContent {
        val gitLog = fileContentReader.readText(storageFolder, GIT_LOG_FILE)
        val gitStatus = fileContentReader.readText(storageFolder, GIT_STATUS_FILE)
        val changes = readMappedFiles(storageFolder, GIT_UUID_PROPERTIES)
        val extras = readMappedFiles(storageFolder, EXTRA_UUID_PROPERTIES)
        val git = if (gitLog == null && gitStatus == null && changes.isEmpty()) {
            null
        } else {
            GitContent(gitLog = gitLog, gitStatus = gitStatus, changes = changes)
        }
        return DeployContent(git = git, extras = extras)
    }

    /**
     * Reads the uuid-mapped files of one section: parses the `<uuid>=<path>` properties file and
     * reads each uuid file's text. Entries referencing files that are missing from the folder are
     * skipped (partial uploads must not fail the whole content read).
     */
    private fun readMappedFiles(storageFolder: String, propertiesFile: String): List<ContentFile> {
        val propertiesText = fileContentReader.readText(storageFolder, propertiesFile) ?: return emptyList()
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
        const val GIT_UUID_PROPERTIES = "uuid-git.properties"
        const val EXTRA_UUID_PROPERTIES = "uuid-extra.properties"
    }
}
