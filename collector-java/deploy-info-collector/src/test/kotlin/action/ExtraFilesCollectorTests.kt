package action

import io.github.ghrcosta.CollectorTask
import io.github.ghrcosta.action.ExtraFilesCollector
import io.github.ghrcosta.util.CommandResult
import io.github.ghrcosta.util.Context
import io.github.ghrcosta.util.Logger
import io.github.ghrcosta.util.executeCommandOrThrowException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.gradle.api.Project
import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.*

class ExtraFilesCollectorTests {

    val testDir: File = File("testDir/").absoluteFile
    val buildDir: File = File(testDir, "buildDirTest/").absoluteFile
    val projectRootDir: File = File(testDir, "projectRootDirTest/").absoluteFile

    lateinit var project: Project

    @BeforeTest
    fun setup() {
        testDir.mkdir()
        buildDir.mkdirs()
        projectRootDir.mkdirs()

        project = mockk(relaxed = true)
        every { project.layout.buildDirectory.asFile.get() } returns buildDir
        every { project.rootDir } returns projectRootDir

        Logger.init(project)
        Context.init(project, CollectorTask.DeployType.GAE)

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
    fun `Copy files`() {
        val file01 = File(projectRootDir, "file01").also { it.createNewFile() }
        val file02 = File(projectRootDir, "file02").also { it.createNewFile() }
        val file03 = File(projectRootDir, "file03").also { it.createNewFile() }

        val extraFiles = listOf(file01.name, file02.name, file03.name)

        ExtraFilesCollector(extraFiles).execute()

        val extraUuidMap = Context.get().extraUuidMap
        assertEquals(3, extraUuidMap.entries.size)
        assertFilesFromUuidMapExist(extraUuidMap)
        assert(extraUuidMap.values.all { it.startsWith("/") })
    }

    @Test
    fun `Copy files but ignore duplicate entries`() {
        val file = File(projectRootDir, "file01").also { it.createNewFile() }

        val extraFiles = listOf(file.name, file.name)

        ExtraFilesCollector(extraFiles).execute()

        val extraUuidMap = Context.get().extraUuidMap
        assertEquals(1, extraUuidMap.entries.size)
    }

    @Test
    fun `Copy files but ignore binary files`() {
        val binaryFile = File(projectRootDir, "binaryFile.bin").also {
            it.createNewFile()
            it.writeBytes("binary file".toByteArray())
        }
        val textFile = File(projectRootDir, "textFile.txt").also {
            it.createNewFile()
            it.writeText("text file")
        }

        val extraFiles = listOf(binaryFile.name, textFile.name)

        ExtraFilesCollector(extraFiles).execute()

        val extraUuidMap = Context.get().extraUuidMap
        assertEquals(1, extraUuidMap.entries.size)
        extraUuidMap.values.first().endsWith(textFile.name)
    }

    @Test
    fun `Copy files but ignore files that don't exist`() {
        val file = File(projectRootDir, "file01").also { it.createNewFile() }

        val extraFiles = listOf(file.name, "aFileThatDoesNotExist")

        ExtraFilesCollector(extraFiles).execute()

        val extraUuidMap = Context.get().extraUuidMap
        assertEquals(1, extraUuidMap.entries.size)
        extraUuidMap.values.first().endsWith(file.name)
    }

    private fun assertFilesFromUuidMapExist(uuidMap: Map<String, String>) {
        uuidMap.keys.forEach { uuid ->
            assertTrue(File(Context.get().outputDir, uuid).isFile)
        }
    }
}