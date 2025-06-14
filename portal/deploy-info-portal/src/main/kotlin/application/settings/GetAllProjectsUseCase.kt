package application.settings

import application.ProjectRepository
import domain.Project

class GetAllProjectsUseCase (
    private val projectRepository: ProjectRepository
) {
    fun execute(): Output {
        return Output(projectRepository.getAll())
    }

    class Output (val projectsInRepository: List<Project>)
}