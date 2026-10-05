package application.cleanup

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.logging.Logger

/**
 * The on-request cleanup trigger: runs the validity/cleanup sweep from a tree request, but only
 * when enough time has passed since the last completed sweep.
 *
 * This replaces a fixed-interval scheduler because the portal runs on App Engine Standard, where
 * idle instances are terminated after ~15 minutes: a scheduled job either never fires (long
 * interval, dead instance) or keeps an instance alive forever (short interval). Tree requests are
 * the app's natural heartbeat, so the sweep piggybacks on them.
 *
 * The timing logic lives here; the sweep itself is an injected [Runnable]. Rules:
 * - When disabled (the configured switch is off), this is a no-op — no state is read or written.
 * - When the last completed sweep is younger than `interval`, nothing happens: normal tree requests
 *   pay no sweep cost.
 * - Otherwise the sweep runs; a sweep that throws is logged and **not** marked completed, so the
 *   next tree request retries.
 *
 * Concurrency: two backend instances may run the sweep at the same time. This is accepted as merely
 * redundant — every delete is idempotent (delete-only-on-proof, the Datastore entry first), so the
 * worst case is duplicated listing API calls and duplicated work, both bounded by the number of
 * instances and the once-per-interval trigger. No claim/lock is used to prevent it.
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
            logger.warning("Cleanup sweep failed: ${e.message}")
            return
        }
        cleanupStateRepository.markCompleted(now)
    }

    companion object {
        private val logger = Logger.getLogger(RunCleanupIfDueUseCase::class.java.name)
    }
}
