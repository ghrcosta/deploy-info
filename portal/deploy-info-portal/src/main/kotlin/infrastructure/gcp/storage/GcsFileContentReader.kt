package infrastructure.gcp.storage

import application.FileContentReader
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import domain.StorageReadException
import java.nio.charset.StandardCharsets

/**
 * Reads individual files from a collector upload's folder in the configured bucket, using the Cloud
 * Storage client from the Spring Cloud GCP Storage starter. The [Storage] client is resolved
 * lazily, so the portal still starts (and the tests still run) without Storage credentials.
 *
 * All GCP failures are wrapped in [StorageReadException] so the application layer can surface them
 * without depending on the Cloud Storage SDK.
 */
class GcsFileContentReader(
    private val storageProvider: () -> Storage,
    private val bucketName: String,
) : FileContentReader {

    override fun readText(storageFolder: String, fileName: String): String? {
        val blobId = BlobId.of(bucketName, "${folderPrefix(storageFolder)}$fileName")
        return try {
            val blob = storageProvider().get(blobId) ?: return null
            String(blob.getContent(), StandardCharsets.UTF_8)
        } catch (exception: StorageException) {
            throw StorageReadException("Failed to read '$fileName' in folder '$storageFolder'", exception)
        }
    }

    override fun listFileNames(storageFolder: String): List<String> {
        return try {
            storageProvider().list(bucketName, Storage.BlobListOption.prefix(folderPrefix(storageFolder)))
                .iterateAll().map { it.getName() }
        } catch (exception: StorageException) {
            throw StorageReadException("Failed to list folder '$storageFolder'", exception)
        }
    }

    /** A nonexistent folder lists as empty, which the content capability treats as not found. */
    private fun folderPrefix(storageFolder: String): String =
        if (storageFolder.endsWith("/")) storageFolder else "$storageFolder/"
}
