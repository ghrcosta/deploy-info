package infrastructure.beans

import application.auth.AccessTokenVerifier
import application.auth.AuthenticateUserUseCase
import application.auth.IdTokenVerifier
import application.auth.UserPermissionChecker
import infrastructure.auth.GoogleAccessTokenVerifier
import infrastructure.auth.GoogleIdTokenVerifier
import infrastructure.auth.GoogleTokenInfoClient
import infrastructure.config.DeployInfoProperties
import infrastructure.gcp.GcpIamMembershipChecker
import infrastructure.gcp.GcpProjectIdResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the authentication chain: token verification against Google's tokeninfo endpoint, the
 * group-aware IAM membership check against the Cloud Asset API, and the use case combining them.
 * Like every GCP interaction in the portal, nothing here touches the network at startup — the
 * tokeninfo client and the Cloud Asset client only act when a request is actually authenticated.
 */
@Configuration
class AuthBeans(private val properties: DeployInfoProperties) {

    init {
        val auth = properties.auth
        if (auth.enabled && auth.clientId.isBlank()) {
            throw IllegalStateException(
                "deploy-info.auth.enabled=true requires deploy-info.auth.client-id: set it to the OAuth " +
                    "client id the frontend's Google Identity Services sign-in issues tokens for — it is the " +
                    "audience the portal verifies on those ID tokens.",
            )
        }
    }

    @Bean
    fun googleTokenInfoClient(): GoogleTokenInfoClient = GoogleTokenInfoClient()

    @Bean
    fun idTokenVerifier(googleTokenInfoClient: GoogleTokenInfoClient): IdTokenVerifier =
        GoogleIdTokenVerifier(googleTokenInfoClient, properties.auth.clientId)

    @Bean
    fun accessTokenVerifier(googleTokenInfoClient: GoogleTokenInfoClient): AccessTokenVerifier =
        GoogleAccessTokenVerifier(googleTokenInfoClient)

    /**
     * Resolves the project id of the portal itself (the App Engine runtime sets the
     * `GOOGLE_CLOUD_PROJECT` environment variable) — lazily, when a membership check needs it.
     */
    @Bean
    fun gcpProjectIdResolver(): GcpProjectIdResolver = GcpProjectIdResolver()

    @Bean
    fun userPermissionChecker(gcpProjectIdResolver: GcpProjectIdResolver): UserPermissionChecker =
        GcpIamMembershipChecker.create(projectId = { gcpProjectIdResolver.resolve() })

    @Bean
    fun authenticateUserUseCase(
        idTokenVerifier: IdTokenVerifier,
        accessTokenVerifier: AccessTokenVerifier,
        userPermissionChecker: UserPermissionChecker,
    ): AuthenticateUserUseCase = AuthenticateUserUseCase(idTokenVerifier, accessTokenVerifier, userPermissionChecker)
}
