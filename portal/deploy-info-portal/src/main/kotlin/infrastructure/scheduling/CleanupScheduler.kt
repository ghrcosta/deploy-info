package infrastructure.scheduling

import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * Runs the validity/cleanup sweep on a fixed interval. The first sweep runs after one interval (not
 * at startup) so the portal has time to settle, and a failed sweep is logged and simply waits for
 * the next tick. Uses a single-threaded daemon scheduled executor — same POC-grade approach as
 * [SimpleRetryScheduler], no external scheduling dependency.
 */
class CleanupScheduler(
    private val sweep: Runnable,
    private val interval: Duration,
) {

    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "cleanup-scheduler").apply { isDaemon = true }
    }

    fun start() {
        executor.scheduleWithFixedDelay(
            {
                try {
                    sweep.run()
                } catch (e: RuntimeException) {
                    logger.warning("Cleanup sweep failed: ${e.message}")
                }
            },
            interval.toMillis(),
            interval.toMillis(),
            TimeUnit.MILLISECONDS,
        )
    }

    companion object {
        private val logger = Logger.getLogger(CleanupScheduler::class.java.name)
    }
}