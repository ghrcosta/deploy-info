package ui.settings

import application.ProjectRepository
import application.settings.AddProjectUseCase
import application.settings.DeleteProjectUseCase
import application.settings.EditProjectUseCase
import application.settings.GetAllProjectsUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("settings")
class SettingsController(
    projectRepository: ProjectRepository,
    private val getAllProjectsUseCase: GetAllProjectsUseCase = GetAllProjectsUseCase(projectRepository),
    private val addProjectUseCase: AddProjectUseCase = AddProjectUseCase(projectRepository),
    private val editProjectUseCase: EditProjectUseCase = EditProjectUseCase(projectRepository),
    private val deleteProjectUseCase: DeleteProjectUseCase = DeleteProjectUseCase(projectRepository),
) {

    @GetMapping("/projects")
    fun getProjects(): ResponseEntity<List<ProjectDTO>> =
        getAllProjectsUseCase.execute().toResponse()

    @PostMapping("/project")
    fun addProject(@RequestBody project: ProjectDTO): ResponseEntity<AddProjectResultDTO> =
        addProjectUseCase.execute(project.toModel()).toResponse()

    @PutMapping("/project")
    fun editProject(@RequestBody project: ProjectDTO): ResponseEntity<EditProjectResultDTO> =
        editProjectUseCase.execute(project.toModel()).toResponse()

    @DeleteMapping("/project/{projectName}")
    fun deleteProject(@PathVariable("projectName") projectName: String): ResponseEntity<List<ProjectDTO>> =
        deleteProjectUseCase.execute(projectName).toResponse()
}