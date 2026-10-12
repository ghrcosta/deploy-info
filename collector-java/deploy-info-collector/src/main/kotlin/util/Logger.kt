package io.github.ghrcosta.util

import org.gradle.api.logging.Logger as GradleLogger

class Logger(private val logger: GradleLogger) {
    companion object {
        private lateinit var instance: Logger

        fun init(logger: GradleLogger) {
            instance = Logger(logger)
        }

        fun d(message: String) = instance.logger.debug(message)
        fun i(message: String) = instance.logger.lifecycle(message)
        fun e(message: String) = instance.logger.error(message)
    }
}
