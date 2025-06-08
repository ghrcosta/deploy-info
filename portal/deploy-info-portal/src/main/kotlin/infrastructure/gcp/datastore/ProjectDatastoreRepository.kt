package infrastructure.gcp.datastore

import application.ProjectRepository
import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import domain.Project
import org.springframework.stereotype.Repository

@Repository
class ProjectDatastoreRepository(
    private val datastoreTemplate: DatastoreTemplate
): ProjectRepository {

    override fun getAll(): List<Project> =
        datastoreTemplate.findAll(ProjectEntity::class.java).map { it.toModel() }

    override fun get(projectId: Long): Project? =
        datastoreTemplate.findById(projectId, ProjectEntity::class.java)?.toModel()

    override fun save(project: Project) {
        datastoreTemplate.save(ProjectEntity(project))
    }
}