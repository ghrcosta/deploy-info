package ui.settings

import application.PortalIdentityResolver
import application.settings.AddProjectUseCase
import application.settings.DeleteProjectUseCase
import application.settings.EditProjectUseCase
import application.settings.GetAllProjectsUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("settings")
class SettingsController(
    private val getAllProjectsUseCase: GetAllProjectsUseCase,
    private val addProjectUseCase: AddProjectUseCase,
    private val editProjectUseCase: EditProjectUseCase,
    private val deleteProjectUseCase: DeleteProjectUseCase,
    private val portalIdentityResolver: PortalIdentityResolver,
) {

    @GetMapping("/projects")
    fun getProjects(): ResponseEntity<List<ProjectDTO>> =
        getAllProjectsUseCase.execute().toResponse()

    @PostMapping("/project")
    fun addProject(@RequestBody project: ProjectDTO): ResponseEntity<AddProjectResultDTO> =
        addProjectUseCase.execute(project.toModel()).toResponse(portalIdentityResolver::resolve)

    @PutMapping("/project")
    fun editProject(@RequestBody project: ProjectDTO): ResponseEntity<EditProjectResultDTO> =
        editProjectUseCase.execute(project.toModel()).toResponse(portalIdentityResolver::resolve)

    @DeleteMapping("/project/{projectName}")
    fun deleteProject(@PathVariable("projectName") projectName: String): ResponseEntity<List<ProjectDTO>> =
        deleteProjectUseCase.execute(projectName).toResponse()
}
