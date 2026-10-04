package infrastructure.beans

import application.GcpAppEngineLister
import application.GcpCloudRunLister
import com.google.auth.oauth2.GoogleCredentials
import infrastructure.gcp.GcpCredentialsProvider
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
}
