package io.github.ghrcosta.util

import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

fun File.getContents(): String {
    return when {
        this.isLarge() -> "<FILE IS TOO LARGE (${this.length()} bytes)>"
        this.isBinary() -> "<FILE IS BINARY>"
        else -> this.readText(StandardCharsets.UTF_8).ifBlank { "<FILE IS BLANK>" }
    }
}

fun File.isLarge() = length() > Context.get().maxFileSize

fun File.isBinary(): Boolean {
    // Retrieve mime type by establishing a connection to the file. We must close the connection manually to prevent the
    // application from locking the file. And since the closing is manual, we'll make the opening explicit too, even
    // though that is not needed (it would be called automatically by [connection.contentType]).
    val connection = toURI().toURL().openConnection()
    connection.connect()
    val mimeType = connection.contentType
    connection.inputStream.close()

    return when {
        mimeType.startsWith("text") -> false
        listOf("application", "content").none { mimeType.startsWith(it) } -> true
        listOf("json", "xml", "html", "yaml", "unknown").any { mimeType.contains(it) } -> false
        else -> true
    }
}

fun createEmptyFile(
    filename: String,
    destinationDir: File = Context.get().outputDir
): File {
    val newFile = File(destinationDir, filename)
    newFile.delete()
    newFile.createNewFile()
    return newFile
}

@OptIn(ExperimentalPathApi::class)
fun createEmptyDirectory(
    directoryName: String,
    destinationDir: File = Context.get().baseDir
): File {
    val newDir = File(destinationDir, "${directoryName}/")
    if (newDir.exists() && newDir.isDirectory) {
        // Ensure the directory is empty to prevent mixing files from different deploys
        newDir.toPath().deleteRecursively()
    }
    newDir.mkdirs()
    return newDir
}