package application.content

import application.DeployLinkRepository
import application.FileContentReader
import domain.*
import java.util.*

/**
 * Reads the file content (GIT section and extras) of a collector upload's Cloud Storage folder,
 * resolved through the deploy link matching the given deploy identity.
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
    private val deployLinkRepository: DeployLinkRepository,
    private val fileContentReader: FileContentReader,
) {

    /** The deploy-link identity of the version whose collected files should be read. */
    data class Input(
        val projectName: String,
        val deployType: DeployType,
        val location: String?,
        val serviceId: String,
        val versionId: String,
    )

    sealed class Output {
        /** The collected file content of the linked upload folder. */
        data class Content(val deployContent: DeployContent) : Output()

        /** No deploy link exists for the given identity. */
        data object LinkNotFound : Output()

        /** The Cloud Storage folder could not be read (a portal issue, not a user issue). */
        data object StorageError : Output()
    }

    fun execute(input: Input): Output {
        val link = deployLinkRepository.get(
            input.projectName, input.deployType, input.location, input.serviceId, input.versionId,
        ) ?: return Output.LinkNotFound
        return try {
            Output.Content(readContent(link.storageFolder))
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