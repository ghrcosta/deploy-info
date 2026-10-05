package domain

import java.time.Instant

/**
 * The persistence-backed state of the validity/cleanup sweep, used by the on-request trigger to
 * decide whether a sweep is due.
 */
data class CleanupState(
    /** When the last sweep that ran to completion finished; null when none has run yet. */
    val lastCleanupCompleted: Instant?,
)
