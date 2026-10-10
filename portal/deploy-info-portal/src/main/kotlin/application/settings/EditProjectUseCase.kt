package application.settings

import application.ProjectRepository
import application.ServiceAccountIssue
import application.ServiceAccountValidator
import domain.Project

class EditProjectUseCase(
    private val projectRepository: ProjectRepository,
    private val serviceAccountValidator: ServiceAccountValidator,
) {
    fun execute(modifiedProject: Project): Output {
        val existingProject = projectRepository.get(modifiedProject.projectId)

        var issueProjectNotFound = false
        var serviceAccountIssue = ServiceAccountIssue.NONE
        var projectsInDatabase: List<Project> = emptyList()

        if (existingProject != null) {
            // Validation is only needed when the target service account changed — an unchanged one
            // was already validated when the project was added or last edited.
            if (existingProject.serviceAccount != modifiedProject.serviceAccount) {
                serviceAccountIssue = serviceAccountValidator.validate(modifiedProject)
            }

            if (serviceAccountIssue == ServiceAccountIssue.NONE) {
                projectRepository.save(modifiedProject)
                projectsInDatabase = projectRepository.getAll()
            }
        } else {
            issueProjectNotFound = true
        }

        return Output(
            issueProjectNotFound = issueProjectNotFound,
            issueServiceAccountError = serviceAccountIssue != ServiceAccountIssue.NONE,
            serviceAccountIssue = serviceAccountIssue,
            projectsInRepository = projectsInDatabase
        )
    }

    class Output(
        val issueProjectNotFound: Boolean,
        val issueServiceAccountError: Boolean,
        val serviceAccountIssue: ServiceAccountIssue = ServiceAccountIssue.NONE,
        val projectsInRepository: List<Project>
    ) {
        fun issuesFound() = issueProjectNotFound || issueServiceAccountError
    }
}
