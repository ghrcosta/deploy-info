package domain

data class Project(
    var name: String,
    var category: String? = null,
    var serviceAccount: String
)