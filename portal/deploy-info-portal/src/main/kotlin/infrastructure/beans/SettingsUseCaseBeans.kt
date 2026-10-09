package infrastructure.beans

import application.ProjectRepository
import application.ServiceAccountValidator
import application.settings.AddProjectUseCase
import application.settings.DeleteProjectUseCase
import application.settings.EditProjectUseCase
import application.settings.GetAllProjectsUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SettingsUseCaseBeans(
    private val projectRepository: ProjectRepository,
    private val serviceAccountValidator: ServiceAccountValidator,
) {
    @Bean
    fun getAllProjectsUseCase() = GetAllProjectsUseCase(projectRepository)

    @Bean
    fun addProjectUseCase() = AddProjectUseCase(projectRepository, serviceAccountValidator)

    @Bean
    fun editProjectUseCase() = EditProjectUseCase(projectRepository, serviceAccountValidator)

    @Bean
    fun deleteProjectUseCase() = DeleteProjectUseCase(projectRepository)
}
