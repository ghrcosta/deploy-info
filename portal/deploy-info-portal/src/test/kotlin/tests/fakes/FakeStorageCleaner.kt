package tests.fakes

import application.StorageCleaner

/** Recording [StorageCleaner] that just records which folders were deleted. */
class FakeStorageCleaner : StorageCleaner {

    val deletedFolders = mutableListOf<String>()

    /** When true, every delete throws instead of recording (simulating a Cloud Storage failure). */
    var throwOnEveryDelete: Boolean = false

    override fun delete(storageFolder: String) {
        if (throwOnEveryDelete) {
            throw RuntimeException("transient storage failure")
        }
        deletedFolders.add(storageFolder)
    }
}
