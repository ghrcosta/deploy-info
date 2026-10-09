package domain

/**
 * Thrown when the GCP App Engine or Cloud Run listing API cannot be reached or returns an
 * unexpected response.
 *
 * [statusCode] carries the gax status code of the failed API call (e.g. `PERMISSION_DENIED`); it is
 * null when the failure was detected locally instead of returned by the API (e.g. a malformed
 * resource without a creation time).
 */
class GcpListingException(
    message: String,
    cause: Throwable? = null,
    val statusCode: String? = null,
) : RuntimeException(message, cause) {

    val isPermissionDenied: Boolean
        get() = statusCode == "PERMISSION_DENIED" || statusCode == "403"
}
