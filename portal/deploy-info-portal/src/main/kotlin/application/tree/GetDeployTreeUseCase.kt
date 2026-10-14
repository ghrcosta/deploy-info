package application.tree

import application.DeployLinkRepository
import application.ProjectRepository
import domain.DeployLink
import domain.DeployType
import domain.Project
import java.time.Instant

/**
 * Builds the main-screen navigator tree: a hierarchy of group > project > service > version from
 * the configured projects and the deploy links in the database.
 *
 * The tree is built purely from Datastore. The data the file-viewer header needs (deploy type,
 * location, url, author, timestamp) is carried on the version nodes together with the upload's
 * `storageFolder`, so the file-content endpoint won't have to query the database again.
 *
 * Items are sorted alphabetically on all levels; ungrouped projects go in an empty-name group,
 * which is sorted last.
 *
 * The application layer stays Spring-free (see CreateDeployLinkUseCase).
 */
class GetDeployTreeUseCase(
    private val projectRepository: ProjectRepository,
    private val deployLinkRepository: DeployLinkRepository,
) {

    /** The top level: one entry per project group; the empty name is the "ungrouped" group. */
    data class GroupEntry(val name: String, val projects: List<ProjectEntry>)

    data class ProjectEntry(val projectId: String, val services: List<ServiceEntry>)

    data class ServiceEntry(
        val name: String,
        val type: DeployType,
        val versions: List<VersionEntry>,
    )

    /**
     * One selectable version node. `id` is the deploy identity's key name
     * (`<project>_<TYPE>_<location|->_<service>_<version>`), `name` is the version id, and the
     * remaining fields feed the file-viewer header and the file-content endpoint.
     */
    data class VersionEntry(
        val id: String,
        val name: String,
        val type: DeployType,
        val location: String?,
        val url: String?,
        val author: String,
        val timestamp: Instant,
        val storageFolder: String,
    )

    fun execute(): List<GroupEntry> {
        val projectsByName: Map<String, Project> = projectRepository.getAll().associateBy { it.projectId }
        val tree = mutableMapOf<String, MutableMap<String, MutableMap<ServiceKey, MutableList<VersionEntry>>>>()

        for (link in deployLinkRepository.getAll()) {
            val groupName = projectsByName[link.projectId]?.group ?: UNGROUPED
            tree.getOrPut(groupName) { mutableMapOf() }
                .getOrPut(link.projectId) { mutableMapOf() }
                .getOrPut(ServiceKey(link.deployType, link.serviceId)) { mutableListOf() }
                .add(link.toVersionEntry())
        }
        return tree.toGroupEntries()
    }

    private fun DeployLink.toVersionEntry() = VersionEntry(
        id = keyName,
        name = versionId,
        type = deployType,
        location = location,
        url = url,
        author = userEmail,
        timestamp = collectTimestamp,
        storageFolder = storageFolder,
    )

    private fun Map<String, MutableMap<String, MutableMap<ServiceKey, MutableList<VersionEntry>>>>.toGroupEntries() =
        entries
            .sortedWith(compareBy({ if (it.key == UNGROUPED) 1 else 0 }, { it.key }))
            .map { (groupName, projects) ->
                GroupEntry(
                    name = groupName,
                    projects = projects.entries
                        .sortedBy { it.key }
                        .map { (projectId, services) ->
                            ProjectEntry(projectId = projectId, services = services.toServiceEntries())
                        },
                )
            }

    private fun Map<ServiceKey, MutableList<VersionEntry>>.toServiceEntries() =
        entries
            .sortedBy { it.key.serviceId }
            .map { (serviceKey, versions) ->
                ServiceEntry(
                    name = serviceKey.serviceId,
                    type = serviceKey.deployType,
                    versions = versions.sortedBy { it.name },
                )
            }

    private data class ServiceKey(val deployType: DeployType, val serviceId: String)

    private companion object {
        /** The group of projects without a group (and links of unconfigured projects). */
        const val UNGROUPED = ""
    }
}