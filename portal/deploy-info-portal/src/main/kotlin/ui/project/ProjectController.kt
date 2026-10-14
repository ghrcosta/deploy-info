package ui.project

import application.PortalIdentityResolver
import application.project.AddProjectUseCase
import application.project.DeleteProjectUseCase
import application.project.EditProjectUseCase
import application.project.GetAllProjectsUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("project")
class ProjectController(
    private val getAllProjectsUseCase: GetAllProjectsUseCase,
    private val addProjectUseCase: AddProjectUseCase,
    private val editProjectUseCase: EditProjectUseCase,
    private val deleteProjectUseCase: DeleteProjectUseCase,
    private val portalIdentityResolver: PortalIdentityResolver,
) {

    @GetMapping("/all")
    fun getProjects(): ResponseEntity<List<ProjectDTO>> =
        getAllProjectsUseCase.execute().toResponse()

    @PostMapping
    fun addProject(@RequestBody project: ProjectDTO): ResponseEntity<AddProjectResultDTO> =
        addProjectUseCase.execute(project.toModel()).toResponse(portalIdentityResolver::resolve)

    @PutMapping
    fun editProject(@RequestBody project: ProjectDTO): ResponseEntity<EditProjectResultDTO> =
        editProjectUseCase.execute(project.toModel()).toResponse(portalIdentityResolver::resolve)

    @DeleteMapping("/{projectId}")
    fun deleteProject(@PathVariable("projectId") projectId: String): ResponseEntity<List<ProjectDTO>> =
        deleteProjectUseCase.execute(projectId).toResponse()
}
