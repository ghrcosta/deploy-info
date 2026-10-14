package application.cleanup

import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The on-request cleanup trigger: runs the injected sweep [Runnable] from a tree request, but only
 * when enough time has passed since the last completed sweep — disabled means a no-op; a sweep that
 * throws is logged and not marked completed, so the next tree request retries.
 *
 * Why the sweep piggybacks on tree requests instead of a fixed scheduler, and why concurrent sweeps
 * are accepted as redundant: documentation/backend/deploy-cleanup.md.
 */
class RunCleanupIfDueUseCase(
    private val enabled: Boolean,
    private val sweep: Runnable,
    private val cleanupStateRepository: CleanupStateRepository,
    private val clock: Clock,
    private val interval: Duration,
) {

    fun executeIfDue() {
        if (!enabled) return
        val now = clock.instant()
        val lastCompleted = cleanupStateRepository.get()?.lastCleanupCompleted ?: return runAndMark(now)
        if (lastCompleted.isAfter(now.minus(interval))) return
        runAndMark(now)
    }

    private fun runAndMark(now: Instant) {
        try {
            sweep.run()
        } catch (e: RuntimeException) {
            logger.warn("Cleanup sweep failed: ${e.message}")
            return
        }
        cleanupStateRepository.markCompleted(now)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RunCleanupIfDueUseCase::class.java)
    }
}
