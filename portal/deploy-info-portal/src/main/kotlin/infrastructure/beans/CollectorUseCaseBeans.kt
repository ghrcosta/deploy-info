package infrastructure.beans

import application.*
import application.collector.CreateDeployLinkUseCase
import application.collector.GetStorageBucketUseCase
import com.google.cloud.storage.Storage
import infrastructure.config.DeployInfoProperties
import infrastructure.gcp.storage.GcsStorageCleaner
import infrastructure.scheduling.SimpleRetryScheduler
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the collector-facing capabilities: the storage-bucket lookup and the deploy-link creation —
 * the GCP listers, the Datastore-backed link repository, the Cloud Storage cleaner (resolved lazily
 * so the portal starts without Storage credentials), and the retry scheduler. The file-content
 * reader and use case live in `PortalUseCaseBeans`.
 */
@Configuration
class CollectorUseCaseBeans(
    private val projectRepository: ProjectRepository,
    private val appEngineLister: GcpAppEngineLister,
    private val cloudRunLister: GcpCloudRunLister,
    private val deployLinkRepository: DeployLinkRepository,
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
    fun createDeployLinkUseCase(
        storageCleaner: StorageCleaner,
        retryScheduler: RetryScheduler,
    ): CreateDeployLinkUseCase = CreateDeployLinkUseCase(
        projectRepository = projectRepository,
        appEngineLister = appEngineLister,
        cloudRunLister = cloudRunLister,
        deployLinkRepository = deployLinkRepository,
        storageCleaner = storageCleaner,
        retryScheduler = retryScheduler,
        window = properties.linking.window,
    )
}