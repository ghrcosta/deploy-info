package application

/**
 * Reads the content of individual files in a collector upload's Cloud Storage folder (the bucket is
 * an infrastructure concern; this interface works with the folder name only).
 */
interface FileContentReader {

    /**
     * Returns the text content of one file in the folder, or null when the file does not exist.
     *
     * @throws domain.StorageReadException when the storage backend cannot be reached or fails.
     */
    fun readText(storageFolder: String, fileName: String): String?
}