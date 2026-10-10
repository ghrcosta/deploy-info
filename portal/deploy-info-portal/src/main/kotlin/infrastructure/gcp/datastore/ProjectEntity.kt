package infrastructure.gcp.datastore

import com.google.cloud.spring.data.datastore.core.mapping.Entity
import domain.Project
import org.springframework.data.annotation.Id

@Entity(name = "deploy-info/project")
class ProjectEntity(
    @Id
    val projectId: String,

    val group: String? = null,
    val serviceAccount: String,
) {
    constructor(project: Project) : this(
        projectId = project.projectId,
        group = project.group,
        serviceAccount = project.serviceAccount,
    )

    fun toModel(): Project = Project(
        projectId = projectId,
        group = group,
        serviceAccount = serviceAccount,
    )
}