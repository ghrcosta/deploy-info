package application

import java.time.Duration

/**
 * Schedules a task to run once after a delay. Used by the linking use case to re-run the matching
 * after a transient listing failure or before giving up and deleting an unlinked upload.
 */
interface RetryScheduler {

    fun schedule(delay: Duration, task: Runnable)
}
