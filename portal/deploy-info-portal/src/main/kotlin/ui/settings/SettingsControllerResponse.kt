package ui.settings

import application.settings.AddProjectUseCase
import application.settings.DeleteProjectUseCase
import application.settings.EditProjectUseCase
import application.settings.GetAllProjectsUseCase
import org.springframework.http.ResponseEntity

fun GetAllProjectsUseCase.Output.toResponse(): ResponseEntity<List<ProjectDTO>> =
    if (projectsInDatabase.isEmpty()) {
        ResponseEntity.noContent().build()
    } else {
        ResponseEntity.ok(projectsInDatabase.map { ProjectDTO(it) })
    }

fun AddProjectUseCase.Output.toResponse(): ResponseEntity<AddProjectResultDTO> =
    if (issuesFound()) {
        ResponseEntity.ok(
            AddProjectResultDTO(
                issues = AddProjectResultDTO.IssuesDTO(
                    hasNameConflict = issueHasNameConflict,
                )
            )
        )
    } else if (projectsInDatabase.isNullOrEmpty()) {
        ResponseEntity.noContent().build()
    } else {
        ResponseEntity.ok(
            AddProjectResultDTO(
                projects = projectsInDatabase.map { ProjectDTO(it) },
            )
        )
    }

fun EditProjectUseCase.Output.toResponse(): ResponseEntity<EditProjectResultDTO> =
    if (issuesFound()) {
        ResponseEntity.ok(
            EditProjectResultDTO(
                issues = EditProjectResultDTO.IssuesDTO(
                    issueProjectNotFound = issueProjectNotFound,
                    issueServiceAccountError = issueServiceAccountError,
                )
            )
        )
    } else if (projectsInDatabase.isNullOrEmpty()) {
        ResponseEntity.noContent().build()
    } else {
        ResponseEntity.ok(
            EditProjectResultDTO(
                projects = projectsInDatabase.map { ProjectDTO(it) },
            )
        )
    }

fun DeleteProjectUseCase.Output.toResponse(): ResponseEntity<List<ProjectDTO>> =
    if (projectsInDatabase.isEmpty()) {
        ResponseEntity.noContent().build()
    } else {
        ResponseEntity.ok(projectsInDatabase.map { ProjectDTO(it) })
    }