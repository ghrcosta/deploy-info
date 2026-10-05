package infrastructure.gcp.appengine

import application.GcpAppEngineLister
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.rpc.ApiException
import com.google.appengine.v1.*
import com.google.auth.oauth2.GoogleCredentials
import com.google.protobuf.Timestamp
import domain.AppEngineDeploy
import domain.GcpListingException
import domain.Project
import infrastructure.gcp.CredentialsProvider
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Lists App Engine services/versions of a project through the official Google Cloud App Engine Admin
 * client library, authenticating with credentials impersonating the project's service account.
 *
 * The client library takes care of authentication, request building, JSON parsing and pagination;
 * this class only caches the underlying client per service account and maps the library's resources
 * into [AppEngineDeploy] domain objects.
 */
class GcpAppEngineApiClient(
    private val credentialsProvider: CredentialsProvider,
    private val adminClientFactory: (GoogleCredentials) -> AppEngineAdminClient,
) : GcpAppEngineLister {

    private val adminClientsByServiceAccount = ConcurrentHashMap<String, AppEngineAdminClient>()

    override fun listAllDeploys(project: Project): List<AppEngineDeploy> {
        val adminClient = adminClientFor(project)
        val services = catchListingErrors(project) { adminClient.listServices(project.name) }
        return services
            .flatMap { service ->
                val versions = catchListingErrors(project) { adminClient.listVersions(project.name, service.id) }
                versions.map { it.toAppEngineDeploy(project, service.id) }
            }
            .sortedByDescending { it.createTime }
    }

    /** One client per service account — each uses its own impersonated credentials. */
    private fun adminClientFor(project: Project): AppEngineAdminClient =
        adminClientsByServiceAccount.computeIfAbsent(project.serviceAccount) {
            adminClientFactory(credentialsProvider.credentialsFor(project))
        }

    /** Wraps API failures of the client library in [GcpListingException]. */
    private fun <T> catchListingErrors(project: Project, listing: () -> T): T =
        try {
            listing()
        } catch (e: ApiException) {
            throw GcpListingException(
                "GCP App Engine API returned ${e.statusCode.code} for project ${project.name}", e
            )
        }

    private fun Version.toAppEngineDeploy(project: Project, serviceId: String): AppEngineDeploy =
        AppEngineDeploy(
            projectId = project.name,
            serviceId = serviceId,
            versionId = id,
            createTime = createTime.toInstant(project.name, serviceId, id),
            url = versionUrl.takeIf { it.isNotEmpty() },
            createdBy = createdBy.takeIf { it.isNotEmpty() },
        )

    private fun Timestamp.toInstant(projectId: String, serviceId: String, versionId: String): Instant {
        if (seconds == 0L && nanos == 0) {
            throw GcpListingException(
                "Version ${serviceId}/${versionId} of project ${projectId} has no createTime"
            )
        }
        return Instant.ofEpochSecond(seconds, nanos.toLong())
    }

    /**
     * Seam over the App Engine Admin client library, so the tests can exercise the mapping and
     * error handling with simple in-memory doubles instead of mocking the gax paging machinery.
     */
    interface AppEngineAdminClient {

        /** Lists every service of the App Engine application. */
        fun listServices(projectId: String): List<Service>

        fun listVersions(projectId: String, serviceId: String): List<Version>
    }

    private class GaxAppEngineAdminClient(
        private val servicesClient: ServicesClient,
        private val versionsClient: VersionsClient,
    ) : AppEngineAdminClient {

        override fun listServices(projectId: String): List<Service> =
            servicesClient
                .listServices(
                    ListServicesRequest.newBuilder().setParent("apps/${projectId}").build()
                )
                .iterateAll()
                .toList()

        override fun listVersions(projectId: String, serviceId: String): List<Version> =
            versionsClient
                .listVersions(
                    ListVersionsRequest.newBuilder()
                        .setParent("apps/${projectId}/services/${serviceId}")
                        .build()
                )
                .iterateAll()
                .toList()
    }

    companion object {

        /** Creates the lister backed by the real App Engine Admin client library. */
        fun create(credentialsProvider: CredentialsProvider): GcpAppEngineLister =
            GcpAppEngineApiClient(credentialsProvider, ::gaxAppEngineAdminClient)

        private fun gaxAppEngineAdminClient(credentials: GoogleCredentials): AppEngineAdminClient =
            GaxAppEngineAdminClient(
                ServicesClient.create(servicesSettings(credentials)),
                VersionsClient.create(versionsSettings(credentials)),
            )

        private fun servicesSettings(credentials: GoogleCredentials): ServicesSettings =
            ServicesSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build()

        private fun versionsSettings(credentials: GoogleCredentials): VersionsSettings =
            VersionsSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build()
    }
}