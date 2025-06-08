package ui.settings

import domain.Project

class ProjectDTO(
    private val name: String,
    private val category: String? = null,
    private val serviceAccount: String
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