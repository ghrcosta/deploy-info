package infrastructure.gcp

import application.auth.AuthenticationIssue
import application.auth.UserPermissionChecker
import com.google.api.gax.rpc.ApiException
import com.google.api.gax.rpc.StatusCode
import com.google.cloud.asset.v1.AnalyzeIamPolicyRequest
import com.google.cloud.asset.v1.AnalyzeIamPolicyResponse
import com.google.cloud.asset.v1.AssetServiceClient
import com.google.cloud.asset.v1.IamPolicyAnalysisQuery
import domain.GcpListingException
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Checks that a user is present in the IAM of the project the portal runs in — directly, or through
 * a group listed there — with the Cloud Asset API's `AnalyzeIamPolicy`, run on the portal's own
 * Application Default Credentials:
 *
 * - the query's `identitySelector` restricts the analysis to the user;
 * - `expandGroups` makes the analysis traverse the group-membership graph, so a user bound to a
 *   project only through a Google Group is found too;
 * - the analysis results being non-empty means the user has at least one binding under the project
 *   (there is no access restriction on the query: any IAM presence counts).
 *
 * An empty result means the user is not present. A denied analysis (the portal's service account
 * lacks `cloudasset.assets.analyzeIamPolicy` — e.g. via `roles/cloudasset.viewer` — or the Cloud
 * Asset API is disabled in the project) is a portal-side IAM configuration problem, reported as
 * [AuthenticationIssue.PORTAL_IAM_CONFIG] so an admin gets a distinct remediation instead of users
 * being told they lack access.
 *
 * Results are cached per email — the analysis is a relatively expensive call — with a long TTL for
 * positive results (an existing grant rarely disappears) and a short TTL for negative ones, so a
 * newly granted user is not locked out for long. Like every GCP interaction in the portal, the
 * Cloud Asset client is created lazily on the first check, never at startup.
 */
class GcpIamMembershipChecker(
    /** Resolves the portal's own project id, or null when it cannot be determined. */
    private val projectId: () -> String?,
    /** Seam over the Cloud Asset client library, so tests can avoid the gax machinery. */
    private val assetAnalyzerFactory: () -> AssetAnalyzer,
    /** Overridable for tests; null uses the system clock. */
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : UserPermissionChecker {

    /** Performs `AnalyzeIamPolicy` calls — implemented against the real client library by [create]. */
    interface AssetAnalyzer {

        fun analyzeIamPolicy(request: AnalyzeIamPolicyRequest): AnalyzeIamPolicyResponse
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    override fun check(email: String): AuthenticationIssue {
        cache[email]?.takeIf { it.validUntil > nowSeconds() }?.let { return it.issue }
        val issue = checkWithCloudAsset(email)
        val ttlSeconds =
            if (issue == AuthenticationIssue.NONE) POSITIVE_CACHE_TTL_SECONDS else NEGATIVE_CACHE_TTL_SECONDS
        cache[email] = CacheEntry(issue, nowSeconds() + ttlSeconds)
        return issue
    }
    private fun checkWithCloudAsset(email: String): AuthenticationIssue = try {
        val analysis = assetAnalyzer.analyzeIamPolicy(requestFor(email))
        val mainAnalysis = analysis.mainAnalysis
        if (mainAnalysis.analysisResultsCount > 0) {
            logger.info("IAM membership check succeeded: email={}", email)
            AuthenticationIssue.NONE
        } else {
            logger.info(
                "IAM membership check found no binding: email={}, fullyExplored={}",
                email,
                mainAnalysis.fullyExplored,
            )
            AuthenticationIssue.NOT_PERMITTED
        }
    } catch (e: ApiException) {
        val reason = GcpListingException.errorInfoReason(e)
        logger.warn(
            "IAM membership check failed: email={}, statusCode={}, reason={}, causeChain=\"{}\"",
            email,
            e.statusCode.code,
            reason,
            GcpListingException.causeChainMessage(e),
        )
        if (e.statusCode.code == StatusCode.Code.PERMISSION_DENIED ||
            e.statusCode.code == StatusCode.Code.UNAUTHENTICATED ||
            reason == SERVICE_DISABLED_REASON
        ) {
            AuthenticationIssue.PORTAL_IAM_CONFIG
        } else {
            AuthenticationIssue.PORTAL_ISSUE
        }
    } catch (e: Exception) {
        logger.warn(
            "IAM membership check failed: email={}, issue=PORTAL_ISSUE, causeChain=\"{}\"",
            email,
            GcpListingException.causeChainMessage(e),
        )
        AuthenticationIssue.PORTAL_ISSUE
    }

    /** A new Cloud Asset client per check would waste connections — one instance serves all checks. */
    private val assetAnalyzer: AssetAnalyzer by lazy { assetAnalyzerFactory() }

    private fun requestFor(email: String): AnalyzeIamPolicyRequest {
        val project = projectId() ?: throw IllegalStateException(
            "Cannot check IAM membership: the portal's own project id could not be resolved " +
                "(the ${GcpProjectIdResolver.ENVIRONMENT_VARIABLE} environment variable, which the App Engine " +
                "runtime always sets, is unset and the metadata server did not answer). " +
                "Set the environment variable explicitly when exercising authentication outside App Engine.",
        )
        return AnalyzeIamPolicyRequest.newBuilder()
            .setAnalysisQuery(
                IamPolicyAnalysisQuery.newBuilder()
                    .setScope("projects/$project")
                    .setIdentitySelector(
                        IamPolicyAnalysisQuery.IdentitySelector.newBuilder().setIdentity("user:$email")
                    )
                    .setOptions(IamPolicyAnalysisQuery.Options.newBuilder().setExpandGroups(true))
                    .build()
            )
            .build()
    }

    private data class CacheEntry(val issue: AuthenticationIssue, val validUntil: Long)

    companion object {
        private val logger = LoggerFactory.getLogger(GcpIamMembershipChecker::class.java)

        /** `google.rpc.ErrorInfo` reason of the error when the Cloud Asset API is not enabled. */
        private const val SERVICE_DISABLED_REASON = "SERVICE_DISABLED"

        private const val POSITIVE_CACHE_TTL_SECONDS = 600L
        private const val NEGATIVE_CACHE_TTL_SECONDS = 60L

        /** Creates the checker backed by the real Cloud Asset client library (portal ADC). */
        fun create(projectId: () -> String?): GcpIamMembershipChecker =
            GcpIamMembershipChecker(projectId, ::gaxAssetAnalyzer)

        private fun gaxAssetAnalyzer(): AssetAnalyzer = GaxAssetAnalyzer(AssetServiceClient.create())

        private class GaxAssetAnalyzer(
            private val client: AssetServiceClient,
        ) : AssetAnalyzer {

            override fun analyzeIamPolicy(request: AnalyzeIamPolicyRequest): AnalyzeIamPolicyResponse =
                client.analyzeIamPolicy(request)
        }
    }
}
