package tests.fakes

import application.RetryScheduler
import java.time.Duration

/**
 * Controllable in-memory [RetryScheduler]: scheduled tasks are stored with their delay and only run
 * when the test calls [runScheduledTasks] or [advanceBy] (to simulate elapsed time), so the delayed
 * linking retry and the final deletion can be exercised deterministically.
 */
class FakeRetryScheduler : RetryScheduler {

    data class ScheduledTask(val delay: Duration, val task: Runnable)

    private val tasks = mutableListOf<ScheduledTask>()

    fun scheduledTasks(): List<ScheduledTask> = tasks.toList()

    override fun schedule(delay: Duration, task: Runnable) {
        tasks.add(ScheduledTask(delay, task))
    }

    /** Runs every scheduled task, regardless of its delay. */
    fun runScheduledTasks() = advanceBy(Duration.ofSeconds(Long.MAX_VALUE))

    /** Runs every scheduled task whose delay is at most [elapsed]. */
    fun advanceBy(elapsed: Duration) {
        tasks
            .filter { it.delay <= elapsed }
            .also { due -> tasks.removeAll(due.toSet()) }
            .forEach { it.task.run() }
    }
}
