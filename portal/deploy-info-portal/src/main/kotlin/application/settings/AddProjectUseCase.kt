package application.settings

import application.ProjectRepository
import application.ServiceAccountIssue
import application.ServiceAccountValidator
import domain.Project

class AddProjectUseCase(
    private val projectRepository: ProjectRepository,
    private val serviceAccountValidator: ServiceAccountValidator,
) {
    fun execute(newProject: Project): Output {
        val existingProjects = projectRepository.getAll()

        val issueProjectIdConflict = existingProjects.any { it.projectId == newProject.projectId }

        var serviceAccountIssue = ServiceAccountIssue.NONE
        if (!issueProjectIdConflict) {
            serviceAccountIssue = serviceAccountValidator.validate(newProject)
        }

        var projectsInDatabase: List<Project> = emptyList()
        if (!issueProjectIdConflict && serviceAccountIssue == ServiceAccountIssue.NONE) {
            projectRepository.save(newProject)
            projectsInDatabase = projectRepository.getAll()
        }

        return Output(
            issueProjectIdConflict = issueProjectIdConflict,
            issueServiceAccountError = serviceAccountIssue != ServiceAccountIssue.NONE,
            serviceAccountIssue = serviceAccountIssue,
            projectsInRepository = projectsInDatabase
        )
    }

    class Output(
        val issueProjectIdConflict: Boolean,
        val issueServiceAccountError: Boolean,
        val serviceAccountIssue: ServiceAccountIssue = ServiceAccountIssue.NONE,
        val projectsInRepository: List<Project>
    ) {
        fun issuesFound() = issueProjectIdConflict || issueServiceAccountError
    }
}
