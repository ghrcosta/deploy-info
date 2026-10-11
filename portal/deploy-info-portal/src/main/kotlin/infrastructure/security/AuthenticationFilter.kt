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
 * Rejects requests to the protected API prefixes (`/portal`, `/settings`, `/collector`, `/auth/user`)
 * unless they carry a Google token of a user who is present in the IAM of the project the portal runs
 * in — directly or through a group. Two token kinds are accepted, chosen by the path:
 *
 * - browser requests (`/portal`, `/settings`, `/auth/user`) carry the Google Identity Services ID
 *   token the frontend obtained at sign-in;
 * - collector requests (`/collector`) carry the OAuth access token the collector obtains with
 *   `gcloud auth print-access-token`.
 *
 * Failures are reported as JSON bodies the callers can act on: 401 for a missing/invalid token, 403
 * for a valid token of an unpermitted user, 503 when the portal cannot run its IAM membership check.
 * The verification results are cached (see the implementations behind [AuthenticateUserUseCase]), so
 * the cost per request is amortized across calls.
 *
 * Requests pass through untouched while authentication is disabled
 * (`deploy-info.auth.enabled=false` — the local and test profiles), and CORS preflights (`OPTIONS`)
 * always pass through: browsers do not send credentials on preflights, and the CORS handling itself
 * lives at the MVC level (see `CorsConfiguration`).
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
