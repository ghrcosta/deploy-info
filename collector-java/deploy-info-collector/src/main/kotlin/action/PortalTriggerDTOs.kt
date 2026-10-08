package io.github.ghrcosta.action

/** Request body of `POST /collector/handleNewDirectory` (see `documentation/api.md`). */
data class TriggerRequest(
    /** The collector's output directory name, `<user>_<deployType>_<epochMillis>`. */
    val directoryName: String,
    /** Candidate GCP projects the code may have been deployed to. */
    val projects: List<String>,
    /** `GAE` or `RUN`. */
    val deployType: String,
    /** The gcloud account that performed the deploy and the collection. */
    val userEmail: String,
)

/** Response body of `POST /collector/handleNewDirectory` (see `documentation/api.md`). */
data class TriggerResponse(
    /** `created`, `already-linked`, `pending` or `unknown-project`. */
    val status: String,
    /** Present on `created` and `already-linked`: the deploy the upload was linked to. */
    val project: String? = null,
    val service: String? = null,
    val version: String? = null,
) {
    companion object {
        const val STATUS_CREATED = "created"
        const val STATUS_ALREADY_LINKED = "already-linked"
        const val STATUS_PENDING = "pending"
    }
}
