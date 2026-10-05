package ui.trigger

import domain.DeployType

/** Request body of `POST /trigger/handleNewDirectory` (see `documentation/api.md`). */
data class TriggerRequest(
    /** The collector's output directory name, `<user>_<deployType>_<epochMillis>`. */
    val directoryName: String,
    /** Candidate GCP projects the code may have been deployed to. */
    val projects: List<String>,
    /** `GAE` or `RUN`. */
    val deployType: DeployType,
    /** The gcloud account that ran the deploy and the collection. */
    val userEmail: String,
)

/** Response body of `POST /trigger/handleNewDirectory` (see `documentation/api.md`). */
data class TriggerResponse(
    /** `created`, `already-linked`, `pending` or `unknown-project`. */
    val status: String,
    val project: String? = null,
    val service: String? = null,
    val version: String? = null,
) {
    companion object {
        fun created(project: String, service: String, version: String) =
            TriggerResponse("created", project, service, version)

        fun alreadyLinked(project: String, service: String, version: String) =
            TriggerResponse("already-linked", project, service, version)

        val PENDING = TriggerResponse("pending")
        val UNKNOWN_PROJECT = TriggerResponse("unknown-project")
    }
}
