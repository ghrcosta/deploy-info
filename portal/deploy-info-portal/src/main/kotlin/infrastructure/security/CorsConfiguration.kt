package infrastructure.security

import infrastructure.config.DeployInfoProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Applies the CORS mappings configured via `deploy-info.cors.allowed-origins` (rationale for the
 * empty-list-disables-everything behavior: [DeployInfoProperties.Cors]).
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