package infrastructure.gcp.storage

import application.StorageCleaner
import com.google.cloud.storage.Storage
import com.google.cloud.storage.Storage.BlobListOption

/**
 * Deletes every object under a collector upload's folder prefix in the configured bucket, using the
 * Cloud Storage client from the Spring Cloud GCP Storage starter. The [Storage] client is resolved
 * lazily, so the portal still starts (and the tests still run) without Storage credentials.
 */
class GcsStorageCleaner(
    private val storageProvider: () -> Storage,
    private val bucketName: String,
) : StorageCleaner {

    override fun delete(storageFolder: String) {
        val prefix = if (storageFolder.endsWith("/")) storageFolder else "$storageFolder/"
        val blobIds = storageProvider()
            .list(bucketName, BlobListOption.prefix(prefix))
            .iterateAll()
            .map { it.blobId }
            .toList()
        if (blobIds.isNotEmpty()) {
            storageProvider().delete(*blobIds.toTypedArray())
        }
    }
}

