package ui.portal

import application.cleanup.RunCleanupIfDueUseCase
import application.content.GetDeploymentContentUseCase
import application.tree.GetDeploymentTreeUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("portal")
class PortalController(
    private val getDeploymentTreeUseCase: GetDeploymentTreeUseCase,
    private val getDeploymentContentUseCase: GetDeploymentContentUseCase,
    private val runCleanupIfDueUseCase: RunCleanupIfDueUseCase,
) {

    @GetMapping("/tree")
    fun getTree(): ResponseEntity<List<GroupDTO>> {
        runCleanupIfDueUseCase.executeIfDue()
        return ResponseEntity.ok(getDeploymentTreeUseCase.execute().map { GroupDTO(it) })
    }

    @GetMapping("/deploy/content")
    fun getContent(@RequestParam("folder") storageFolder: String): ResponseEntity<Any> =
        when (val output = getDeploymentContentUseCase.execute(GetDeploymentContentUseCase.Input(storageFolder))) {
            is GetDeploymentContentUseCase.Output.Content ->
                ResponseEntity.ok()
                    .header("Cache-Control", "private, max-age=604800, immutable")
                    .body(DeploymentContentDTO(output.deploymentContent))
            is GetDeploymentContentUseCase.Output.FolderNotFound ->
                ResponseEntity.status(404).build()
            is GetDeploymentContentUseCase.Output.StorageError ->
                ResponseEntity.status(502).build()
        }
}
