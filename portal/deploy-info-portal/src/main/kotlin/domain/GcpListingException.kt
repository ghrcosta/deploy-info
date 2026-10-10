package domain

/**
 * Thrown when the GCP App Engine or Cloud Run listing API cannot be reached or returns an
 * unexpected response.
 *
 * [statusCode] carries the gax status code of the failed API call (e.g. `PERMISSION_DENIED`); it is
 * null when the failure was detected locally instead of returned by the API (e.g. a malformed
 * resource without a creation time).
 *
 * [reason] carries the reason of the server's error body when it could be extracted from the
 * exception cause chain (e.g. `SERVICE_DISABLED` for a not-enabled listing API).
 */
class GcpListingException(
    message: String,
    cause: Throwable? = null,
    val statusCode: String? = null,
    val reason: String? = null,
) : RuntimeException(message, cause) {

    val isPermissionDenied: Boolean
        get() = statusCode == "PERMISSION_DENIED" || statusCode == "403"

    /**
     * True when the listing API cannot resolve the project at all — gax `NOT_FOUND`, or
     * `INVALID_ARGUMENT` for a malformed project id. Kept distinct from [isPermissionDenied]
     * because the two need different remediation: a wrong/unreachable project id means the entered
     * name must be rechecked, not that IAM roles are missing. (The App Engine Admin API itself
     * answers a wrong/unreachable project id with a plain `PERMISSION_DENIED` — "or it may not
     * exist" — so that case stays indistinguishable from a real denial; see
     * `documentation/settings-projects.md`.)
     */
    val isProjectNotFound: Boolean
        get() = statusCode == "NOT_FOUND" || statusCode == "404" ||
            statusCode == "INVALID_ARGUMENT" || statusCode == "400"

    companion object {

        /**
         * Joins the messages of the whole cause chain into one string, the way
         * `GcpCredentialsProvider.categorize` matches them — used by the logging of the listing
         * clients, because the client library surfaces the server's error text (denied permission,
         * resource, remediation link) only somewhere along the cause chain.
         */
        fun causeChainMessage(throwable: Throwable): String =
            generateSequence(throwable as Throwable?) { it.cause }.joinToString(" ") { it.message ?: "" }

        /**
         * Extracts the reason of the server's error body out of the cause-chain message.
         */
        fun errorInfoReason(throwable: Throwable): String? {
            val message = causeChainMessage(throwable)
            errorInfoReasonRegex.find(message)?.let { return it.groupValues[1] }
            val lowerCaseMessage = message.lowercase()
            return if (DISABLED_API_MARKERS.any { it in lowerCaseMessage }) "SERVICE_DISABLED" else null
        }

        private val errorInfoReasonRegex = Regex("\"reason\"\\s*:\\s*\"([A-Z_]+)\"")

        private val DISABLED_API_MARKERS = listOf("has not been used in project", "or it is disabled")
    }
}
