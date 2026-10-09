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

/**
 * [portalServiceAccount] is resolved lazily (the portal's own service account email), so it is only
 * looked up when the response actually reports a service-account issue and needs it for the
 * remediation instructions shown to the user.
 */
fun AddProjectUseCase.Output.toResponse(portalServiceAccount: () -> String?): ResponseEntity<AddProjectResultDTO> =
    if (issuesFound()) {
        ResponseEntity.ok(
            AddProjectResultDTO(
                issues = AddProjectResultDTO.IssuesDTO(
                    issueNameConflict = issueNameConflict,
                    issueServiceAccountError = issueServiceAccountError,
                    serviceAccountIssue = serviceAccountIssue.name.takeIf { issueServiceAccountError },
                    portalServiceAccount = portalServiceAccount().takeIf { issueServiceAccountError },
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

fun EditProjectUseCase.Output.toResponse(portalServiceAccount: () -> String?): ResponseEntity<EditProjectResultDTO> =
    if (issuesFound()) {
        ResponseEntity.ok(
            EditProjectResultDTO(
                issues = EditProjectResultDTO.IssuesDTO(
                    issueProjectNotFound = issueProjectNotFound,
                    issueServiceAccountError = issueServiceAccountError,
                    serviceAccountIssue = serviceAccountIssue.name.takeIf { issueServiceAccountError },
                    portalServiceAccount = portalServiceAccount().takeIf { issueServiceAccountError },
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
