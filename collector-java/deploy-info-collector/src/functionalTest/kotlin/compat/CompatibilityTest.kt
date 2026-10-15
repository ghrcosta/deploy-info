package compat

import org.gradle.testkit.runner.BuildResult
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
 * Covered combinations:
 *  - minimum supported Java: Gradle 7.6 running on JDK 11
 *  - minimum supported:      Gradle 7.6 running on JDK 17
 *  - maximum supported:      Gradle 9.8.1 running on JDK 21
 *
 * It also covers the two Gradle features the plugin declares compatibility with (see the `compatibility`
 * block in `build.gradle.kts`):
 *  - the configuration cache, on both corners of the matrix, by running the consumer build twice and
 *    asserting the cache is stored on the first run and reused on the second;
 *  - isolated projects, on the latest combination only (the feature does not exist in Gradle 7.6),
 *    by enabling it explicitly and asserting the build still succeeds.
 */
class CompatibilityTest {

    companion object {
        /** Minimum Gradle version the plugin supports. */
        private const val MINIMUM_SUPPORTED_GRADLE = "7.6"

        /** Latest stable Gradle the plugin is verified against. Update when upgrading the plugin build. */
        private const val LATEST_SUPPORTED_GRADLE = "9.8.1"

        private const val PROBE_TASK = "deployInfoProbe"
        private const val PROBE_SUCCESS_MARKER = "deployInfoProbe OK:"

        private const val CONFIGURATION_CACHE_FLAG = "--configuration-cache"
        private const val CONFIGURATION_CACHE_STORED_MARKER = "Configuration cache entry stored."
        // Gradle 7.6 prints "Reusing configuration cache entry." while Gradle 9 prints
        // "Configuration cache entry reused.", so the check accepts both wordings.
        private val CONFIGURATION_CACHE_REUSED_PATTERN =
            Regex("(?i)configuration cache entry reused|reusing configuration cache entry")

        /** Enables Gradle's incubating isolated projects feature (present since Gradle 8, not in 7.6). */
        private const val ISOLATED_PROJECTS_FLAG = "-Dorg.gradle.unsafe.isolated-projects=true"
        private const val ISOLATED_PROJECTS_MARKER = "Isolated Projects is an incubating feature."

        /** Name of the generated plugin descriptor, placed at the classpath root by the pluginDescriptors task. */
        private const val PLUGIN_DESCRIPTOR_RESOURCE = "io.github.ghrcosta.deploy-info-collector.properties"
        private const val FEATURE_COMPATIBILITY_SUPPORTED_VALUE = "DECLARED_SUPPORTED"

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
                deployType = ProbeTask.DeployType.GAE
                portalUrl = 'https://deploy-info-portal.example.com'
                projects = ['proj-a', 'proj-b']
            }
        """.trimIndent()
    }

    @TempDir
    lateinit var projectDir: File

    @Test
    @DisplayName("runs on minimum supported Java combination Gradle 7.6 with Java 11")
    fun runsOnMinimumSupportedJavaCombinationGradle76WithJava11() {
        runProbeTask(gradleVersion = MINIMUM_SUPPORTED_GRADLE, javaHome = requireJavaHome("compat.java11Home"))
    }

    @Test
    @DisplayName("runs on minimum supported combination Gradle 7.6 with Java 17")
    fun runsOnMinimumSupportedCombinationGradle76WithJava17() {
        runProbeTask(gradleVersion = MINIMUM_SUPPORTED_GRADLE, javaHome = requireJavaHome("compat.java17Home"))
    }

    @Test
    @DisplayName("runs on latest supported combination Gradle 9.8.1 with Java 21")
    fun runsOnLatestSupportedCombinationGradle981WithJava21() {
        runProbeTask(gradleVersion = LATEST_SUPPORTED_GRADLE, javaHome = requireJavaHome("compat.java21Home"))
    }

    @Test
    @DisplayName("is configuration-cache compatible on minimum supported combination Gradle 7.6 with Java 17")
    fun isConfigurationCacheCompatibleOnMinimumSupportedCombinationGradle76WithJava17() {
        verifyConfigurationCacheStoredAndReused(
            gradleVersion = MINIMUM_SUPPORTED_GRADLE,
            javaHome = requireJavaHome("compat.java17Home"),
        )
    }

    @Test
    @DisplayName("is configuration-cache compatible on latest supported combination Gradle 9.8.1 with Java 21")
    fun isConfigurationCacheCompatibleOnLatestSupportedCombinationGradle981WithJava21() {
        verifyConfigurationCacheStoredAndReused(
            gradleVersion = LATEST_SUPPORTED_GRADLE,
            javaHome = requireJavaHome("compat.java21Home"),
        )
    }

    @Test
    @DisplayName("is isolated-projects compatible on latest supported combination Gradle 9.8.1 with Java 21")
    fun isIsolatedProjectsCompatibleOnLatestSupportedCombinationGradle981WithJava21() {
        val result = runProbeTask(
            gradleVersion = LATEST_SUPPORTED_GRADLE,
            javaHome = requireJavaHome("compat.java21Home"),
            extraArguments = listOf(ISOLATED_PROJECTS_FLAG, CONFIGURATION_CACHE_FLAG),
        )

        assertTrue(
            result.output.contains(ISOLATED_PROJECTS_MARKER),
            "Isolated Projects was not enabled in the consumer build:\n${result.output}",
        )
    }

    @Test
    @DisplayName("publishes the feature compatibility flags of both supported features")
    fun publishesFeatureCompatibilityFlagsOfBothSupportedFeatures() {
        val descriptor = javaClass.classLoader.getResourceAsStream(PLUGIN_DESCRIPTOR_RESOURCE)
            ?.use { it.readBytes().decodeToString() }
            ?: throw IllegalStateException(
                "Plugin descriptor '$PLUGIN_DESCRIPTOR_RESOURCE' is not on the test classpath. " +
                    "Run the tests through the 'functionalTest' Gradle task, which wires it in."
            )

        assertTrue(
            descriptor.contains("compatibility.feature.configuration-cache=$FEATURE_COMPATIBILITY_SUPPORTED_VALUE"),
            "The published plugin descriptor does not declare configuration-cache support:\n$descriptor",
        )
        assertTrue(
            descriptor.contains("compatibility.feature.isolated-projects=$FEATURE_COMPATIBILITY_SUPPORTED_VALUE"),
            "The published plugin descriptor does not declare isolated-projects support:\n$descriptor",
        )
    }

    private fun verifyConfigurationCacheStoredAndReused(gradleVersion: String, javaHome: String) {
        val firstRun = runProbeTask(
            gradleVersion = gradleVersion,
            javaHome = javaHome,
            extraArguments = listOf(CONFIGURATION_CACHE_FLAG),
        )
        assertTrue(
            firstRun.output.contains(CONFIGURATION_CACHE_STORED_MARKER),
            "The first run did not store a configuration cache entry:\n${firstRun.output}",
        )

        val secondRun = runProbeTask(
            gradleVersion = gradleVersion,
            javaHome = javaHome,
            extraArguments = listOf(CONFIGURATION_CACHE_FLAG),
        )
        assertTrue(
            CONFIGURATION_CACHE_REUSED_PATTERN.containsMatchIn(secondRun.output),
            "The second run did not reuse the configuration cache entry:\n${secondRun.output}",
        )
    }

    private fun runProbeTask(
        gradleVersion: String,
        javaHome: String,
        extraArguments: List<String> = emptyList(),
    ): BuildResult {
        writeConsumerProjectFiles(javaHome)

        val result = GradleRunner.create()
            .withPluginClasspath()
            .withGradleVersion(gradleVersion)
            .withProjectDir(projectDir)
            .withArguments(listOf("--console=plain", "--stacktrace") + extraArguments + PROBE_TASK)
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":$PROBE_TASK")?.outcome)
        assertTrue(result.output.contains(PROBE_SUCCESS_MARKER), "Probe task did not report success:\n${result.output}")
        return result
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
