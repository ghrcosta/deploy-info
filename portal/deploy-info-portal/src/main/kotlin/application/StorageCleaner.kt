package application

/** Deletes the Cloud Storage folder of a collector upload that could not be linked to a deploy. */
interface StorageCleaner {

    /** Deletes all objects stored under the given folder prefix in the configured bucket. */
    fun delete(storageFolder: String)
}
