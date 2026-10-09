package infrastructure.gcp

import application.GcpAppEngineLister
import application.GcpCloudRunLister
import application.ServiceAccountIssue
import application.ServiceAccountValidator
import domain.CredentialsErrorCategory
import domain.CredentialsException
import domain.GcpListingException
import domain.Project

/**
 * Validates a project's service account by exercising the same permission chain the deploy listing
 * uses, in order:
 *
 * 1. **Impersonation** — [CredentialsProvider.credentialsFor] obtains credentials for the target
 *    service account via the IAM Credentials API, which requires
 *    `roles/iam.serviceAccountTokenCreator` on it. Because `credentialsFor()` forces a token
 *    refresh, the IAM token request is genuinely made and a missing permission surfaces as a
 *    user-fixable [CredentialsException] (category `SERVICE_ACCOUNT_MISCONFIGURATION`).
 * 2. **Listing** — lists the App Engine and Cloud Run deploys with the impersonated credentials,
 *    which requires the App Engine Viewer / Cloud Run Viewer roles on the target project. A
 *    permission denial (`403` / `PERMISSION_DENIED`) surfaces as [GcpListingException] with that
 *    status code.
 *
 * Like the credentials themselves, validation runs lazily per add/edit request — never at startup.
 */
class GcpServiceAccountValidator(
    private val credentialsProvider: CredentialsProvider,
    private val appEngineLister: GcpAppEngineLister,
    private val cloudRunLister: GcpCloudRunLister,
) : ServiceAccountValidator {

    override fun validate(project: Project): ServiceAccountIssue {
        val impersonationIssue = try {
            credentialsProvider.credentialsFor(project)
            null
        } catch (e: CredentialsException) {
            when (e.category) {
                CredentialsErrorCategory.SERVICE_ACCOUNT_MISCONFIGURATION ->
                    ServiceAccountIssue.MISSING_IMPERSONATION_PERMISSION
                CredentialsErrorCategory.PORTAL_ISSUE ->
                    ServiceAccountIssue.PORTAL_ISSUE
            }
        } catch (_: Exception) {
            ServiceAccountIssue.PORTAL_ISSUE
        }
        if (impersonationIssue != null) return impersonationIssue

        return try {
            appEngineLister.listAllDeploys(project)
            cloudRunLister.listAllDeploys(project)
            ServiceAccountIssue.NONE
        } catch (e: GcpListingException) {
            if (e.isPermissionDenied) ServiceAccountIssue.MISSING_LISTING_PERMISSION else ServiceAccountIssue.PORTAL_ISSUE
        } catch (_: CredentialsException) {
            ServiceAccountIssue.PORTAL_ISSUE
        }
    }
}
