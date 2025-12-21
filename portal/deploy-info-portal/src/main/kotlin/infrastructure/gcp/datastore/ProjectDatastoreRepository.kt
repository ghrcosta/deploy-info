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

    override fun get(projectName: String): Project? =
        datastoreTemplate.findById(projectName, ProjectEntity::class.java)?.toModel()

    override fun save(project: Project) {
        datastoreTemplate.save(ProjectEntity(project))
    }

    override fun delete(projectName: String) {
        datastoreTemplate.deleteById(projectName, ProjectEntity::class.java)
    }
}