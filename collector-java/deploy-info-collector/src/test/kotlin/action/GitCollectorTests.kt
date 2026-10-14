package action

import io.github.ghrcosta.CollectorTask
import io.github.ghrcosta.action.GitCollector
import io.github.ghrcosta.util.CommandResult
import io.github.ghrcosta.util.Context
import io.github.ghrcosta.util.GitCollectedFile
import io.github.ghrcosta.util.GitFileStatus
import io.github.ghrcosta.util.Logger
import io.github.ghrcosta.util.executeCommand
import io.github.ghrcosta.util.executeCommandOrThrowException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import org.gradle.api.logging.Logger as GradleLogger
import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.*

class GitCollectorTests {

    val testDir: File = File("testDir/").absoluteFile
    val buildDir: File = File(testDir, "buildDirTest/").absoluteFile
    val projectRootDir: File = File(testDir, "projectRootDirTest/").absoluteFile

    val gitStatusResponse =  "git status output"
    val gitLogResponse =  "git log output"
    val someFileRelatedContent = "some file related content"

    @BeforeTest
    fun setup() {
        testDir.mkdir()
        buildDir.mkdirs()
        projectRootDir.mkdirs()

        Logger.init(mockk<GradleLogger>(relaxed = true))
        Context.init(projectRootDir, buildDir, CollectorTask.DeployType.GAE)

        mockkStatic("io.github.ghrcosta.util.CommandUtilsKt")
        every { executeCommandOrThrowException("gcloud config get-value account", any()) } returns
                CommandResult(stdout = "user@email.com", stderr = "")
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun cleanup() {
        testDir.toPath().deleteRecursively()
    }

    @Test
    fun `Generate main files + for each 'git diff' that returns content, save it to a file`() {
        val file01 = File(projectRootDir, "file01").also { it.createNewFile() }
        val file02 = File(projectRootDir, "file02").also { it.createNewFile() }
        val file03 = File(projectRootDir, "file03").also { it.createNewFile() }
        val file04 = File(projectRootDir, "file04").also { it.createNewFile() }

        val paths = mutableListOf<String>()

        everyExecuteCommandAnswers(
            gitStatusPorcelainResponse = {
                """
                 M ${projectRootDir.name}/${file01.name}
                MM ${projectRootDir.name}/${file02.name}
                ?? ${projectRootDir.name}/${file03.name}
                AM ${projectRootDir.name}/${file04.name}
                """.trimIndent()
            },
            gitDiffResponse = { path: String ->
                paths.add(path)
                someFileRelatedContent
            }
        )

        GitCollector().execute()

        // 'git diff' is only executed for MODIFIED files; untracked/new files get their contents copied
        assertEquals(2, paths.size)
        assertGitFilesAreCorrect()

        val gitUuidMap = Context.get().gitUuidMap
        assertEquals(4, gitUuidMap.entries.size)
        assertFilesFromUuidMapExist(gitUuidMap)
        assertEquals(
            mapOf(
                "file01" to GitFileStatus.MODIFIED,
                "file02" to GitFileStatus.MODIFIED,
                "file03" to GitFileStatus.UNTRACKED,
                "file04" to GitFileStatus.NEW, // 'AM': the staged addition wins over the worktree modification
            ),
            gitUuidMap.values.associate { File(it.filepath).name to it.status },
        )
        // The recorded filepaths are repo-root-relative with a leading "/" (not absolute).
        assertEquals(
            setOf("/${projectRootDir.name}/file01", "/${projectRootDir.name}/file02", "/${projectRootDir.name}/file03", "/${projectRootDir.name}/file04"),
            gitUuidMap.values.map { it.filepath }.toSet(),
        )
    }

    @Test
    fun `Generate main files + for each 'git diff' that returns empty, copy file contents to a new file`() {
        val file01 = File(projectRootDir, "file01").also {
            it.createNewFile()
            it.writeText(someFileRelatedContent)
        }
        val file02 = File(projectRootDir, "file02").also {
            it.createNewFile()
            it.writeText(someFileRelatedContent)
        }
        val file03 = File(projectRootDir, "file03").also {
            it.createNewFile()
            it.writeText(someFileRelatedContent)
        }

        val paths = mutableListOf<String>()

        everyExecuteCommandAnswers(
            gitStatusPorcelainResponse = {
                """
                 M ${projectRootDir.name}/${file01.name}
                MM ${projectRootDir.name}/${file02.name}
                ?? ${projectRootDir.name}/${file03.name}
                """.trimIndent()
            },
            gitDiffResponse = { path: String ->
                paths.add(path)
                ""
            }
        )

        GitCollector().execute()

        // 'git diff' is only executed for MODIFIED files; untracked/new files get their contents copied
        assertEquals(2, paths.size)
        assertGitFilesAreCorrect()

        val gitUuidMap = Context.get().gitUuidMap
        assertEquals(3, gitUuidMap.entries.size)
        assertFilesFromUuidMapExist(gitUuidMap)
    }

    @Test
    fun `Generate main files + ignore files that don't exist`() {
        val file01 = File(projectRootDir, "file01").also { it.createNewFile() }

        val paths = mutableListOf<String>()

        everyExecuteCommandAnswers(
            gitStatusPorcelainResponse = {
                """
                 M ${projectRootDir.name}/${file01.name}
                MM ${projectRootDir.name}/aFileThatDoesNotExist
                ?? ${projectRootDir.name}/aFileThatDoesNotExist2
                """.trimIndent()
            },
            gitDiffResponse = { path: String ->
                paths.add(path)
                someFileRelatedContent
            }
        )

        GitCollector().execute()

        assertEquals(1, paths.size)
        assertGitFilesAreCorrect()

        val gitUuidMap = Context.get().gitUuidMap
        assertEquals(1, gitUuidMap.entries.size)
        assertFilesFromUuidMapExist(gitUuidMap)
    }

    @Test
    fun `Generate main files + repo root equals the project root`() {
        val file = File(projectRootDir, "file01").also { it.createNewFile() }

        val paths = mutableListOf<String>()

        everyExecuteCommandAnswers(
            gitStatusPorcelainResponse = { " M ${file.name}" },
            gitDiffResponse = { path: String ->
                paths.add(path)
                someFileRelatedContent
            },
            gitRepoRootResponse = { projectRootDir.absolutePath }
        )

        GitCollector().execute()

        assertEquals(1, paths.size)
        assertEquals(file.absolutePath, paths.first())
        assertGitFilesAreCorrect()

        val gitUuidMap = Context.get().gitUuidMap
        assertEquals(1, gitUuidMap.entries.size)
        assertFilesFromUuidMapExist(gitUuidMap)
        // Repo root == project root: the porcelain path is just the file name.
        assertEquals(setOf("/file01"), gitUuidMap.values.map { it.filepath }.toSet())
    }

    @Test
    fun `Generate main files + support file names with spaces`() {
        val file = File(projectRootDir, "file with space").also { it.createNewFile() }

        val paths = mutableListOf<String>()

        everyExecuteCommandAnswers(
            gitStatusPorcelainResponse = { " M ${projectRootDir.name}/${file.name}" },
            gitDiffResponse = { path: String ->
                paths.add(path)
                someFileRelatedContent
            }
        )

        GitCollector().execute()

        assertEquals(1, paths.size)
        assertGitFilesAreCorrect()

        val gitUuidMap = Context.get().gitUuidMap
        assertEquals(1, gitUuidMap.entries.size)
        assertFilesFromUuidMapExist(gitUuidMap)
    }

    private fun everyExecuteCommandAnswers(
        gitStatusPorcelainResponse: () -> String,
        gitDiffResponse: (String) -> String,
        gitRepoRootResponse: () -> String = { testDir.absolutePath }
    ) {
        val commandSlot = slot<String>()
        every { executeCommand(capture(commandSlot)) } answers {
            val command = commandSlot.captured
            println(command)
            when {
                (command == "git status --long --branch --untracked-files=normal") -> {
                    gitStatusResponse
                }
                (command == "git log --oneline") -> {
                    gitLogResponse
                }
                (command == "git rev-parse --show-toplevel") -> {
                    gitRepoRootResponse()
                }
                (command == "git status --porcelain --untracked-files=normal --no-renames") -> {
                    gitStatusPorcelainResponse()
                }
                command.startsWith("git --no-pager diff") -> {
                    val path = command.substringAfter("git --no-pager diff ").replace("\"", "")
                    gitDiffResponse(path)
                }
                else -> {
                    throw RuntimeException("Executed unexpected command -- ${command}")
                }
            }
        }
    }

    private fun assertGitFilesAreCorrect() {
        val gitStatusFile = File(Context.get().outputDir, "git-status.txt")
        assert(gitStatusFile.isFile)
        assertEquals(gitStatusResponse, gitStatusFile.readText())

        val gitLogFile = File(Context.get().outputDir, "git-log.txt")
        assert(gitLogFile.isFile)
        assertEquals(gitLogResponse, gitLogFile.readText())

        val errorFile = File(Context.get().outputDir, "error.txt")
        assert(!errorFile.exists())
    }

    private fun assertFilesFromUuidMapExist(uuidMap: Map<String, GitCollectedFile>) {
        uuidMap.keys.forEach { uuid ->
            assertTrue(File(Context.get().outputDir, uuid).isFile)
        }
    }
}
