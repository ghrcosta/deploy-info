package infrastructure.security

import application.auth.AuthenticateUserUseCase
import infrastructure.config.DeployInfoProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers the [AuthenticationFilter] on the protected API prefixes. Everything else — the static
 * frontend resources — stays public, so the browser can load the sign-in screen.
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
