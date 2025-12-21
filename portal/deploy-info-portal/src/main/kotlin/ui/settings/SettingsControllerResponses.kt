package ui.settings

import application.settings.AddProjectUseCase
import application.settings.DeleteProjectUseCase
import application.settings.EditProjectUseCase
import application.settings.GetAllProjectsUseCase
import org.springframework.http.ResponseEntity

fun GetAllProjectsUseCase.Output.toResponse(): ResponseEntity<List<ProjectDTO>> =
    if (projectsInRepository.isEmpty()) {
        ResponseEntity.noContent().build()
    } else {
        ResponseEntity.ok(projectsInRepository.map { ProjectDTO(it) })
    }

fun AddProjectUseCase.Output.toResponse(): ResponseEntity<AddProjectResultDTO> =
    if (issuesFound()) {
        ResponseEntity.ok(
            AddProjectResultDTO(
                issues = AddProjectResultDTO.IssuesDTO(
                    issueNameConflict = issueNameConflict,
                    issueServiceAccountError = issueServiceAccountError,
                )
            )
        )
    } else {
        ResponseEntity.ok(
            AddProjectResultDTO(
                projects = projectsInRepository.map { ProjectDTO(it) },
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
    } else {
        ResponseEntity.ok(
            EditProjectResultDTO(
                projects = projectsInRepository.map { ProjectDTO(it) },
            )
        )
    }

fun DeleteProjectUseCase.Output.toResponse(): ResponseEntity<List<ProjectDTO>> =
    ResponseEntity.ok(projectsInRepository.map { ProjectDTO(it) })