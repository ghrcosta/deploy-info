package infrastructure.beans

import application.*
import application.cleanup.CleanupInvalidDeployLinksUseCase
import application.cleanup.CleanupStateRepository
import application.cleanup.RunCleanupIfDueUseCase
import infrastructure.config.DeployInfoProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

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
