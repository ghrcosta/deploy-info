package io.github.ghrcosta.probe

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Test-only task used by the Gradle TestKit compatibility tests.
 *
 * IMPORTANT: it intentionally mirrors the Gradle API surface of `io.github.ghrcosta.CollectorTask`
 * (abstract `Property`/`ListProperty` fields, `@Input`, `@Optional`, `@Option` annotations, enum
 * conversion) so the compatibility tests exercise exactly the same APIs the production task uses.
 * Keep this in sync with CollectorTask whenever its Gradle API surface changes.
 *
 * Unlike the production task, it performs no external commands, no network requests and no writes
 * outside the consumer project's own build directory, so it is safe to run in a TestKit build.
 *
 * This class lives in the functionalTest source set and is therefore NOT part of the published plugin.
 */
@DisableCachingByDefault(because = "Test-only probe task; not cacheable")
abstract class ProbeTask : DefaultTask() {

    init {
        group = "deployInfo"
        description = "Test-only task used by the Gradle TestKit compatibility tests. Not part of the published plugin."
    }

    companion object {
        const val TASK_NAME = "deployInfoProbe"
    }

    @get:Input
    @get:Option(
        description = "Maximum size of files that can be included. Default is 50KB."
    )
    @get:Optional
    abstract val maxFileSize: Property<Int>

    @get:Input
    @get:Option(
        description = "If git-related data must be collected. Default is 'true'."
    )
    @get:Optional
    abstract val collectGitStatus: Property<Boolean>

    @get:Input
    @get:Option(
        description = "Extra files to include in the collection."
    )
    @get:Optional
    abstract val extraFilesToCollect: ListProperty<String>

    @get:Input
    @get:Option(
        description = "Type of deploy. Supported values are 'GAE' and 'RUN'."
    )
    abstract val deployType: Property<DeployType>

    @get:Input
    @get:Option(
        description = "URL of the portal backend."
    )
    abstract val portalUrl: Property<String>

    @get:Input
    @get:Option(
        description = "List of GCP projects where the code may have been deployed."
    )
    abstract val projects: ListProperty<String>

    @Suppress("unused") enum class DeployType { GAE, RUN }

    @TaskAction
    fun run() {
        validate()

        // Only reads the configured properties and logs. No external commands, no network, no file writes.
        logger.lifecycle(
            "deployInfoProbe OK: maxFileSize=${maxFileSize.orNull}, collectGitStatus=${collectGitStatus.getOrElse(true)}, " +
                    "extraFilesToCollect=${extraFilesToCollect.orNull}, " +
                    "deployType=${deployType.get()}, portalUrl=${portalUrl.orNull}, projects=${projects.get()}"
        )
    }

    private fun validate() {
        if (portalUrl.get().isBlank())
            throw GradleException("'portalUrl' must not be empty.")
        if (projects.get().isEmpty())
            throw GradleException("'projects' must not be empty: list at least one GCP project where the code may be deployed.")
        projects.get().find { it.isBlank() }?.let {
            throw GradleException("'projects' must not contain empty values.")
        }
    }
}
