package infrastructure.beans

import application.*
import application.collector.CreateDeploymentLinkUseCase
import application.collector.GetStorageBucketUseCase
import com.google.cloud.storage.Storage
import infrastructure.config.DeployInfoProperties
import infrastructure.gcp.storage.GcsStorageCleaner
import infrastructure.scheduling.SimpleRetryScheduler
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CollectorUseCaseBeans(
    private val projectRepository: ProjectRepository,
    private val appEngineLister: GcpAppEngineLister,
    private val cloudRunLister: GcpCloudRunLister,
    private val deploymentLinkRepository: DeploymentLinkRepository,
    private val storageProvider: ObjectProvider<Storage>,
    private val properties: DeployInfoProperties,
) {

    @Bean
    fun getStorageBucketUseCase(): GetStorageBucketUseCase = GetStorageBucketUseCase(properties)

    @Bean
    fun storageCleaner(): StorageCleaner =
        GcsStorageCleaner({ storageProvider.getObject() }, properties.storageBucket)

    @Bean
    fun retryScheduler(): RetryScheduler = SimpleRetryScheduler()

    @Bean
    fun createDeploymentLinkUseCase(
        storageCleaner: StorageCleaner,
        retryScheduler: RetryScheduler,
    ): CreateDeploymentLinkUseCase = CreateDeploymentLinkUseCase(
        projectRepository = projectRepository,
        appEngineLister = appEngineLister,
        cloudRunLister = cloudRunLister,
        deploymentLinkRepository = deploymentLinkRepository,
        storageCleaner = storageCleaner,
        retryScheduler = retryScheduler,
        window = properties.linking.window,
    )
}