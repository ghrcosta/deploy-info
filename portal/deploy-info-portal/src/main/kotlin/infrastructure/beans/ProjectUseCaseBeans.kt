package infrastructure.beans

import application.ProjectRepository
import application.ServiceAccountValidator
import application.project.AddProjectUseCase
import application.project.DeleteProjectUseCase
import application.project.EditProjectUseCase
import application.project.GetAllProjectsUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ProjectUseCaseBeans(
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
