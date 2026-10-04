package domain

/** Thrown when the GCP App Engine listing API cannot be reached or returns an unexpected response. */
class GcpListingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
