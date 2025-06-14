package application.settings

import application.ProjectRepository
import domain.Project

class AddProjectUseCase(
    private val projectRepository: ProjectRepository
) {
    fun execute(newProject: Project): Output {
        val existingProjects = projectRepository.getAll()

        val issueNameConflict = existingProjects.any { it.name == newProject.name }

        var issueServiceAccountError = false
        // TODO: Test if name + serviceAccount are working

        var projectsInDatabase: List<Project>? = null
        if (!issueNameConflict) {
            projectRepository.save(newProject)
            projectsInDatabase = projectRepository.getAll()
        }

        return Output(
            issueNameConflict = issueNameConflict,
            issueServiceAccountError = issueServiceAccountError,
            projectsInRepository = projectsInDatabase
        )
    }

    class Output(
        val issueNameConflict: Boolean,
        val issueServiceAccountError: Boolean,
        val projectsInRepository: List<Project>?
    ) {
        fun issuesFound() = issueNameConflict || issueServiceAccountError
    }
}