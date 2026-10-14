package util

import io.github.ghrcosta.CollectorTask
import io.github.ghrcosta.util.CommandResult
import io.github.ghrcosta.util.Context
import io.github.ghrcosta.util.Logger
import io.github.ghrcosta.util.executeCommandOrThrowException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.gradle.api.logging.Logger as GradleLogger
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

    @BeforeTest
    fun setup() {
        testDir.mkdir()
        buildDir.mkdirs()
        projectRootDir.mkdirs()

        Logger.init(mockk<GradleLogger>(relaxed = true))

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

        Context.init(projectRootDir, buildDir, deployType)

        assertNotNull(Context.get())
        assertEquals(userEmail, Context.get().userEmail)
        assert(!File(Context.get().outputDir, "collector.properties").exists())
    }

    @Test
    fun `Context converts the configured max file size from KB to bytes`() {
        every { executeCommandOrThrowException("gcloud config get-value account", any()) } returns
                CommandResult(stdout = "user@email.com", stderr = "")

        Context.init(projectRootDir, buildDir, CollectorTask.DeployType.GAE, maxFileSizeInKb = 1)

        assertEquals(1024, Context.get().maxFileSize)
    }

    @Test
    fun `Context keeps the configured max git log lines`() {
        every { executeCommandOrThrowException("gcloud config get-value account", any()) } returns
                CommandResult(stdout = "user@email.com", stderr = "")

        Context.init(projectRootDir, buildDir, CollectorTask.DeployType.GAE, maxGitLogLines = 7)

        assertEquals(7, Context.get().maxGitLogLines)
    }

    @Test
    fun `Context defaults the max git log lines to 30`() {
        every { executeCommandOrThrowException("gcloud config get-value account", any()) } returns
                CommandResult(stdout = "user@email.com", stderr = "")

        Context.init(projectRootDir, buildDir, CollectorTask.DeployType.GAE)

        assertEquals(30, Context.get().maxGitLogLines)
    }

    @Test
    fun `Context defaults the max file size to 50KB`() {
        every { executeCommandOrThrowException("gcloud config get-value account", any()) } returns
                CommandResult(stdout = "user@email.com", stderr = "")

        Context.init(projectRootDir, buildDir, CollectorTask.DeployType.GAE)

        assertEquals(50 * 1024, Context.get().maxFileSize)
    }
}
