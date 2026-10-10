package application.settings

import application.ProjectRepository
import domain.Project

class DeleteProjectUseCase(
    private val projectRepository: ProjectRepository
) {
    fun execute(projectId: String): Output {
        projectRepository.delete(projectId)

        return Output(projectRepository.getAll())
    }

    class Output(
        val projectsInRepository: List<Project>
    )
}