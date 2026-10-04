package domain

/**
 * Why an access token could not be generated for a project's service account.
 */
enum class AccessTokenErrorCategory {

    /**
     * Potentially fixable by the user who configured the project: wrong service account email,
     * missing permission/role on the service account, or the service account not existing.
     */
    SERVICE_ACCOUNT_MISCONFIGURATION,

    /**
     * A portal-side problem that the user cannot fix: missing portal credentials, network
     * failure, quota, or an unexpected response — needs developer/admin attention.
     */
    PORTAL_ISSUE,
}

/** Thrown when an access token could not be generated for a project's service account (impersonation). */
class AccessTokenException(
    message: String,
    val category: AccessTokenErrorCategory,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
