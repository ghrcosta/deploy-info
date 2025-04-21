package io.github.ghrcosta.util

/** Executables that must be called differently depending on the OS. Key is Linux, value is Windows. */
private val executableWindowsMap = mapOf(
    "gcloud" to "gcloud.cmd",
)

fun getExecutableForOS(linuxExecutable: String): String =
    when {
        isWindows() -> executableWindowsMap.getOrDefault(linuxExecutable.lowercase(), linuxExecutable)
        else -> linuxExecutable
    }

private fun isWindows(): Boolean =
    "windows" in System.getProperty("os.name").lowercase()