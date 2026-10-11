package application.auth

/**
 * Checks that an authenticated user is present in the IAM of the project the portal is deployed in —
 * directly, or through a group listed there (the infrastructure implementation expands group
 * membership, see `infrastructure/gcp/GcpIamMembershipChecker`).
 */
fun interface UserPermissionChecker {

    /** Returns [AuthenticationIssue.NONE] when the user may use the portal, or the issue explaining why not. */
    fun check(email: String): AuthenticationIssue
}
