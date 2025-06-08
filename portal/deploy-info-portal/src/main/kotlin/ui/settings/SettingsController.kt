package ui.settings

import application.ProjectRepository
import application.settings.AddProjectUseCase
import application.settings.GetAllProjectsUseCase
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("settings")
class SettingsController(
    projectRepository: ProjectRepository,
    private val getAllProjectsUseCase: GetAllProjectsUseCase = GetAllProjectsUseCase(projectRepository),
    private val addProjectUseCase: AddProjectUseCase = AddProjectUseCase(projectRepository),
) {

    @GetMapping("/projects")
    fun getProjects(): ResponseEntity<List<ProjectDTO>> {
        return getAllProjectsUseCase.execute().toResponse()
    }

    @PostMapping("/project")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun addProject(@RequestBody project: ProjectDTO) {
        addProjectUseCase.execute(project.toModel())
    }

    @PutMapping("/project")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun editProject(@RequestBody project: ProjectDTO) {

    }

    @DeleteMapping("/project")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProject() {

    }
}