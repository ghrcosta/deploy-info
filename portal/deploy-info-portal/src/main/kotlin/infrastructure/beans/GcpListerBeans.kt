package infrastructure.beans

import application.GcpAppEngineLister
import application.GcpCloudRunLister
import application.PortalIdentityResolver
import application.ServiceAccountValidator
import com.google.auth.oauth2.GoogleCredentials
import infrastructure.gcp.GcpCredentialsProvider
import infrastructure.gcp.GcpPortalIdentityResolver
import infrastructure.gcp.GcpServiceAccountValidator
import infrastructure.gcp.appengine.GcpAppEngineApiClient
import infrastructure.gcp.cloudrun.GcpCloudRunApiClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GcpListerBeans {

    @Bean
    fun gcpAppEngineLister(gcpCredentialsProvider: GcpCredentialsProvider): GcpAppEngineLister =
        GcpAppEngineApiClient.create(gcpCredentialsProvider)

    @Bean
    fun gcpCloudRunLister(gcpCredentialsProvider: GcpCredentialsProvider): GcpCloudRunLister =
        GcpCloudRunApiClient.create(gcpCredentialsProvider)

    /**
     * Impersonates each configured project's service account through the IAM Credentials API to
     * obtain the credentials used by the listing APIs. The portal's own credentials are resolved
     * lazily (Application Default Credentials) so that missing credentials only fail when a listing
     * call is actually made, not at startup.
     */
    @Bean
    fun gcpCredentialsProvider(): GcpCredentialsProvider = GcpCredentialsProvider(
        callerCredentials = { GoogleCredentials.getApplicationDefault() },
    )

    /**
     * Validates a project's service account the same way the listing uses it — impersonation first,
     * then the GAE / Cloud Run listings — lazily per add/edit request, never at startup.
     */
    @Bean
    fun serviceAccountValidator(
        gcpCredentialsProvider: GcpCredentialsProvider,
        gcpAppEngineLister: GcpAppEngineLister,
        gcpCloudRunLister: GcpCloudRunLister,
    ): ServiceAccountValidator = GcpServiceAccountValidator(
        gcpCredentialsProvider, gcpAppEngineLister, gcpCloudRunLister,
    )

    /**
     * Resolves the portal's own service account email from the same lazily-resolved Application
     * Default Credentials the impersonation uses — only looked up when an add/edit response reports
     * a service-account issue.
     */
    @Bean
    fun portalIdentityResolver(): PortalIdentityResolver = GcpPortalIdentityResolver(
        callerCredentials = { GoogleCredentials.getApplicationDefault() },
    )
}
