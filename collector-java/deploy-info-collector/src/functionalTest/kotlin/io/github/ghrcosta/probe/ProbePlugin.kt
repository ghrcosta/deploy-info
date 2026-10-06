package io.github.ghrcosta.probe

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Test-only plugin that registers [ProbeTask]. It is resolved by the TestKit consumer build through
 * the handwritten descriptor at `src/functionalTest/resources/META-INF/gradle-plugins/`.
 * Lives in the functionalTest source set and is therefore NOT part of the published plugin.
 */
@Suppress("unused")
class ProbePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register(ProbeTask.TASK_NAME, ProbeTask::class.java)
    }
}
