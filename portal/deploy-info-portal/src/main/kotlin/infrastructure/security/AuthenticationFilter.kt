package infrastructure.security

import application.auth.AuthenticateUserUseCase
import application.auth.AuthenticateUserUseCase.TokenKind
import application.auth.AuthenticationIssue
import infrastructure.config.DeployInfoProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * Rejects requests to the protected API prefixes unless they carry a Google token of a user who
 * is present in the IAM of the project the portal runs in — directly or through a group.
 *
 * Requests pass through untouched while authentication is disabled (`deploy-info.auth.enabled=false`).
 */
class AuthenticationFilter(
    private val authenticateUserUseCase: AuthenticateUserUseCase,
    private val properties: DeployInfoProperties,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!properties.auth.enabled || request.method == "OPTIONS") {
            filterChain.doFilter(request, response)
            return
        }

        val token = bearerToken(request) ?: run {
            reject(response, HttpStatus.UNAUTHORIZED, "unauthenticated")
            return
        }
        val kind =
            if (request.requestURI.startsWith(COLLECTOR_PREFIX)) TokenKind.COLLECTOR_ACCESS_TOKEN
            else TokenKind.BROWSER_ID_TOKEN

        val user = authenticateUserUseCase.authenticate(token, kind)
        when (user.issue) {
            AuthenticationIssue.INVALID_TOKEN -> reject(response, HttpStatus.UNAUTHORIZED, "unauthenticated")
            AuthenticationIssue.NOT_PERMITTED ->
                reject(
                    response,
                    HttpStatus.FORBIDDEN,
                    "not-permitted",
                    mapOf("email" to user.email),
                )
            AuthenticationIssue.PORTAL_IAM_CONFIG, AuthenticationIssue.PORTAL_ISSUE ->
                reject(response, HttpStatus.SERVICE_UNAVAILABLE, "portal-issue")
            AuthenticationIssue.NONE -> {
                request.setAttribute(AUTHENTICATED_EMAIL_ATTRIBUTE, user.email)
                filterChain.doFilter(request, response)
            }
        }
    }

    private fun bearerToken(request: HttpServletRequest): String? {
        val header = request.getHeader(AUTHORIZATION_HEADER) ?: return null
        if (!header.startsWith(BEARER_PREFIX)) return null
        return header.removePrefix(BEARER_PREFIX).trim().takeIf { it.isNotEmpty() }
    }

    private fun reject(
        response: HttpServletResponse,
        status: HttpStatus,
        error: String,
        extra: Map<String, String> = emptyMap(),
    ) {
        response.status = status.value()
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write(jsonMapper.writeValueAsString(mapOf("error" to error) + extra))
    }

    companion object {
        /**
         * Request attribute holding the authenticated user's email for the handlers behind the
         * filter (see the email-match check in `CollectorController`).
         */
        const val AUTHENTICATED_EMAIL_ATTRIBUTE = "deployInfo.authenticatedEmail"

        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val COLLECTOR_PREFIX = "/collector"

        private val jsonMapper = jacksonObjectMapper()
    }
}
