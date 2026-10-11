package application.auth

/**
 * Why authenticating a caller (a browser user or the collector) can fail, in a form the HTTP layer
 * turns into status codes and error messages.
 */
enum class AuthenticationIssue {

    /** The token is valid and the user is present in the IAM of the portal's project. */
    NONE,

    /** The token is missing, malformed, expired, or was issued for another audience. */
    INVALID_TOKEN,

    /**
     * The token is valid, but the user is not present in the IAM of the portal's project — not
     * directly and not through any group listed there.
     */
    NOT_PERMITTED,

    /**
     * The portal cannot run the IAM membership check itself: its service account lacks the Cloud
     * Asset permission (`cloudasset.assets.analyzeIamPolicy`, e.g. via `roles/cloudasset.viewer`),
     * or the Cloud Asset API is not enabled in the portal's project. Fixable by an admin.
     */
    PORTAL_IAM_CONFIG,

    /** A portal-side problem the user cannot fix — needs developer/admin attention. */
    PORTAL_ISSUE,
}
