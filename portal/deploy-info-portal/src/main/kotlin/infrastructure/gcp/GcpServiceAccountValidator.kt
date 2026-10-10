package infrastructure.gcp

import application.GcpAppEngineLister
import application.GcpCloudRunLister
import application.ServiceAccountIssue
import application.ServiceAccountValidator
import domain.CredentialsErrorCategory
import domain.CredentialsException
import domain.GcpListingException
import domain.Project
import org.slf4j.LoggerFactory

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
 *    status code and maps to `MISSING_LISTING_PERMISSION` — unless the denial carries the
 *    `google.rpc.ErrorInfo` reason `SERVICE_DISABLED`, i.e. the listing API itself is not enabled
 *    in the project, which maps to `APIS_NOT_ENABLED` so a disabled API does not masquerade as
 *    missing IAM roles; a listing failure that cannot resolve the project at all (`NOT_FOUND` /
 *    `INVALID_ARGUMENT`) maps to `PROJECT_NOT_FOUND`, so a wrong project id does not masquerade as
 *    missing permissions.
 *
 * Like the credentials themselves, validation runs lazily per add/edit request — never at startup.
 */
class GcpServiceAccountValidator(
    private val credentialsProvider: CredentialsProvider,
    private val appEngineLister: GcpAppEngineLister,
    private val cloudRunLister: GcpCloudRunLister,
) : ServiceAccountValidator {

    override fun validate(project: Project): ServiceAccountIssue {
        logger.info(
            "Validating service account: project=${project.projectId}, serviceAccount=${project.serviceAccount}",
        )
        val impersonationIssue = try {
            credentialsProvider.credentialsFor(project)
            null
        } catch (e: CredentialsException) {
            logger.warn(
                "Service account validation failed during impersonation: project=${project.projectId}, " +
                    "serviceAccount=${project.serviceAccount}, " +
                    "CredentialsException causeChain=\"${GcpListingException.causeChainMessage(e)}\"",
            )
            when (e.category) {
                CredentialsErrorCategory.SERVICE_ACCOUNT_MISCONFIGURATION ->
                    ServiceAccountIssue.MISSING_IMPERSONATION_PERMISSION
                CredentialsErrorCategory.PORTAL_ISSUE ->
                    ServiceAccountIssue.PORTAL_ISSUE
            }
        } catch (e: Exception) {
            logger.warn(
                "Service account validation failed during impersonation: project=${project.projectId}, " +
                    "serviceAccount=${project.serviceAccount}, issue=PORTAL_ISSUE, " +
                    "Exception causeChain=\"${GcpListingException.causeChainMessage(e)}\"",
            )
            ServiceAccountIssue.PORTAL_ISSUE
        }
        if (impersonationIssue != null) return impersonationIssue

        return try {
            val appEngineDeploys = appEngineLister.listAllDeploys(project)
            val cloudRunDeploys = cloudRunLister.listAllDeploys(project)
            logger.info(
                "Service account validation succeeded: project=${project.projectId}, " +
                    "serviceAccount=${project.serviceAccount}, appEngineVersions=${appEngineDeploys.size}, " +
                    "cloudRunRevisions=${cloudRunDeploys.size}",
            )
            ServiceAccountIssue.NONE
        } catch (e: GcpListingException) {
            val issue =
                if (e.reason == SERVICE_DISABLED_REASON) ServiceAccountIssue.APIS_NOT_ENABLED
                else if (e.isPermissionDenied) ServiceAccountIssue.MISSING_LISTING_PERMISSION
                else if (e.isProjectNotFound) ServiceAccountIssue.PROJECT_NOT_FOUND
                else ServiceAccountIssue.PORTAL_ISSUE
            logger.warn(
                "Service account validation failed during listing: project=${project.projectId}, " +
                    "serviceAccount=${project.serviceAccount}, issue=${issue}, statusCode=${e.statusCode}, " +
                    "reason=${e.reason}, causeChain=\"${GcpListingException.causeChainMessage(e)}\"",
            )
            issue
        } catch (e: CredentialsException) {
            logger.warn(
                "Service account validation failed during listing: project=${project.projectId}, " +
                    "serviceAccount=${project.serviceAccount}, issue=PORTAL_ISSUE, " +
                    "causeChain=\"${GcpListingException.causeChainMessage(e)}\"",
            )
            ServiceAccountIssue.PORTAL_ISSUE
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GcpServiceAccountValidator::class.java)

        /** `google.rpc.ErrorInfo` reason of the listing error when a listing API is not enabled. */
        private const val SERVICE_DISABLED_REASON = "SERVICE_DISABLED"
    }
}
