package infrastructure.beans

import application.ProjectRepository
import application.settings.AddProjectUseCase
import application.settings.DeleteProjectUseCase
import application.settings.EditProjectUseCase
import application.settings.GetAllProjectsUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SettingsUseCaseBeans(
    private val projectRepository: ProjectRepository,
) {
    @Bean
    fun getAllProjectsUseCase() = GetAllProjectsUseCase(projectRepository)

    @Bean
    fun addProjectUseCase() = AddProjectUseCase(projectRepository)

    @Bean
    fun editProjectUseCase() = EditProjectUseCase(projectRepository)

    @Bean
    fun deleteProjectUseCase() = DeleteProjectUseCase(projectRepository)
}