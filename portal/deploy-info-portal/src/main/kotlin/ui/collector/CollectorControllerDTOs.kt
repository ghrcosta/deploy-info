package ui.collector

import domain.DeployType

/** Request body of `POST /collector/handleNewDirectory` (see `documentation/api.md`). */
data class CollectorRequest(
    /** The collector's output directory name, `<user>_<deployType>_<epochMillis>`. */
    val directoryName: String,
    /** Candidate GCP projects the code may have been deployed to. */
    val projects: List<String>,
    /** `GAE` or `RUN`. */
    val deployType: DeployType,
    /** The gcloud account that ran the deploy and the collection. */
    val userEmail: String,
)

/** Response body of `POST /collector/handleNewDirectory` (see `documentation/api.md`). */
data class CollectorResponse(
    /** `created`, `already-linked`, `pending` or `unknown-project`. */
    val status: String,
    val project: String? = null,
    val service: String? = null,
    val version: String? = null,
) {
    companion object {
        fun created(project: String, service: String, version: String) =
            CollectorResponse("created", project, service, version)

        fun alreadyLinked(project: String, service: String, version: String) =
            CollectorResponse("already-linked", project, service, version)

        val PENDING = CollectorResponse("pending")
        val UNKNOWN_PROJECT = CollectorResponse("unknown-project")
    }
}

/** Response body of `GET /collector/bucket` (see `documentation/api.md`). */
data class BucketResponse(
    /** The Cloud Storage bucket the collector uploads go to. */
    val bucket: String,
)
