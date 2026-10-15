package infrastructure.beans

import application.DeploymentLinkRepository
import application.FileContentReader
import application.ProjectRepository
import application.content.GetDeploymentContentUseCase
import application.tree.GetDeploymentTreeUseCase
import com.google.cloud.storage.Storage
import infrastructure.config.DeployInfoProperties
import infrastructure.gcp.storage.GcsFileContentReader
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PortalUseCaseBeans(
    private val projectRepository: ProjectRepository,
    private val deploymentLinkRepository: DeploymentLinkRepository,
    private val storageProvider: ObjectProvider<Storage>,
    private val properties: DeployInfoProperties,
) {

    @Bean
    fun fileContentReader(): FileContentReader =
        GcsFileContentReader({ storageProvider.getObject() }, properties.storageBucket)

    @Bean
    fun getDeploymentTreeUseCase(): GetDeploymentTreeUseCase = GetDeploymentTreeUseCase(
        projectRepository = projectRepository,
        deploymentLinkRepository = deploymentLinkRepository,
    )

    @Bean
    fun getDeploymentContentUseCase(): GetDeploymentContentUseCase = GetDeploymentContentUseCase(
        fileContentReader = fileContentReader(),
    )
}
