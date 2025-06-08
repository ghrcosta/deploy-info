package application.settings

import application.ProjectRepository
import domain.Project

class AddProjectUseCase(
    private val projectRepository: ProjectRepository
) {
    fun execute(newProject: Project): Output {
        val existingProjects = projectRepository.getAll()

        val hasNameConflict = existingProjects.any { it.name == newProject.name }

        // TODO: Test if name + serviceAccount are working

        return Output(hasNameConflict)
    }

    class Output(
        val hasNameConflict: Boolean,
    )
}