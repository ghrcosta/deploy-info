package infrastructure.gcp.cloudrun

import application.GcpCloudRunLister
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.rpc.ApiException
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.run.v2.*
import com.google.protobuf.Timestamp
import domain.CloudRunDeploy
import domain.GcpListingException
import domain.Project
import infrastructure.gcp.CredentialsProvider
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Lists Cloud Run services/revisions of a project through the official Google Cloud Run client
 * library, authenticating with credentials impersonating the project's service account.
 *
 * The client library takes care of authentication, request building, JSON parsing and pagination;
 * this class only caches the underlying clients per service account, resolves the location/service
 * ids out of the resource names and maps the library's resources into [CloudRunDeploy] domain
 * objects.
 */
class GcpCloudRunApiClient(
    private val credentialsProvider: CredentialsProvider,
    private val adminClientFactory: (GoogleCredentials) -> CloudRunAdminClient,
) : GcpCloudRunLister {

    private val adminClientsByServiceAccount = ConcurrentHashMap<String, CloudRunAdminClient>()

    override fun listAllDeploys(project: Project): List<CloudRunDeploy> {
        val adminClient = adminClientFor(project)
        return catchListingErrors(project) {
            adminClient
                .listServices(project.name)
                .flatMap { service ->
                    val serviceName = ServiceName.parse(service.name)
                    adminClient.listRevisions(service.name).map { revision ->
                        revision.toCloudRunDeploy(
                            project, serviceName.service, serviceName.location, service.uri.takeIf { it.isNotEmpty() },
                        )
                    }
                }
                .sortedByDescending { it.createTime }
        }
    }

    /** One client pair per service account — each uses its own impersonated credentials. */
    private fun adminClientFor(project: Project): CloudRunAdminClient =
        adminClientsByServiceAccount.computeIfAbsent(project.serviceAccount) {
            adminClientFactory(credentialsProvider.credentialsFor(project))
        }

    /** Wraps API failures and unexpected resource names in [GcpListingException]. */
    private fun <T> catchListingErrors(project: Project, listing: () -> T): T =
        try {
            listing()
        } catch (e: ApiException) {
            // The server's error text (denied permission, resource, remediation link) lives only in
            // the cause chain — log it before it is discarded into the compact [GcpListingException].
            logger.warn(
                "GCP Cloud Run listing failed: project=${project.name}, serviceAccount=${project.serviceAccount}, " +
                    "statusCode=${e.statusCode.code}, causeChain=\"${GcpListingException.causeChainMessage(e)}\"",
            )
            throw GcpListingException(
                "GCP Cloud Run API returned ${e.statusCode.code} for project ${project.name}",
                e,
                e.statusCode.code.toString(),
            )
        }

    private fun Revision.toCloudRunDeploy(
        project: Project,
        serviceId: String,
        location: String,
        url: String?,
    ): CloudRunDeploy {
        val revisionName = RevisionName.parse(name)
        return CloudRunDeploy(
            projectId = project.name,
            location = location,
            serviceId = serviceId,
            revisionId = revisionName.revision,
            createTime = createTime.toInstant(project.name, serviceId, revisionName.revision),
            url = url,
            createdBy = creator.takeIf { it.isNotEmpty() },
        )
    }

    private fun Timestamp.toInstant(projectId: String, serviceId: String, revisionId: String): Instant {
        if (seconds == 0L && nanos == 0) {
            throw GcpListingException(
                "Revision ${serviceId}/${revisionId} of project ${projectId} has no createTime"
            )
        }
        return Instant.ofEpochSecond(seconds, nanos.toLong())
    }

    /**
     * Seam over the Cloud Run client library, so the tests can exercise the mapping and error
     * handling with simple in-memory doubles instead of mocking the gax paging machinery.
     */
    interface CloudRunAdminClient {

        /** Lists every service of the project in every region. */
        fun listServices(projectId: String): List<Service>

        fun listRevisions(serviceName: String): List<Revision>
    }

    private class GaxCloudRunAdminClient(
        private val servicesClient: ServicesClient,
        private val revisionsClient: RevisionsClient,
    ) : CloudRunAdminClient {

        override fun listServices(projectId: String): List<Service> =
            servicesClient
                .listServices("projects/${projectId}/locations/-")
                .iterateAll()
                .toList()

        override fun listRevisions(serviceName: String): List<Revision> =
            revisionsClient
                .listRevisions(serviceName)
                .iterateAll()
                .toList()
    }

    companion object {

        private val logger = LoggerFactory.getLogger(GcpCloudRunApiClient::class.java)

        /** Creates the lister backed by the real Cloud Run client library. */
        fun create(credentialsProvider: CredentialsProvider): GcpCloudRunLister =
            GcpCloudRunApiClient(credentialsProvider, ::gaxCloudRunAdminClient)

        private fun gaxCloudRunAdminClient(credentials: GoogleCredentials): CloudRunAdminClient =
            GaxCloudRunAdminClient(
                ServicesClient.create(
                    ServicesSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                        .build()
                ),
                RevisionsClient.create(
                    RevisionsSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                        .build()
                ),
            )
    }
}