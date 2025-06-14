package ui.settings

import domain.Project

data class ProjectDTO(
    val name: String,
    val category: String? = null,
    val serviceAccount: String
) {
    constructor(project: Project) : this(
        name = project.name,
        category = project.category,
        serviceAccount = project.serviceAccount,
    )

    fun toModel(): Project = Project(
        name = name,
        category = category,
        serviceAccount = serviceAccount,
    )
}

class AddProjectResultDTO(
    val issues: IssuesDTO? = null,
    val projects: List<ProjectDTO>? = null,
) {
    class IssuesDTO (
        val issueNameConflict: Boolean,
        val issueServiceAccountError: Boolean,
    )
}

class EditProjectResultDTO(
    val issues: IssuesDTO? = null,
    val projects: List<ProjectDTO>? = null,
) {
    class IssuesDTO (
        val issueProjectNotFound: Boolean,
        val issueServiceAccountError: Boolean,
    )
}