package io.github.ghrcosta.util

import org.gradle.api.Project

class Logger(private val project: Project) {
    companion object {
        private lateinit var instance: Logger

        fun init(project: Project) {
            instance = Logger(project)
        }

        fun i(message: String) = instance.project.logger.lifecycle(message)
        fun e(message: String) = instance.project.logger.error(message)
    }
}