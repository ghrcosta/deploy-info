package tests.fakes

import application.FileContentReader
import domain.StorageReadException

/**
 * In-memory [FileContentReader]: files are seeded per folder, reads can be made to fail like a
 * broken Cloud Storage connection would. A folder that was never seeded is absent for
 * [listFileNames], exactly like a missing Cloud Storage folder.
 */
class FakeFileContentReader : FileContentReader {

    /** Folder name -> (file name -> text content). */
    private val files = mutableMapOf<String, MutableMap<String, String>>()
    private var failAllReads = false

    fun seed(storageFolder: String, fileName: String, content: String) {
        files.getOrPut(storageFolder) { mutableMapOf() }[fileName] = content
    }

    /** Makes every subsequent read throw [StorageReadException] (until reset). */
    fun failAllReads() {
        failAllReads = true
    }

    override fun readText(storageFolder: String, fileName: String): String? {
        if (failAllReads) throw StorageReadException("Simulated storage failure")
        return files[storageFolder]?.get(fileName)
    }

    override fun listFileNames(storageFolder: String): List<String> {
        if (failAllReads) throw StorageReadException("Simulated storage failure")
        return files[storageFolder]?.keys?.toList() ?: emptyList()
    }
}
