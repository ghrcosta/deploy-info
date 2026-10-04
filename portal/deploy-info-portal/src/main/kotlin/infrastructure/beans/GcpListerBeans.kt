package infrastructure.beans

import application.GcpAppEngineLister
import application.GcpCloudRunLister
import com.google.auth.oauth2.GoogleCredentials
import infrastructure.gcp.AccessTokenProvider
import infrastructure.gcp.IamCredentialsAccessTokenProvider
import infrastructure.gcp.appengine.GcpAppEngineApiClient
import infrastructure.gcp.cloudrun.GcpCloudRunApiClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class GcpListerBeans {

    @Bean
    fun gcpAppEngineLister(accessTokenProvider: AccessTokenProvider): GcpAppEngineLister =
        GcpAppEngineApiClient(RestTemplate(), accessTokenProvider)

    @Bean
    fun gcpCloudRunLister(accessTokenProvider: AccessTokenProvider): GcpCloudRunLister =
        GcpCloudRunApiClient(RestTemplate(), accessTokenProvider)

    /**
     * Impersonates each configured project's service account through the IAM Credentials API to
     * obtain the access tokens used by the listing APIs. The portal's own credentials are resolved
     * lazily (Application Default Credentials) so that missing credentials only fail when a listing
     * call is actually made, not at startup.
     */
    @Bean
    fun accessTokenProvider(): AccessTokenProvider = IamCredentialsAccessTokenProvider(
        callerCredentials = { GoogleCredentials.getApplicationDefault() },
    )
}
