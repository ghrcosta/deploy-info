package application

import domain.Project

/**
 * Why service-account validation of a project can fail, in a form the UI can turn into a precise
 * error message with remediation steps.
 */
enum class ServiceAccountIssue {

    /** The portal can impersonate the service account and list the project's deploys with it. */
    NONE,

    /**
     * The portal cannot impersonate the service account — it lacks
     * `roles/iam.serviceAccountTokenCreator` on the target service account. Fixable by the user.
     */
    MISSING_IMPERSONATION_PERMISSION,

    /**
     * The service account lacks the App Engine Viewer / Cloud Run Viewer roles required to list the
     * project's deploys. Fixable by the user.
     */
    MISSING_LISTING_PERMISSION,

    /**
     * The listing API cannot resolve the project — the id does not exist, cannot be reached, or is
     * not a valid project id. Fixable by the user: enter the exact GCP project ID (e.g. from
     * `gcloud projects list`), not the console display name.
     */
    PROJECT_NOT_FOUND,

    /** A portal-side problem the user cannot fix — needs developer/admin attention. */
    PORTAL_ISSUE,
}

interface ServiceAccountValidator {

    /**
     * Checks that the portal can impersonate the project's service account and list its GAE and
     * Cloud Run deploys with it, hitting the real GCP APIs (see `GcpServiceAccountValidator`).
     */
    fun validate(project: Project): ServiceAccountIssue
}
