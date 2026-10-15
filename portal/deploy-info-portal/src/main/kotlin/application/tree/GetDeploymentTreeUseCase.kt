package application.tree

import application.DeploymentLinkRepository
import application.ProjectRepository
import domain.DeploymentLink
import domain.DeploymentType
import domain.Project
import java.time.Instant

/**
 * Builds the main-screen navigator tree: a hierarchy of group > project > service > version from
 * the configured projects and the deployment links in the database.
 *
 * The tree is built purely from Datastore. The data the file-viewer header needs (deployment type,
 * location, url, author, timestamp) is carried on the version nodes together with the upload's
 * `storageFolder`, so the file-content endpoint won't have to query the database again.
 *
 * Items are sorted on all levels. Groups, projects and services sort alphabetically
 * (case-insensitive, with a case-sensitive tiebreak so the order is deterministic); version names
 * sort naturally: embedded digit runs compare numerically, so "v2" comes before "v10". Ungrouped
 * projects go in an empty-name group, which is sorted last.
 *
 * The application layer stays Spring-free (see CreateDeploymentLinkUseCase).
 */
class GetDeploymentTreeUseCase(
    private val projectRepository: ProjectRepository,
    private val deploymentLinkRepository: DeploymentLinkRepository,
) {

    /** The top level: one entry per project group; the empty name is the "ungrouped" group. */
    data class GroupEntry(val name: String, val projects: List<ProjectEntry>)

    data class ProjectEntry(val projectId: String, val services: List<ServiceEntry>)

    data class ServiceEntry(
        val name: String,
        val type: DeploymentType,
        val versions: List<VersionEntry>,
    )

    /**
     * One selectable version node. `id` is the deployment identity's key name
     * (`<project>_<TYPE>_<location|->_<service>_<version>`), `name` is the version id, and the
     * remaining fields feed the file-viewer header and the file-content endpoint.
     */
    data class VersionEntry(
        val id: String,
        val name: String,
        val type: DeploymentType,
        val location: String?,
        val url: String?,
        val author: String,
        val timestamp: Instant,
        val storageFolder: String,
    )

    fun execute(): List<GroupEntry> {
        val projectsByName: Map<String, Project> = projectRepository.getAll().associateBy { it.projectId }
        val tree = mutableMapOf<String, MutableMap<String, MutableMap<ServiceKey, MutableList<VersionEntry>>>>()

        for (link in deploymentLinkRepository.getAll()) {
            val groupName = projectsByName[link.projectId]?.group ?: UNGROUPED
            tree.getOrPut(groupName) { mutableMapOf() }
                .getOrPut(link.projectId) { mutableMapOf() }
                .getOrPut(ServiceKey(link.deploymentType, link.serviceId)) { mutableListOf() }
                .add(link.toVersionEntry())
        }
        return tree.toGroupEntries()
    }

    private fun DeploymentLink.toVersionEntry() = VersionEntry(
        id = keyName,
        name = versionId,
        type = deploymentType,
        location = location,
        url = url,
        author = userEmail,
        timestamp = collectTimestamp,
        storageFolder = storageFolder,
    )

    private fun Map<String, MutableMap<String, MutableMap<ServiceKey, MutableList<VersionEntry>>>>.toGroupEntries() =
        entries
            .sortedWith(compareBy({ if (it.key == UNGROUPED) 1 else 0 }, { it.key.lowercase() }, { it.key }))
            .map { (groupName, projects) ->
                GroupEntry(
                    name = groupName,
                    projects = projects.entries
                        .sortedWith(compareBy({ it.key.lowercase() }, { it.key }))
                        .map { (projectId, services) ->
                            ProjectEntry(projectId = projectId, services = services.toServiceEntries())
                        },
                )
            }

    private fun Map<ServiceKey, MutableList<VersionEntry>>.toServiceEntries() =
        entries
            .sortedWith(compareBy({ it.key.serviceId.lowercase() }, { it.key.serviceId }, { it.key.deploymentType }))
            .map { (serviceKey, versions) ->
                ServiceEntry(
                    name = serviceKey.serviceId,
                    type = serviceKey.deploymentType,
                    versions = versions.sortedWith(Comparator { x, y -> compareNaturalOrder(x.name, y.name) }),
                )
            }

    private data class ServiceKey(val deploymentType: DeploymentType, val serviceId: String)

    private companion object {
        /** The group of projects without a group (and links of unconfigured projects). */
        const val UNGROUPED = ""
    }
}

/**
 * Natural name order for version names: digit runs compare numerically ("v2" < "v10"), text compares
 * case-insensitively, digits order before other characters, and strings that are equal under those
 * rules ("v2" vs "v02", "V3" vs "v3") fall back to plain lexicographic order so the result is
 * deterministic. Locale-independent on purpose: no Collator, so the order does not depend on the
 * server's default locale.
 */
private fun compareNaturalOrder(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val aDigit = a[i].isDigit()
        val bDigit = b[j].isDigit()
        if (aDigit != bDigit) return if (aDigit) -1 else 1
        if (aDigit) {
            val aRun = a.digitRun(i)
            val bRun = b.digitRun(j)
            val aValue = aRun.trimStart('0')
            val bValue = bRun.trimStart('0')
            // A number with more digits (after leading zeros) is bigger; equal-length numbers
            // compare lexicographically.
            val byValue = aValue.length.compareTo(bValue.length).takeIf { it != 0 } ?: aValue.compareTo(bValue)
            if (byValue != 0) return byValue
            i += aRun.length
            j += bRun.length
        } else {
            val byCaseInsensitive = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
            if (byCaseInsensitive != 0) return byCaseInsensitive
            i++
            j++
        }
    }
    return when {
        i < a.length -> 1 // a has a remainder: the longer string sorts last
        j < b.length -> -1
        else -> a.compareTo(b) // fold-equal (e.g. "v02" vs "v2"): keep a deterministic order
    }
}

/** The run of digits starting at [start] (at least one character, as callers check first). */
private fun String.digitRun(start: Int): String {
    var end = start
    while (end < length && this[end].isDigit()) end++
    return substring(start, end)
}