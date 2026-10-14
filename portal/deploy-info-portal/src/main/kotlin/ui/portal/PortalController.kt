package ui.portal

import application.cleanup.RunCleanupIfDueUseCase
import application.content.GetDeployContentUseCase
import application.tree.GetDeployTreeUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("portal")
class PortalController(
    private val getDeployTreeUseCase: GetDeployTreeUseCase,
    private val getDeployContentUseCase: GetDeployContentUseCase,
    private val runCleanupIfDueUseCase: RunCleanupIfDueUseCase,
) {

    @GetMapping("/tree")
    fun getTree(): ResponseEntity<List<GroupDTO>> {
        runCleanupIfDueUseCase.executeIfDue()
        return ResponseEntity.ok(getDeployTreeUseCase.execute().map { GroupDTO(it) })
    }

    @GetMapping("/deploy/content")
    fun getContent(@RequestParam("folder") storageFolder: String): ResponseEntity<Any> =
        when (val output = getDeployContentUseCase.execute(GetDeployContentUseCase.Input(storageFolder))) {
            is GetDeployContentUseCase.Output.Content ->
                ResponseEntity.ok()
                    .header("Cache-Control", "private, max-age=604800, immutable")
                    .body(DeployContentDTO(output.deployContent))
            is GetDeployContentUseCase.Output.FolderNotFound ->
                ResponseEntity.status(404).build()
            is GetDeployContentUseCase.Output.StorageError ->
                ResponseEntity.status(502).build()
        }
}
