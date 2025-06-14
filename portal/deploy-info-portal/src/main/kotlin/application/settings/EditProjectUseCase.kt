package application.settings

import application.ProjectRepository
import domain.Project

class EditProjectUseCase(
    private val projectRepository: ProjectRepository
) {
    fun execute(modifiedProject: Project): Output {
        val existingProject = projectRepository.get(modifiedProject.name)

        var issueProjectNotFound = false
        var issueServiceAccountError = false
        var projectsInDatabase: List<Project>? = null

        if (existingProject != null) {
            if (existingProject.serviceAccount != modifiedProject.serviceAccount) {
                // TODO: Test if name + serviceAccount are working
            }

            if (!issueServiceAccountError) {
                projectRepository.save(modifiedProject)
                projectsInDatabase = projectRepository.getAll()
            }
        } else {
            issueProjectNotFound = true
        }

        return Output(
            issueProjectNotFound = issueProjectNotFound,
            issueServiceAccountError = issueServiceAccountError,
            projectsInRepository = projectsInDatabase
        )
    }

    class Output(
        val issueProjectNotFound: Boolean,
        val issueServiceAccountError: Boolean,
        val projectsInRepository: List<Project>?
    ) {
        fun issuesFound() = issueProjectNotFound || issueServiceAccountError
    }
}