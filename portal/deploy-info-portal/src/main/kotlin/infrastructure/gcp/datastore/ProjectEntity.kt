package infrastructure.gcp.datastore

import com.google.cloud.spring.data.datastore.core.mapping.Entity
import domain.Project
import org.springframework.data.annotation.Id

@Entity(name = "project")
class ProjectEntity(
    @Id
    val name: String,

    val group: String? = null,
    val serviceAccount: String,
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