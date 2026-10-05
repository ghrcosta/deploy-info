package ui.portal

import application.cleanup.RunCleanupIfDueUseCase
import application.content.GetDeployContentUseCase
import application.tree.GetDeployTreeUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Main-screen contracts (see `docs/api.md`): the navigator tree and the collected file content of
 * an upload folder.
 */
@RestController
@RequestMapping("portal")
class PortalController(
    private val getDeployTreeUseCase: GetDeployTreeUseCase,
    private val getDeployContentUseCase: GetDeployContentUseCase,
    private val runCleanupIfDueUseCase: RunCleanupIfDueUseCase,
) {

    /**
     * The navigator tree (group > project > service > version). The on-request cleanup trigger
     * runs first when it is due, so the tree never serves links that a due sweep would have removed
     * first; this makes the response slower by the sweep's duration roughly once per interval.
     */
    @GetMapping("/tree")
    fun getTree(): ResponseEntity<List<GroupDTO>> {
        runCleanupIfDueUseCase.executeIfDue()
        return ResponseEntity.ok(getDeployTreeUseCase.execute().map { GroupDTO(it) })
    }

    /**
     * The collected files (GIT section and extras) of one upload folder, as carried by the tree's
     * version nodes — so this endpoint touches Cloud Storage only, never Datastore.
     */
    @GetMapping("/deploy/content")
    fun getContent(@RequestParam("folder") storageFolder: String): ResponseEntity<Any> =
        when (val output = getDeployContentUseCase.execute(GetDeployContentUseCase.Input(storageFolder))) {
            is GetDeployContentUseCase.Output.Content ->
                ResponseEntity.ok(DeployContentDTO(output.deployContent))
            is GetDeployContentUseCase.Output.FolderNotFound ->
                ResponseEntity.status(404).build()
            is GetDeployContentUseCase.Output.StorageError ->
                ResponseEntity.status(502).build()
        }
}
