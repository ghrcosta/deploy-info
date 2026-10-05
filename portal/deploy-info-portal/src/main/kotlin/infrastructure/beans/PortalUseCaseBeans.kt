package infrastructure.beans

import application.DeployLinkRepository
import application.FileContentReader
import application.ProjectRepository
import application.content.GetDeployContentUseCase
import application.tree.GetDeployTreeUseCase
import com.google.cloud.storage.Storage
import infrastructure.config.DeployInfoProperties
import infrastructure.gcp.storage.GcsFileContentReader
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Wires the main-screen capabilities: the navigator tree and the upload folder content reading. */
@Configuration
class PortalUseCaseBeans(
    private val projectRepository: ProjectRepository,
    private val deployLinkRepository: DeployLinkRepository,
    private val storageProvider: ObjectProvider<Storage>,
    private val properties: DeployInfoProperties,
) {

    /** Resolved lazily, so the portal starts (and the tests run) without Storage credentials. */
    @Bean
    fun fileContentReader(): FileContentReader =
        GcsFileContentReader({ storageProvider.getObject() }, properties.storageBucket)

    @Bean
    fun getDeployTreeUseCase(): GetDeployTreeUseCase = GetDeployTreeUseCase(
        projectRepository = projectRepository,
        deployLinkRepository = deployLinkRepository,
    )

    @Bean
    fun getDeployContentUseCase(): GetDeployContentUseCase = GetDeployContentUseCase(
        fileContentReader = fileContentReader(),
    )
}
