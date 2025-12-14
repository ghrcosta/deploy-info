package domain

data class Project(
    var name: String,
    var group: String? = null,
    var serviceAccount: String
)