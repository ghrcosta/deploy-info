package tests.fakes

import application.StorageCleaner

/** Recording [StorageCleaner] that just records which folders were deleted. */
class FakeStorageCleaner : StorageCleaner {

    val deletedFolders = mutableListOf<String>()

    override fun delete(storageFolder: String) {
        deletedFolders.add(storageFolder)
    }
}
