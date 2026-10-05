package infrastructure.beans

import application.*
import application.cleanup.CleanupInvalidDeployLinksUseCase
import application.cleanup.CleanupStateRepository
import application.cleanup.RunCleanupIfDueUseCase
import infrastructure.config.DeployInfoProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Wires the validity/cleanup logic: the sweep use case and the on-request trigger that runs it
 * from tree requests when due. There is deliberately no fixed-interval scheduler — the portal runs
 * on App Engine Standard, where idle instances are terminated, so a scheduled job either never
 * fires or keeps an instance alive forever. The clock is the system clock in UTC (the sweep only
 * compares timestamps against the deploy links' collect timestamps).
 */
@Configuration
class CleanupBeans(private val properties: DeployInfoProperties) {

    @Bean
    fun systemClock(): Clock = Clock.systemUTC()

    @Bean
    fun cleanupInvalidDeployLinksUseCase(
        projectRepository: ProjectRepository,
        appEngineLister: GcpAppEngineLister,
        cloudRunLister: GcpCloudRunLister,
        deployLinkRepository: DeployLinkRepository,
        storageCleaner: StorageCleaner,
        clock: Clock,
    ): CleanupInvalidDeployLinksUseCase = CleanupInvalidDeployLinksUseCase(
        projectRepository = projectRepository,
        appEngineLister = appEngineLister,
        cloudRunLister = cloudRunLister,
        deployLinkRepository = deployLinkRepository,
        storageCleaner = storageCleaner,
        clock = clock,
        gracePeriod = properties.cleanup.gracePeriod,
    )

    @Bean
    fun runCleanupIfDueUseCase(
        cleanupInvalidDeployLinksUseCase: CleanupInvalidDeployLinksUseCase,
        cleanupStateRepository: CleanupStateRepository,
        clock: Clock,
    ): RunCleanupIfDueUseCase = RunCleanupIfDueUseCase(
        enabled = properties.cleanup.enabled,
        sweep = { cleanupInvalidDeployLinksUseCase.execute() },
        cleanupStateRepository = cleanupStateRepository,
        clock = clock,
        interval = properties.cleanup.interval,
    )
}
