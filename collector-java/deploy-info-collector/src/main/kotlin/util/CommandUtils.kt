package io.github.ghrcosta.util

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

fun executeCommand(
    command: String,
    tempDir: File = Context.get().baseDir
): String {
    return try {
        executeCommandOrThrowException(command, tempDir).stdout
    } catch (e: IOException) {
        val errorOutput = e.message ?: "Unknown error"
        "Command failed: ${command}\nError: ${errorOutput}"
    }
}

fun executeCommandOrThrowException(
    command: String,
    tempDir: File = Context.get().baseDir,
): CommandResult {
    val executable = command.substringBefore(" ")
    val executableForOS = getExecutableForOS(executable)
    val commandForOS = command.replaceFirst(executable, executableForOS)

    Logger.i("Executing command: ${commandForOS}")

    // Need to read both stdout and stderr to prevent the process from getting stuck waiting for its buffer to be
    // read. Also, cannot redirect stderr into stdout because it messes up the output of some commands.
    val stdoutFile = createEmptyFile("stdout", tempDir)
    val stderrFile = createEmptyFile("stderr", tempDir)

    val process = ProcessBuilder(commandForOS.split(" "))
        .redirectError(stderrFile)
        .redirectOutput(stdoutFile)
        .start()

    process.waitFor()

    val stdout = stdoutFile.readText(StandardCharsets.UTF_8).trimEnd()
    stdoutFile.delete()
    val stderr = stderrFile.readText(StandardCharsets.UTF_8).trimEnd()
    stderrFile.delete()

    return CommandResult(stdout, stderr)
}

class CommandResult(val stdout: String, val stderr: String)