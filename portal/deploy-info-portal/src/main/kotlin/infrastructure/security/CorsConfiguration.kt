package infrastructure.security

import infrastructure.config.DeployInfoProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Applies the CORS mappings configured via `deploy-info.cors.allowed-origins`
 * (see [DeployInfoProperties.Cors]). With no configured origins no mapping is registered at all,
 * so CORS processing is disabled — required for the same-origin production serving (UI built into
 * the same jar), because browsers send an `Origin` header even on same-origin non-GET requests and
 * any request carrying an unlisted origin would be rejected.
 */
@Configuration
class CorsConfiguration(private val properties: DeployInfoProperties) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        val origins = properties.cors.allowedOrigins
        if (origins.isEmpty()) {
            return
        }
        registry.addMapping("/**")
            .allowedOrigins(*origins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}