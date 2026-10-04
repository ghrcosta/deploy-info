package tests.fakes

import application.ProjectRepository
import domain.Project

/**
 * In-memory [ProjectRepository] used in unit tests (and by Spring tests through `@MockitoBean`-free
 * wiring when needed) instead of the real Datastore-backed repository, so no external dependency is
 * required. Saving a project with an existing name replaces it.
 */
class FakeProjectRepository(
    private val projects: MutableList<Project> = mutableListOf(),
) : ProjectRepository {

    override fun getAll(): List<Project> = projects.toList()

    override fun get(projectName: String): Project? = projects.firstOrNull { it.name == projectName }

    override fun save(project: Project) {
        projects.removeAll { it.name == project.name }
        projects.add(project)
    }

    override fun delete(projectName: String) {
        projects.removeAll { it.name == projectName }
    }
}
