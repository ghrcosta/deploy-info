package infrastructure.frontend

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Serves the frontend embedded in the jar. Registering an all-paths handler replaces Spring Boot's
 * default static-resource handler (Boot skips its own when one is already registered for every
 * path) and adds the frontend fallback on top of it. The API endpoints keep working because Spring
 * MVC consults the request-mapped controllers before resource handlers. The serving is same-origin,
 * so no CORS configuration applies to it (see `infrastructure/security/CorsConfiguration.kt`).
 */
@Configuration
class FrontendServingConfiguration : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(FrontendResourceResolver(API_PREFIXES))
    }

    private companion object {
        /** First path segments of the backend HTTP endpoints (see the controllers in `ui/`). */
        val API_PREFIXES = listOf("collector", "portal", "settings")
    }
}
