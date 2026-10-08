package util

import io.github.ghrcosta.CollectorTask
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ContextTests {
    val testDir: File = File("testDir/").absoluteFile
    val buildDir: File = File(testDir, "buildDirTest/").absoluteFile
    val projectRootDir: File = File(testDir, "projectRootDirTest/").absoluteFile

    lateinit var project: Project

    @BeforeTest
    fun setup() {
        testDir.mkdir()
        buildDir.mkdirs()
        projectRootDir.mkdirs()

        Logger.init(mockk(relaxed = true))

        project = mockk(relaxed = true)
        every { project.layout.buildDirectory.asFile.get() } returns buildDir
        every { project.rootDir } returns projectRootDir

        mockkStatic("io.github.ghrcosta.util.CommandUtilsKt")
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun cleanup() {
        testDir.toPath().deleteRecursively()
    }

    @Test
    fun `Context is initialized without writing a collector properties file`() {
        val userEmail = "user@email.com"
        val deployType = CollectorTask.DeployType.GAE
        every { executeCommandOrThrowException("gcloud config get-value account", any()) } returns
                CommandResult(stdout = userEmail, stderr = "")

        Context.init(project, deployType)

        assertNotNull(Context.get())
        assertEquals(userEmail, Context.get().userEmail)
        assert(!File(Context.get().outputDir, "collector.properties").exists())
    }
}