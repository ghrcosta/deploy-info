package io.github.ghrcosta

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
abstract class CollectorPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register(CollectorTask.TASK_NAME, CollectorTask::class.java)
    }
}