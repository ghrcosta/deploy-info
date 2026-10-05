package tests.fakes

import application.cleanup.CleanupStateRepository
import domain.CleanupState
import java.time.Instant

/** In-memory [CleanupStateRepository] with a completion counter for the trigger tests. */
class FakeCleanupStateRepository : CleanupStateRepository {

    var state: CleanupState? = null
    var completedCount = 0
        private set

    override fun get(): CleanupState? = state?.copy()

    override fun markCompleted(now: Instant) {
        completedCount++
        state = CleanupState(lastCleanupCompleted = now)
    }
}
