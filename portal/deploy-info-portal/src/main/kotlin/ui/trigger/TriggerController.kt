package ui.trigger

import application.linking.CreateDeployLinkUseCase
import domain.InvalidDirectoryNameException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Trigger contract: the collector calls this after uploading its output directory to Cloud Storage
 * (see `docs/api.md`).
 */
@RestController
@RequestMapping("trigger")
class TriggerController(
    private val createDeployLinkUseCase: CreateDeployLinkUseCase,
) {

    @PostMapping("/handleNewDirectory")
    fun handleNewDirectory(@RequestBody request: TriggerRequest): ResponseEntity<TriggerResponse> =
        when (val output = createDeployLinkUseCase.execute(request.toUseCaseInput())) {
            is CreateDeployLinkUseCase.Output.Created ->
                ResponseEntity.ok(
                    TriggerResponse.created(
                        output.deployLink.projectName, output.deployLink.serviceId, output.deployLink.versionId,
                    )
                )
            is CreateDeployLinkUseCase.Output.AlreadyLinked ->
                ResponseEntity.ok(
                    TriggerResponse.alreadyLinked(
                        output.existing.projectName, output.existing.serviceId, output.existing.versionId,
                    )
                )
            is CreateDeployLinkUseCase.Output.NotLinked -> ResponseEntity.accepted().body(TriggerResponse.PENDING)
            is CreateDeployLinkUseCase.Output.UnknownProject ->
                ResponseEntity.unprocessableContent().body(TriggerResponse.UNKNOWN_PROJECT)
        }

    /** The directory name does not follow the collector convention — a caller error, not a server error. */
    @ExceptionHandler(InvalidDirectoryNameException::class)
    fun onInvalidDirectoryName(): ResponseEntity<Void> = ResponseEntity.badRequest().build()

    private fun TriggerRequest.toUseCaseInput() = CreateDeployLinkUseCase.Input(
        directoryName = directoryName,
        projects = projects,
        deployType = deployType,
        userEmail = userEmail,
    )
}

