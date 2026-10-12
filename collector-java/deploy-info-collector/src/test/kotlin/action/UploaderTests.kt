package action

import io.github.ghrcosta.CollectorTask
import io.github.ghrcosta.action.Uploader
import io.github.ghrcosta.util.CommandResult
import io.github.ghrcosta.util.Context
import io.github.ghrcosta.util.GitCollectedFile
import io.github.ghrcosta.util.GitFileStatus
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
    fun `Save git UUID map to CSV and extra UUID map to properties file`() {
        val gitUuid1 = UUID.randomUUID().toString()
        val gitUuid2 = UUID.randomUUID().toString()
        val gitUuid3 = UUID.randomUUID().toString()
        val extraUuid1 = UUID.randomUUID().toString()
        Context.get().gitUuidMap.put(gitUuid1, GitCollectedFile("/path/to/file01", status = GitFileStatus.MODIFIED))
        Context.get().gitUuidMap.put(gitUuid2, GitCollectedFile("/path/to/file02", status = GitFileStatus.NEW))
        Context.get().gitUuidMap.put(gitUuid3, GitCollectedFile("/path/to/file04", status = GitFileStatus.UNTRACKED))
        Context.get().extraUuidMap.put(extraUuid1, "/path/to/file03")

        Uploader("fakeBucketName").execute()

        val uuidGitCsvFile = File(Context.get().outputDir, "uuid-git.csv")
        assert(uuidGitCsvFile.isFile)
        assertEquals(
            listOf(
                "uuid,filepath,status",
                "$gitUuid1,/path/to/file01,modified",
                "$gitUuid2,/path/to/file02,new",
                "$gitUuid3,/path/to/file04,untracked",
            ),
            uuidGitCsvFile.readLines(),
        )

        val uuidExtraPropertiesFile = File(Context.get().outputDir, "uuid-extra.properties")
        assert(uuidExtraPropertiesFile.isFile)
        val uuidExtraProperties = Properties().also { properties ->
            uuidExtraPropertiesFile.reader().use { properties.load(it) }
        }
        assertEquals(1, uuidExtraProperties.entries.size)
        assert(uuidExtraProperties.keys.containsAll(listOf(extraUuid1)))
    }

    @Test
    fun `Git UUID CSV quotes filepaths containing commas or quotes`() {
        val gitUuid1 = UUID.randomUUID().toString()
        val gitUuid2 = UUID.randomUUID().toString()
        Context.get().gitUuidMap.put(gitUuid1, GitCollectedFile("/path/with,comma", status = GitFileStatus.MODIFIED))
        Context.get().gitUuidMap.put(gitUuid2, GitCollectedFile("/path/with\"quote", status = GitFileStatus.NEW))

        Uploader("fakeBucketName").execute()

        val csvLines = File(Context.get().outputDir, "uuid-git.csv").readLines()
        assertEquals(
            listOf(
                "uuid,filepath,status",
                "$gitUuid1,\"/path/with,comma\",modified",
                "$gitUuid2,\"/path/with\"\"quote\",new",
            ),
            csvLines,
        )
    }

    @Test
    fun `Git UUID CSV file is not saved if map is empty`() {
        Context.get().extraUuidMap.put(UUID.randomUUID().toString(), "/path/to/file01")

        Uploader("fakeBucketName").execute()

        val uuidGitCsvFile = File(Context.get().outputDir, "uuid-git.csv")
        assert(!uuidGitCsvFile.exists())

        val uuidExtraPropertiesFile = File(Context.get().outputDir, "uuid-extra.properties")
        assert(uuidExtraPropertiesFile.isFile)
    }

    @Test
    fun `Extra UUID properties file is not saved if map is empty`() {
        Context.get().gitUuidMap.put(UUID.randomUUID().toString(), GitCollectedFile("/path/to/file01", status = GitFileStatus.MODIFIED))

        Uploader("fakeBucketName").execute()

        val uuidGitCsvFile = File(Context.get().outputDir, "uuid-git.csv")
        assert(uuidGitCsvFile.isFile)

        val uuidExtraPropertiesFile = File(Context.get().outputDir, "uuid-extra.properties")
        assert(!uuidExtraPropertiesFile.exists())
    }
}
