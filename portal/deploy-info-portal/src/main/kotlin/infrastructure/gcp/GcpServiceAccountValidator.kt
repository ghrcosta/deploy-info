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
 * Validates a project's service account by exercising the same permission chain the deployment listing
 * uses -- see: documentation/backend/gcp-deployment-listing.md.
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
