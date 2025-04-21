package action

import io.github.ghrcosta.CollectorTask
import io.github.ghrcosta.action.Uploader
import io.github.ghrcosta.util.CommandResult
import io.github.ghrcosta.util.Context
import io.github.ghrcosta.util.Logger
import io.github.ghrcosta.util.executeCommandOrThrowException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.gradle.api.Project
import java.io.File
import java.util.Properties
import java.util.UUID
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UploaderTests {

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
        every { executeCommandOrThrowException(any()) } returns CommandResult(stdout = "", stderr = "")
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun cleanup() {
        testDir.toPath().deleteRecursively()
    }

    @Test
    fun `Save UUID maps to properties files`() {
        val gitUuid1 = UUID.randomUUID().toString()
        val gitUuid2 = UUID.randomUUID().toString()
        val extraUuid1 = UUID.randomUUID().toString()
        Context.get().gitUuidMap.put(gitUuid1, "/path/to/file01")
        Context.get().gitUuidMap.put(gitUuid2, "/path/to/file02")
        Context.get().extraUuidMap.put(extraUuid1, "/path/to/file03")

        Uploader("fakeBucketName").execute()

        val uuidGitPropertiesFile = File(Context.get().outputDir, "uuid-git.properties")
        assert(uuidGitPropertiesFile.isFile)
        val uuidGitProperties = Properties().also { properties ->
            uuidGitPropertiesFile.reader().use { properties.load(it) }
        }
        assertEquals(2, uuidGitProperties.entries.size)
        assert(uuidGitProperties.keys.containsAll(listOf(gitUuid1, gitUuid2)))

        val uuidExtraPropertiesFile = File(Context.get().outputDir, "uuid-extra.properties")
        assert(uuidExtraPropertiesFile.isFile)
        val uuidExtraProperties = Properties().also { properties ->
            uuidExtraPropertiesFile.reader().use { properties.load(it) }
        }
        assertEquals(1, uuidExtraProperties.entries.size)
        assert(uuidExtraProperties.keys.containsAll(listOf(extraUuid1)))
    }

    @Test
    fun `Git UUID properties file is not saved if map is empty`() {
        Context.get().extraUuidMap.put(UUID.randomUUID().toString(), "/path/to/file01")

        Uploader("fakeBucketName").execute()

        val uuidGitPropertiesFile = File(Context.get().outputDir, "uuid-git.properties")
        assert(!uuidGitPropertiesFile.exists())

        val uuidExtraPropertiesFile = File(Context.get().outputDir, "uuid-extra.properties")
        assert(uuidExtraPropertiesFile.isFile)
    }

    @Test
    fun `Extra UUID properties file is not saved if map is empty`() {
        Context.get().gitUuidMap.put(UUID.randomUUID().toString(), "/path/to/file01")

        Uploader("fakeBucketName").execute()

        val uuidGitPropertiesFile = File(Context.get().outputDir, "uuid-git.properties")
        assert(uuidGitPropertiesFile.isFile)

        val uuidExtraPropertiesFile = File(Context.get().outputDir, "uuid-extra.properties")
        assert(!uuidExtraPropertiesFile.exists())
    }
}