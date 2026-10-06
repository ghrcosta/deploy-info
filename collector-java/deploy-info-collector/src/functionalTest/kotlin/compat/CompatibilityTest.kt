package compat

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Gradle TestKit compatibility matrix tests.
 *
 * Each test builds a real consumer project in a temp directory, applies the plugin through the
 * plugin-under-test classpath injected by `withPluginClasspath()` (i.e. no repository resolution),
 * and runs the test-only ProbeTask, which exercises the plugin's Gradle API surface without
 * external commands or network access.
 *
 * Covered combinations (the two corners of the compatibility matrix):
 *  - minimum supported:  Gradle 7.6 running on JDK 17 (catches too-new bytecode and APIs missing in 7.6)
 *  - maximum supported:  Gradle 9.8.0 running on JDK 21 (catches APIs removed in new Gradle and JDK-21 issues)
 */
class CompatibilityTest {

    companion object {
        /** Minimum Gradle version the plugin supports. */
        private const val MINIMUM_SUPPORTED_GRADLE = "7.6"

        /** Latest stable Gradle the plugin is verified against. Update when upgrading the plugin build. */
        private const val LATEST_SUPPORTED_GRADLE = "9.8.0"

        private const val PROBE_TASK = "deployInfoProbe"
        private const val PROBE_SUCCESS_MARKER = "deployInfoProbe OK:"

        private const val CONSUMER_SETTINGS = "rootProject.name = 'compat-probe-project'"

        // NOTE: the consumer build is a Groovy build script on purpose. Gradle 7.6's embedded Kotlin
        // compiler cannot read Kotlin 2.x metadata of the plugin classes, so a Kotlin DSL consumer
        // script that references the plugin's classes fails there with
        // "Protocol message contained an invalid tag (zero)" — a Gradle/Kotlin limitation unrelated to
        // the plugin itself. Groovy scripts resolve plugin classes dynamically and are the right
        // vehicle for the compatibility matrix.
        private val consumerBuild = """
            import io.github.ghrcosta.probe.ProbeTask

            plugins {
                id "io.github.ghrcosta.deploy-info-collector"
                id "io.github.ghrcosta.deploy-info-probe"
            }

            tasks.deployInfoProbe {
                storageBucket = 'test-bucket'
                deployType = ProbeTask.DeployType.GAE
                portalUrl = 'https://deploy-info-portal.example.com'
                projects = ['proj-a', 'proj-b']
            }
        """.trimIndent()
    }

    @TempDir
    lateinit var projectDir: File

    @Test
    @DisplayName("runs on minimum supported combination Gradle 7.6 with Java 17")
    fun runsOnMinimumSupportedCombinationGradle76WithJava17() {
        runProbeTask(gradleVersion = MINIMUM_SUPPORTED_GRADLE, javaHome = requireJavaHome("compat.java17Home"))
    }

    @Test
    @DisplayName("runs on latest supported combination Gradle 9.8.0 with Java 21")
    fun runsOnLatestSupportedCombinationGradle98WithJava21() {
        runProbeTask(gradleVersion = LATEST_SUPPORTED_GRADLE, javaHome = requireJavaHome("compat.java21Home"))
    }

    private fun runProbeTask(gradleVersion: String, javaHome: String) {
        writeConsumerProjectFiles(javaHome)

        val result = GradleRunner.create()
            .withPluginClasspath()
            .withGradleVersion(gradleVersion)
            .withProjectDir(projectDir)
            .withArguments("--console=plain", "--stacktrace", PROBE_TASK)
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":$PROBE_TASK")?.outcome)
        assertTrue(result.output.contains(PROBE_SUCCESS_MARKER), "Probe task did not report success:\n${result.output}")
    }

    private fun writeConsumerProjectFiles(javaHome: String) {
        // The consumer build must run on the specific JDK of the compatibility combination.
        File(projectDir, "gradle.properties").writeText("org.gradle.java.home=$javaHome\n")
        File(projectDir, "settings.gradle").writeText(CONSUMER_SETTINGS)
        File(projectDir, "build.gradle").writeText(consumerBuild)
    }

    private fun requireJavaHome(systemPropertyName: String): String =
        System.getProperty(systemPropertyName)
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "System property '$systemPropertyName' is not set. " +
                    "Run the tests through the 'functionalTest' Gradle task, which resolves the toolchains."
            )
}
