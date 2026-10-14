package infrastructure.gcp.storage

import application.StorageCleaner
import com.google.cloud.storage.Storage
import com.google.cloud.storage.Storage.BlobListOption

/** Deletes every object under a collector upload's folder prefix in the configured bucket. */
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

