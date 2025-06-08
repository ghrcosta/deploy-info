package application.settings

import application.ProjectRepository
import domain.Project

class AddProjectUseCase(
    private val projectRepository: ProjectRepository
) {
    fun execute(newProject: Project): Output {
        val existingProjects = projectRepository.getAll()

        val issueHasNameConflict = existingProjects.any { it.name == newProject.name }

        var issueServiceAccountError = false
        // TODO: Test if name + serviceAccount are working

        var projectsInDatabase: List<Project>? = null
        if (!issueHasNameConflict) {
            projectRepository.save(newProject)
            projectsInDatabase = projectRepository.getAll()
        }

        return Output(
            issueHasNameConflict = issueHasNameConflict,
            issueServiceAccountError = issueServiceAccountError,
            projectsInDatabase = projectsInDatabase
        )
    }

    class Output(
        val issueHasNameConflict: Boolean,
        val issueServiceAccountError: Boolean,
        val projectsInDatabase: List<Project>?
    ) {
        fun issuesFound() = issueHasNameConflict || issueServiceAccountError
    }
}