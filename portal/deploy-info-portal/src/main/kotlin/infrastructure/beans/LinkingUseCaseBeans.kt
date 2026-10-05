package infrastructure.beans

import application.*
import application.linking.CreateDeployLinkUseCase
import com.google.cloud.storage.Storage
import infrastructure.config.DeployInfoProperties
import infrastructure.gcp.storage.GcsStorageCleaner
import infrastructure.scheduling.SimpleRetryScheduler
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the linking use case: the GCP listers, the Datastore-backed link repository, the Cloud
 * Storage cleaner (resolved lazily so the portal starts without Storage credentials), the retry
 * scheduler, and the matching window duration.
 */
@Configuration
class LinkingUseCaseBeans(
    private val projectRepository: ProjectRepository,
    private val appEngineLister: GcpAppEngineLister,
    private val cloudRunLister: GcpCloudRunLister,
    private val deployLinkRepository: DeployLinkRepository,
    private val storageProvider: ObjectProvider<Storage>,
    private val properties: DeployInfoProperties,
) {

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
