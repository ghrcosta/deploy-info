package infrastructure.security

import application.auth.AuthenticateUserUseCase
import infrastructure.config.DeployInfoProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers the [AuthenticationFilter] on the protected API prefixes. The filter paths follow the
 * servlet wildcard convention, so the pattern for the `portal` prefix covers deeper paths like
 * `portal/deploy/content` too. Everything else — the static frontend resources and the frontend
 * fallback (the UI is served from the same jar) — stays public, so the browser can load the app
 * shell and render the sign-in screen.
 */
@Configuration
class SecurityConfiguration {

    @Bean
    fun authenticationFilterRegistration(
        authenticateUserUseCase: AuthenticateUserUseCase,
        properties: DeployInfoProperties,
    ): FilterRegistrationBean<AuthenticationFilter> {
        val registration = FilterRegistrationBean(
            AuthenticationFilter(authenticateUserUseCase, properties),
        )
        registration.addUrlPatterns("/portal/*", "/settings/*", "/collector/*", "/auth/user")
        return registration
    }
}
