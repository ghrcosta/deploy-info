package infrastructure.beans

import application.*
import application.cleanup.CleanupInvalidDeploymentLinksUseCase
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
    fun cleanupInvalidDeploymentLinksUseCase(
        projectRepository: ProjectRepository,
        appEngineLister: GcpAppEngineLister,
        cloudRunLister: GcpCloudRunLister,
        deploymentLinkRepository: DeploymentLinkRepository,
        storageCleaner: StorageCleaner,
        clock: Clock,
    ): CleanupInvalidDeploymentLinksUseCase = CleanupInvalidDeploymentLinksUseCase(
        projectRepository = projectRepository,
        appEngineLister = appEngineLister,
        cloudRunLister = cloudRunLister,
        deploymentLinkRepository = deploymentLinkRepository,
        storageCleaner = storageCleaner,
        clock = clock,
        gracePeriod = properties.cleanup.gracePeriod,
    )

    @Bean
    fun runCleanupIfDueUseCase(
        cleanupInvalidDeploymentLinksUseCase: CleanupInvalidDeploymentLinksUseCase,
        cleanupStateRepository: CleanupStateRepository,
        clock: Clock,
    ): RunCleanupIfDueUseCase = RunCleanupIfDueUseCase(
        enabled = properties.cleanup.enabled,
        sweep = { cleanupInvalidDeploymentLinksUseCase.execute() },
        cleanupStateRepository = cleanupStateRepository,
        clock = clock,
        interval = properties.cleanup.interval,
    )
}
