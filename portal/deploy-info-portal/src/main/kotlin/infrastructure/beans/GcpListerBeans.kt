package infrastructure.beans

import application.GcpAppEngineLister
import application.GcpCloudRunLister
import infrastructure.gcp.AccessTokenProvider
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
     * Placeholder until impersonation is implemented (Phase 1, item 4): any attempt to call a GCP
     * listing API will fail until a real token provider is registered.
     */
    @Bean
    fun accessTokenProvider(): AccessTokenProvider = AccessTokenProvider {
        throw IllegalStateException(
            "Access token generation for project '${it.name}' is not implemented yet (impersonation, TODO Phase 1)"
        )
    }
}
