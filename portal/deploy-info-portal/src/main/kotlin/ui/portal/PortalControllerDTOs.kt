package ui.portal

import application.tree.GetDeployTreeUseCase
import com.fasterxml.jackson.annotation.JsonProperty
import domain.ContentFile
import domain.DeployContent
import domain.GitContent

/** Response of `GET /portal/tree` (see `documentation/api.md`): the navigator tree, group level. */
data class GroupDTO(
    val name: String,
    val projects: List<ProjectDTO>,
) {
    constructor(group: GetDeployTreeUseCase.GroupEntry) : this(
        name = group.name,
        projects = group.projects.map { ProjectDTO(it) },
    )
}

data class ProjectDTO(
    val projectId: String,
    val services: List<ServiceDTO>,
) {
    constructor(project: GetDeployTreeUseCase.ProjectEntry) : this(
        projectId = project.projectId,
        services = project.services.map { ServiceDTO(it) },
    )
}

data class ServiceDTO(
    val name: String,
    val type: String,
    val versions: List<VersionDTO>,
) {
    constructor(service: GetDeployTreeUseCase.ServiceEntry) : this(
        name = service.name,
        type = service.type.name,
        versions = service.versions.map { VersionDTO(it) },
    )
}

/** A selectable version node; the fields the file-viewer header needs ride along with the identity. */
data class VersionDTO(
    /** The deploy identity key: `<project>_<TYPE>_<location|->_<service>_<version>`. */
    val id: String,
    val name: String,
    val type: String,
    val location: String?,
    val url: String?,
    val author: String,
    /** Epoch millis of the collection timestamp. */
    val timestamp: Long,
    /** The upload folder to pass to `GET /portal/deploy/content`. */
    val storageFolder: String,
) {
    constructor(version: GetDeployTreeUseCase.VersionEntry) : this(
        id = version.id,
        name = version.name,
        type = version.type.name,
        location = version.location,
        url = version.url,
        author = version.author,
        timestamp = version.timestamp.toEpochMilli(),
        storageFolder = version.storageFolder,
    )
}

/** Response of `GET /portal/deploy/content` (see `documentation/api.md`): the collected files. */
data class DeployContentDTO(
    val git: GitDTO?,
    val extras: List<ContentFileDTO>,
) {
    constructor(content: DeployContent) : this(
        git = content.git?.let { GitDTO(it) },
        extras = content.extras.map { ContentFileDTO(it) },
    )

    data class GitDTO(
        @JsonProperty("gitlog")
        val gitLog: String?,
        @JsonProperty("gitstatus")
        val gitStatus: String?,
        val changes: List<ContentFileDTO>,
    ) {
        constructor(git: GitContent) : this(
            gitLog = git.gitLog,
            gitStatus = git.gitStatus,
            changes = git.changes.map { ContentFileDTO(it) },
        )
    }
}

data class ContentFileDTO(
    val filepath: String,
    val content: String,
) {
    constructor(file: ContentFile) : this(
        filepath = file.filepath,
        content = file.content,
    )
}
