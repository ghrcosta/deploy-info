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

    Logger.d("Executing command: ${commandForOS}")

    // Need to read both stdout and stderr to prevent the process from getting stuck waiting for its buffer to be
    // read. Also, cannot redirect stderr into stdout because it messes up the output of some commands.
    val stdoutFile = createEmptyFile("stdout", tempDir)
    val stderrFile = createEmptyFile("stderr", tempDir)

    val process = ProcessBuilder(tokenizeCommand(commandForOS))
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

/**
 * Splits a command string into its arguments: sections enclosed in double quotes become a single argument and the
 * quotes are removed. This is needed because [ProcessBuilder] executes the command without a shell, so quotes would
 * otherwise be passed literally to the executable (e.g. `git --no-pager diff "/path with space/file"` would make git
 * look for a file named `"/path` and never match).
 */
internal fun tokenizeCommand(command: String): List<String> =
    Regex("\"([^\"]*)\"|(\\S+)").findAll(command)
        .map { match -> if (match.groupValues[1].isNotEmpty()) match.groupValues[1] else match.groupValues[2] }
        .toList()