package io.github.ghrcosta

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CollectorTaskTests {
    val testDir: File = File("testDir/collectorTaskTests/").absoluteFile

    @BeforeTest
    fun setup() {
        testDir.mkdirs()
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun cleanup() {
        testDir.toPath().deleteRecursively()
    }

    private fun createTask(): CollectorTask =
        ProjectBuilder.builder().withProjectDir(testDir).build()
            .tasks.create(CollectorTask.TASK_NAME, CollectorTask::class.java)

    @Test
    fun `String inputs are stripped of the surrounding blank space`() {
        val task = createTask()
        task.portalUrl.set("  https://deploy-info-portal.example.com  ")
        task.projects.set(listOf("  proj-prod  ", "\tproj-dev\n"))
        task.extraFilesToCollect.set(listOf("  file1.txt  ", "file2.txt"))

        val (portalUrl, projects, extraFiles) = task.trimmedInputs()

        assertEquals("https://deploy-info-portal.example.com", portalUrl)
        assertEquals(listOf("proj-prod", "proj-dev"), projects)
        assertEquals(listOf("file1.txt", "file2.txt"), extraFiles)
    }

    @Test
    fun `Whitespace-only portalUrl is rejected as empty after stripping`() {
        val task = createTask()
        task.portalUrl.set("   ")
        task.projects.set(listOf("proj-prod"))

        val (portalUrl, projects, _) = task.trimmedInputs()

        val exception = assertFailsWith<GradleException> { task.validateParameters(portalUrl, projects) }
        assertEquals("'portalUrl' must not be empty.", exception.message)
    }

    @Test
    fun `Whitespace-only project is rejected as an empty value after stripping`() {
        val task = createTask()
        task.portalUrl.set("https://deploy-info-portal.example.com")
        task.projects.set(listOf("   ", "proj-prod"))

        val (portalUrl, projects, _) = task.trimmedInputs()

        val exception = assertFailsWith<GradleException> { task.validateParameters(portalUrl, projects) }
        assertEquals("'projects' must not contain empty values.", exception.message)
    }
}
