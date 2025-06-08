package ui.settings

import application.settings.GetAllProjectsUseCase
import org.springframework.http.ResponseEntity

fun GetAllProjectsUseCase.Output.toResponse(): ResponseEntity<List<ProjectDTO>> =
    if (projects.isEmpty()) {
        ResponseEntity.noContent().build()
    } else {
        ResponseEntity.ok(projects.map { ProjectDTO(it) })
    }