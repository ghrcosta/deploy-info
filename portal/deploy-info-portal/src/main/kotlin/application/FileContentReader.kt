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

    /**
     * Returns the names of the files in the folder; empty when the folder does not exist or is
     * empty. The file-content capability uses this to distinguish an upload folder that is gone
     * (e.g. deleted by the cleanup sweep) from one that merely contains nothing readable.
     *
     * @throws domain.StorageReadException when the storage backend cannot be reached or fails.
     */
    fun listFileNames(storageFolder: String): List<String>
}
