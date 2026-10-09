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

        val issueNameConflict = existingProjects.any { it.name == newProject.name }

        var serviceAccountIssue = ServiceAccountIssue.NONE
        if (!issueNameConflict) {
            serviceAccountIssue = serviceAccountValidator.validate(newProject)
        }

        var projectsInDatabase: List<Project> = emptyList()
        if (!issueNameConflict && serviceAccountIssue == ServiceAccountIssue.NONE) {
            projectRepository.save(newProject)
            projectsInDatabase = projectRepository.getAll()
        }

        return Output(
            issueNameConflict = issueNameConflict,
            issueServiceAccountError = serviceAccountIssue != ServiceAccountIssue.NONE,
            serviceAccountIssue = serviceAccountIssue,
            projectsInRepository = projectsInDatabase
        )
    }

    class Output(
        val issueNameConflict: Boolean,
        val issueServiceAccountError: Boolean,
        val serviceAccountIssue: ServiceAccountIssue = ServiceAccountIssue.NONE,
        val projectsInRepository: List<Project>
    ) {
        fun issuesFound() = issueNameConflict || issueServiceAccountError
    }
}
