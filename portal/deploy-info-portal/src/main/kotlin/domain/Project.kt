package domain

data class Project(
    var projectId: String,
    var group: String? = null,
    var serviceAccount: String
)