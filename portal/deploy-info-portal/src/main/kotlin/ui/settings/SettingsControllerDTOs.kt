package ui.settings

import domain.Project

data class ProjectDTO(
    val name: String,
    val group: String? = null,
    val serviceAccount: String
) {
    constructor(project: Project) : this(
        name = project.name,
        group = project.group,
        serviceAccount = project.serviceAccount,
    )

    fun toModel(): Project = Project(
        name = name,
        group = group,
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

        /** The specific service-account issue (a [application.ServiceAccountIssue] code), when any. */
        val serviceAccountIssue: String? = null,

        /**
         * The portal's own service account email, resolved server-side — the principal to grant the
         * IAM roles to in the remediation instructions. Null when it cannot be determined.
         */
        val portalServiceAccount: String? = null,
    )
}

class EditProjectResultDTO(
    val issues: IssuesDTO? = null,
    val projects: List<ProjectDTO>? = null,
) {
    class IssuesDTO (
        val issueProjectNotFound: Boolean,
        val issueServiceAccountError: Boolean,

        /** The specific service-account issue (a [application.ServiceAccountIssue] code), when any. */
        val serviceAccountIssue: String? = null,

        /**
         * The portal's own service account email, resolved server-side — the principal to grant the
         * IAM roles to in the remediation instructions. Null when it cannot be determined.
         */
        val portalServiceAccount: String? = null,
    )
}
