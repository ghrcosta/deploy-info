package ui.collector

import application.collector.CreateDeploymentLinkUseCase
import application.collector.GetStorageBucketUseCase
import domain.InvalidDirectoryNameException
import infrastructure.config.DeployInfoProperties
import infrastructure.security.AuthenticationFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Collector contract: the collector calls these endpoints (see `documentation/api.md`).
 */
@RestController
@RequestMapping("collector")
class CollectorController(
    private val createDeploymentLinkUseCase: CreateDeploymentLinkUseCase,
    private val getStorageBucketUseCase: GetStorageBucketUseCase,
    private val properties: DeployInfoProperties,
) {

    @PostMapping("/handleNewDirectory")
    fun handleNewDirectory(
        @RequestBody request: CollectorRequest,
        httpServletRequest: HttpServletRequest,
    ): ResponseEntity<Any> {
        val tokenEmail = httpServletRequest.getAttribute(AuthenticationFilter.AUTHENTICATED_EMAIL_ATTRIBUTE) as? String
        if (tokenEmail != null && properties.auth.collectorRequireEmailMatch &&
            !tokenEmail.equals(request.userEmail, ignoreCase = true)
        ) {
            return ResponseEntity.status(403).body(
                CollectorAuthenticationError(
                    error = "email-mismatch",
                    tokenEmail = tokenEmail,
                    requestUserEmail = request.userEmail,
                )
            )
        }
        return when (val output = createDeploymentLinkUseCase.execute(request.toUseCaseInput())) {
            is CreateDeploymentLinkUseCase.Output.Created ->
                ResponseEntity.ok(
                    CollectorResponse.created(
                        output.deploymentLink.projectId, output.deploymentLink.serviceId, output.deploymentLink.versionId,
                    )
                )
            is CreateDeploymentLinkUseCase.Output.AlreadyLinked ->
                ResponseEntity.ok(
                    CollectorResponse.alreadyLinked(
                        output.existing.projectId, output.existing.serviceId, output.existing.versionId,
                    )
                )
            is CreateDeploymentLinkUseCase.Output.NotLinked -> ResponseEntity.accepted().body(CollectorResponse.PENDING)
            is CreateDeploymentLinkUseCase.Output.UnknownProject ->
                ResponseEntity.unprocessableContent().body(CollectorResponse.UNKNOWN_PROJECT)
        }
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

    private fun CollectorRequest.toUseCaseInput() = CreateDeploymentLinkUseCase.Input(
        directoryName = directoryName,
        projects = projects,
        deploymentType = deployType,
        userEmail = userEmail,
    )
}

