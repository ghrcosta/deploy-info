package application

/**
 * Resolves the email of the portal's own service account, so the UI can name the principal that
 * must be granted the IAM roles in its permission remediation instructions.
 */
fun interface PortalIdentityResolver {

    /**
     * Returns the portal's own service account email, or null when it cannot be determined (e.g.
     * the portal runs with user credentials instead of a service account).
     */
    fun resolve(): String?
}
