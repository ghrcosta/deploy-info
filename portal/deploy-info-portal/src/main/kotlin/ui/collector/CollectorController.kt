package ui.collector

import application.collector.CreateDeployLinkUseCase
import application.collector.GetStorageBucketUseCase
import domain.InvalidDirectoryNameException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Collector contract: the collector calls these endpoints (see `documentation/api.md`).
 */
@RestController
@RequestMapping("collector")
class CollectorController(
    private val createDeployLinkUseCase: CreateDeployLinkUseCase,
    private val getStorageBucketUseCase: GetStorageBucketUseCase,
) {

    @PostMapping("/handleNewDirectory")
    fun handleNewDirectory(@RequestBody request: CollectorRequest): ResponseEntity<CollectorResponse> =
        when (val output = createDeployLinkUseCase.execute(request.toUseCaseInput())) {
            is CreateDeployLinkUseCase.Output.Created ->
                ResponseEntity.ok(
                    CollectorResponse.created(
                        output.deployLink.projectId, output.deployLink.serviceId, output.deployLink.versionId,
                    )
                )
            is CreateDeployLinkUseCase.Output.AlreadyLinked ->
                ResponseEntity.ok(
                    CollectorResponse.alreadyLinked(
                        output.existing.projectId, output.existing.serviceId, output.existing.versionId,
                    )
                )
            is CreateDeployLinkUseCase.Output.NotLinked -> ResponseEntity.accepted().body(CollectorResponse.PENDING)
            is CreateDeployLinkUseCase.Output.UnknownProject ->
                ResponseEntity.unprocessableContent().body(CollectorResponse.UNKNOWN_PROJECT)
        }

    /** Bucket lookup: tells the collector which Cloud Storage bucket its uploads go to. */
    @GetMapping("/bucket")
    fun bucket(): ResponseEntity<BucketResponse> =
        when (val output = getStorageBucketUseCase.execute()) {
            is GetStorageBucketUseCase.Output.Bucket -> ResponseEntity.ok(BucketResponse(output.name))
            is GetStorageBucketUseCase.Output.NotConfigured -> ResponseEntity.internalServerError().build()
        }

    /** The directory name does not follow the collector convention — a caller error, not a server error. */
    @ExceptionHandler(InvalidDirectoryNameException::class)
    fun onInvalidDirectoryName(): ResponseEntity<Void> = ResponseEntity.badRequest().build()

    private fun CollectorRequest.toUseCaseInput() = CreateDeployLinkUseCase.Input(
        directoryName = directoryName,
        projects = projects,
        deployType = deployType,
        userEmail = userEmail,
    )
}

