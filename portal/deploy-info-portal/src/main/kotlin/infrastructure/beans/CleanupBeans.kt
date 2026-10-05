package infrastructure.beans

import application.*
import application.cleanup.CleanupInvalidDeployLinksUseCase
import infrastructure.config.DeployInfoProperties
import infrastructure.scheduling.CleanupScheduler
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Wires the validity/cleanup logic: the use case plus, only when `deploy-info.cleanup.enabled` is
 * `true`, the scheduler that runs it on the configured interval. The clock is the system clock in
 * UTC (the sweep only compares timestamps against the deploy links' collect timestamps).
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

    /** Only scheduled when the properties file turns the cleanup on; there is no code default. */
    @Bean
    @ConditionalOnProperty(name = ["deploy-info.cleanup.enabled"], havingValue = "true")
    fun cleanupScheduler(
        cleanupInvalidDeployLinksUseCase: CleanupInvalidDeployLinksUseCase,
    ): CleanupScheduler = CleanupScheduler(
        sweep = { cleanupInvalidDeployLinksUseCase.execute() },
        interval = properties.cleanup.interval,
    ).apply { start() }
}