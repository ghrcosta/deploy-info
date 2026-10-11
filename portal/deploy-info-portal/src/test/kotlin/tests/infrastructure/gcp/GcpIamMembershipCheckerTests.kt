package tests.infrastructure.gcp

import application.auth.AuthenticationIssue
import com.google.api.gax.rpc.ApiException
import com.google.api.gax.rpc.StatusCode
import com.google.cloud.asset.v1.AnalyzeIamPolicyRequest
import com.google.cloud.asset.v1.AnalyzeIamPolicyResponse
import infrastructure.gcp.GcpIamMembershipChecker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fake analyzer plays the Cloud Asset API: a non-empty analysis result list means the identity
 * has at least one binding under the project — directly or through a group (the group expansion
 * itself happens server-side; the checker's contract is exercised via the request shape below).
 */
private class FakeAssetAnalyzer : GcpIamMembershipChecker.AssetAnalyzer {

    val requests = mutableListOf<AnalyzeIamPolicyRequest>()
    var callCount = 0
    var analysisResults = 0
    var exception: Exception? = null

    override fun analyzeIamPolicy(request: AnalyzeIamPolicyRequest): AnalyzeIamPolicyResponse {
        callCount++
        requests += request
        exception?.let { throw it }
        val builder = AnalyzeIamPolicyResponse.newBuilder()
        if (analysisResults > 0) {
            builder.mainAnalysis = builder.mainAnalysisBuilder
                .addAnalysisResults(com.google.cloud.asset.v1.IamPolicyAnalysisResult.getDefaultInstance())
                .build()
        }
        return builder.build()
    }
}

/**
 * This gax version's `StatusCode.Code` enum does not implement the [StatusCode] interface, so the
 * exceptions thrown by the fake wrap a tiny hand-made [StatusCode] carrying the wanted code.
 */
private fun statusCode(code: StatusCode.Code): StatusCode =
    object : StatusCode {
        override fun getCode(): StatusCode.Code = code

        override fun getTransportCode(): Any = ""
    }

class GcpIamMembershipCheckerTests {

    private var clock = 1000L
    private val analyzer = FakeAssetAnalyzer()
    private var projectId: String? = "portal-proj"

    // One checker instance per test: the cache is per instance, so the TTL tests must share it.
    private val checker = GcpIamMembershipChecker(
        projectId = { projectId },
        assetAnalyzerFactory = { analyzer },
        nowSeconds = { clock },
    )

    @Test
    fun `A user with a binding under the portal project is permitted`() {
        analyzer.analysisResults = 1

        assertEquals(AuthenticationIssue.NONE, checker.check("user@example.com"))
    }

    @Test
    fun `The analysis asks for the user's identity, group expansion and the portal project scope`() {
        analyzer.analysisResults = 1

        checker.check("user@example.com")

        val query = analyzer.requests.single().analysisQuery
        assertEquals("projects/portal-proj", query.scope)
        assertEquals("user:user@example.com", query.identitySelector.identity)
        assertTrue(query.options.expandGroups)
    }

    @Test
    fun `A user with no binding is not permitted`() {
        analyzer.analysisResults = 0

        assertEquals(AuthenticationIssue.NOT_PERMITTED, checker.check("user@example.com"))
    }

    @Test
    fun `A denied analysis is a portal IAM configuration problem`() {
        analyzer.exception = ApiException(
            IllegalStateException("\"reason\": \"PERMISSION_DENIED\""),
            statusCode(StatusCode.Code.PERMISSION_DENIED),
            false,
        )

        assertEquals(AuthenticationIssue.PORTAL_IAM_CONFIG, checker.check("user@example.com"))
    }

    @Test
    fun `A disabled Cloud Asset API is a portal IAM configuration problem`() {
        analyzer.exception = ApiException(
            IllegalStateException("Google Cloud Asset API has not been used in project portal-proj before"),
            statusCode(StatusCode.Code.FAILED_PRECONDITION),
            false,
        )

        assertEquals(AuthenticationIssue.PORTAL_IAM_CONFIG, checker.check("user@example.com"))
    }

    @Test
    fun `Another API failure is a portal issue`() {
        analyzer.exception = ApiException(
            IllegalStateException("boom"),
            statusCode(StatusCode.Code.INTERNAL),
            false,
        )

        assertEquals(AuthenticationIssue.PORTAL_ISSUE, checker.check("user@example.com"))
    }

    @Test
    fun `An unresolvable portal project id is a portal issue and never calls the API`() {
        projectId = null

        assertEquals(AuthenticationIssue.PORTAL_ISSUE, checker.check("user@example.com"))
        assertEquals(0, analyzer.callCount)
    }

    @Test
    fun `Positive results are cached for the long TTL`() {
        analyzer.analysisResults = 1

        checker.check("user@example.com")
        clock += 599 // still within the 600s TTL
        checker.check("user@example.com")

        assertEquals(1, analyzer.callCount)
        clock += 1
        checker.check("user@example.com")
        assertEquals(2, analyzer.callCount)
    }

    @Test
    fun `Negative results are cached only briefly, so a newly granted user is not locked out long`() {
        checker.check("user@example.com")
        clock += 59 // still within the 60s TTL
        checker.check("user@example.com")

        assertEquals(1, analyzer.callCount)
        clock += 1
        checker.check("user@example.com")
        assertEquals(2, analyzer.callCount)
    }

    @Test
    fun `Caches per email`() {
        analyzer.analysisResults = 1

        checker.check("a@example.com")
        checker.check("b@example.com")

        assertEquals(2, analyzer.callCount)
    }
}
