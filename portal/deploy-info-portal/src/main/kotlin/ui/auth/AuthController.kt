package ui.auth

import infrastructure.config.DeployInfoProperties
import infrastructure.security.AuthenticationFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Authentication contracts for the frontend (see `documentation/api.md` and
 * `documentation/authentication.md`): the public sign-in configuration, and the identity of the
 * authenticated user.
 */
@RestController
@RequestMapping("auth")
class AuthController(
    private val properties: DeployInfoProperties,
) {

    /** The authenticated user — the filter guarantees the token was verified and the user permitted. */
    @GetMapping("/user")
    fun user(httpServletRequest: HttpServletRequest): AuthUserDTO =
        AuthUserDTO(
            email = httpServletRequest.getAttribute(AuthenticationFilter.AUTHENTICATED_EMAIL_ATTRIBUTE)
                as? String ?: "",
        )

    /** The sign-in configuration — public, so the login screen can render before any authentication. */
    @GetMapping("/config")
    fun config(): AuthConfigDTO = AuthConfigDTO(properties.auth.enabled, properties.auth.clientId)
}

