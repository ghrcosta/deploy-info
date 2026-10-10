package infrastructure.scheduling

import application.RetryScheduler
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Simple single-threaded scheduled-executor implementation of [RetryScheduler] (POC-grade; no queue
 * or messaging needed yet). Tasks that throw are logged and dropped — a failed retry simply means
 * the upload stays in Cloud Storage untouched.
 */
class SimpleRetryScheduler : RetryScheduler {

    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "retry-scheduler").apply { isDaemon = true }
    }

    override fun schedule(delay: Duration, task: Runnable) {
        executor.schedule(
            {
                try {
                    task.run()
                } catch (e: RuntimeException) {
                    logger.warn("Scheduled retry task failed: ${e.message}")
                }
            },
            delay.toMillis(),
            TimeUnit.MILLISECONDS,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SimpleRetryScheduler::class.java)
    }
}
