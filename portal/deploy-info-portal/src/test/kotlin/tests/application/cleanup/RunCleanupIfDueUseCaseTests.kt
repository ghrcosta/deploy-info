package tests.application.cleanup

import application.cleanup.RunCleanupIfDueUseCase
import org.junit.jupiter.api.Test
import tests.fakes.FakeCleanupStateRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val NOW: Instant = Instant.ofEpochSecond(10_000_000)
private val INTERVAL: Duration = Duration.ofMinutes(15)

class RunCleanupIfDueUseCaseTests {

    private lateinit var fakeCleanupStateRepository: FakeCleanupStateRepository
    private lateinit var useCase: RunCleanupIfDueUseCase
    private var sweepRuns = 0

    @BeforeTest
    fun setup() {
        fakeCleanupStateRepository = FakeCleanupStateRepository()
        sweepRuns = 0
        useCase = useCase(disabled = false)
    }

    private fun useCase(
        disabled: Boolean,
        sweep: Runnable = Runnable { sweepRuns++ },
    ) = RunCleanupIfDueUseCase(
        enabled = !disabled,
        sweep = sweep,
        cleanupStateRepository = fakeCleanupStateRepository,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
        interval = INTERVAL,
    )

    /** Simulates a sweep that completed `ago` ago (the first trigger run completes at NOW). */
    private fun completedSweepAgo(ago: Duration) {
        useCase.executeIfDue()
        fakeCleanupStateRepository.state = fakeCleanupStateRepository.state
            ?.copy(lastCleanupCompleted = NOW.minus(ago))
    }

    @Test
    fun `First request (no state) - the sweep runs and is marked completed`() {
        useCase.executeIfDue()

        assertEquals(1, sweepRuns)
        assertEquals(NOW, fakeCleanupStateRepository.state?.lastCleanupCompleted)
    }

    @Test
    fun `Disabled - nothing is read or swept`() {
        val disabledUseCase = useCase(disabled = true)

        disabledUseCase.executeIfDue()

        assertEquals(0, sweepRuns)
        assertEquals(0, fakeCleanupStateRepository.completedCount)
    }

    @Test
    fun `Within the interval since the last completed sweep - skipped`() {
        completedSweepAgo(INTERVAL.minusMinutes(1))

        useCase.executeIfDue()

        assertEquals(1, sweepRuns)
        assertEquals(1, fakeCleanupStateRepository.completedCount)
    }

    @Test
    fun `Exactly at the interval after the last completed sweep - the sweep runs again`() {
        completedSweepAgo(INTERVAL)

        useCase.executeIfDue()

        assertEquals(2, sweepRuns)
    }

    @Test
    fun `After the interval - the sweep runs again`() {
        completedSweepAgo(INTERVAL.plusMinutes(1))

        useCase.executeIfDue()

        assertEquals(2, sweepRuns)
        assertEquals(NOW, fakeCleanupStateRepository.state?.lastCleanupCompleted)
    }

    @Test
    fun `Sweep failure - not marked completed, so the next request retries immediately`() {
        val failingUseCase = useCase(
            disabled = false,
            sweep = Runnable {
                sweepRuns++
                throw IllegalStateException("boom")
            },
        )

        failingUseCase.executeIfDue()

        assertNull(fakeCleanupStateRepository.state?.lastCleanupCompleted)
        assertEquals(1, sweepRuns)

        useCase.executeIfDue() // not completed, so the next request is due right away
        assertEquals(2, sweepRuns)
    }
}
