package infrastructure.gcp.datastore

import com.google.cloud.spring.data.datastore.core.mapping.Entity
import domain.Project
import org.springframework.data.annotation.Id

@Entity(name = "project")
class ProjectEntity(
    @Id
    val name: String,

    val category: String? = null,
    val serviceAccount: String,
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