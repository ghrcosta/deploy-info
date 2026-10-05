package application.cleanup

import domain.CleanupState
import java.time.Instant

/** Persists the [CleanupState] singleton the on-request cleanup trigger relies on. */
interface CleanupStateRepository {

    /** The current state, or null when no sweep has ever been completed. */
    fun get(): CleanupState?

    /** Records that a sweep ran to completion. */
    fun markCompleted(now: Instant)
}
