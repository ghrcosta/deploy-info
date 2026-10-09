package tests.infrastructure.gcp

import application.GcpAppEngineLister
import application.GcpCloudRunLister
import application.ServiceAccountIssue
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import domain.CredentialsErrorCategory
import domain.CredentialsException
import domain.GcpListingException
import domain.Project
import infrastructure.gcp.CredentialsProvider
import infrastructure.gcp.GcpServiceAccountValidator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*
import kotlin.test.assertEquals

/**
 * Unit tests of the two-step service-account validation (impersonation first, then the GAE + Cloud
 * Run listings) — see `documentation/plan-service-account-validation.md`. The credential failures
 * themselves are categorized in `GcpCredentialsProviderTests`.
 */
class GcpServiceAccountValidatorTests {

    private val project = Project(name = "proj", group = null, serviceAccount = "sa@proj.iam.gserviceaccount.com")

    private val okCredentialsProvider = CredentialsProvider {
        GoogleCredentials.create(AccessToken("token", Date(Long.MAX_VALUE)))
    }

    @Test
    fun `Report NONE when impersonation and both listings succeed`() {
        val appEngineLister = mock<GcpAppEngineLister>()
        val cloudRunLister = mock<GcpCloudRunLister>()

        val issue = GcpServiceAccountValidator(okCredentialsProvider, appEngineLister, cloudRunLister)
            .validate(project)

        assertEquals(ServiceAccountIssue.NONE, issue)
        verify(appEngineLister).listAllDeploys(project)
        verify(cloudRunLister).listAllDeploys(project)
    }

    @Test
    fun `Report MISSING_IMPERSONATION_PERMISSION when the portal cannot impersonate the service account and skip the listings`() {
        val credentialsProvider = CredentialsProvider {
            throw CredentialsException(
                "The caller does not have permission iam.serviceAccounts.getAccessToken",
                CredentialsErrorCategory.SERVICE_ACCOUNT_MISCONFIGURATION,
            )
        }
        val appEngineLister = mock<GcpAppEngineLister>()
        val cloudRunLister = mock<GcpCloudRunLister>()

        val issue = GcpServiceAccountValidator(credentialsProvider, appEngineLister, cloudRunLister)
            .validate(project)

        assertEquals(ServiceAccountIssue.MISSING_IMPERSONATION_PERMISSION, issue)
        verify(appEngineLister, never()).listAllDeploys(any())
        verify(cloudRunLister, never()).listAllDeploys(any())
    }

    @Test
    fun `Report PORTAL_ISSUE when obtaining credentials fails portal-side`() {
        val credentialsProvider = CredentialsProvider {
            throw CredentialsException(
                "Could not obtain the portal's own credentials",
                CredentialsErrorCategory.PORTAL_ISSUE,
            )
        }

        val issue = GcpServiceAccountValidator(credentialsProvider, mock(), mock()).validate(project)

        assertEquals(ServiceAccountIssue.PORTAL_ISSUE, issue)
    }

    @Test
    fun `Report MISSING_LISTING_PERMISSION when a listing API denies access`() {
        val appEngineLister = mock<GcpAppEngineLister> {
            on { listAllDeploys(any()) } doThrow
                GcpListingException("GCP App Engine API returned PERMISSION_DENIED", statusCode = "PERMISSION_DENIED")
        }

        val issue = GcpServiceAccountValidator(okCredentialsProvider, appEngineLister, mock()).validate(project)

        assertEquals(ServiceAccountIssue.MISSING_LISTING_PERMISSION, issue)
    }

    @Test
    fun `Report PORTAL_ISSUE when a listing fails for another reason`() {
        val appEngineLister = mock<GcpAppEngineLister> {
            on { listAllDeploys(any()) } doThrow GcpListingException("transient listing failure")
        }

        val issue = GcpServiceAccountValidator(okCredentialsProvider, appEngineLister, mock()).validate(project)

        assertEquals(ServiceAccountIssue.PORTAL_ISSUE, issue)
    }
}
