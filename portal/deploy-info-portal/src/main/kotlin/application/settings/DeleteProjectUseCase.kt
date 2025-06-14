package application.settings

import application.ProjectRepository
import domain.Project

class DeleteProjectUseCase(
    private val projectRepository: ProjectRepository
) {
    fun execute(projectName: String): Output {
        projectRepository.delete(projectName)

        return Output(projectRepository.getAll())
    }

    class Output(
        val projectsInRepository: List<Project>
    )
}