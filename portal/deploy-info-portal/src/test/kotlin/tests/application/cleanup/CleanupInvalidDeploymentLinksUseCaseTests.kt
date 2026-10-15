package tests.application.cleanup

import application.cleanup.CleanupInvalidDeploymentLinksUseCase
import domain.DeploymentLink
import domain.DeploymentType
import domain.Project
import org.junit.jupiter.api.Test
import tests.fakes.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PROJECT_ID = "testProject"
private const val PROJECT_2_ID = "otherProject"
private val NOW: Instant = Instant.ofEpochSecond(10_000_000)
private val GRACE_PERIOD: Duration = Duration.ofMinutes(10)
private val OLD_ENOUGH: Instant = NOW.minus(GRACE_PERIOD).minusSeconds(60)

private fun link(
    projectId: String = PROJECT_ID,
    deploymentType: DeploymentType = DeploymentType.GAE,
    serviceId: String,
    versionId: String,
    location: String? = null,
    collectTimestamp: Instant = OLD_ENOUGH,
    deploymentTimestamp: Instant = collectTimestamp.minusSeconds(60),
) = DeploymentLink(
    projectId = projectId,
    deploymentType = deploymentType,
    serviceId = serviceId,
    versionId = versionId,
    location = location,
    storageFolder = "uploads/$serviceId-$versionId",
    userEmail = "deployer@example.com",
    collectTimestamp = collectTimestamp,
    deploymentTimestamp = deploymentTimestamp,
)

class CleanupInvalidDeploymentLinksUseCaseTests {

    private val project = Project(projectId = PROJECT_ID, group = null, serviceAccount = "sa@test.iam.gserviceaccount.com")
    private val project2 = Project(projectId = PROJECT_2_ID, group = null, serviceAccount = "sa2@test.iam.gserviceaccount.com")

    private lateinit var fakeProjectRepository: FakeProjectRepository
    private lateinit var fakeAppEngineLister: FakeAppEngineLister
    private lateinit var fakeCloudRunLister: FakeCloudRunLister
    private lateinit var fakeDeploymentLinkRepository: FakeDeploymentLinkRepository
    private lateinit var fakeStorageCleaner: FakeStorageCleaner
    private lateinit var useCase: CleanupInvalidDeploymentLinksUseCase

    @BeforeTest
    fun setup() {
        fakeProjectRepository = FakeProjectRepository()
        fakeAppEngineLister = FakeAppEngineLister()
        fakeCloudRunLister = FakeCloudRunLister()
        fakeDeploymentLinkRepository = FakeDeploymentLinkRepository()
        fakeStorageCleaner = FakeStorageCleaner()
        fakeProjectRepository.save(project)
        useCase = CleanupInvalidDeploymentLinksUseCase(
            projectRepository = fakeProjectRepository,
            appEngineLister = fakeAppEngineLister,
            cloudRunLister = fakeCloudRunLister,
            deploymentLinkRepository = fakeDeploymentLinkRepository,
            storageCleaner = fakeStorageCleaner,
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
            gracePeriod = GRACE_PERIOD,
        )
    }

    @Test
    fun `GAE - stale link (service and version gone) is deleted from the repository and its storage folder too`() {
        fakeDeploymentLinkRepository.save(link(serviceId = "web", versionId = "v42"))
        fakeAppEngineLister.seed(PROJECT_ID, listOf(FakeAppEngineLister.deploy(PROJECT_ID, "other", "v1", NOW, null)))

        useCase.execute()

        assertEquals(0, fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.GAE).size)
        assertEquals(listOf("uploads/web-v42"), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `GAE - live link (exact service and version still listed) is kept untouched`() {
        fakeDeploymentLinkRepository.save(link(serviceId = "web", versionId = "v42"))
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(
                FakeAppEngineLister.deploy(PROJECT_ID, "web", "v41", NOW, null),
                FakeAppEngineLister.deploy(PROJECT_ID, "web", "v42", NOW, null),
            ),
        )

        useCase.execute()

        assertEquals(1, fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.GAE).size)
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
    }

    @Test
    fun `GAE - a same-service newer version does not keep the stale older version's link alive`() {
        fakeDeploymentLinkRepository.save(link(serviceId = "web", versionId = "v41"))
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(FakeAppEngineLister.deploy(PROJECT_ID, "web", "v42", NOW, null)),
        )

        useCase.execute()

        assertEquals(0, fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.GAE).size)
        assertEquals(listOf("uploads/web-v41"), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `RUN - stale revision link (revision gone) is deleted - location is part of the identity`() {
        fakeDeploymentLinkRepository.save(
            link(deploymentType = DeploymentType.RUN, serviceId = "svc", versionId = "rev-old", location = "europe-west1"),
        )
        // Same revision id in a different region, and a live revision in the same one.
        fakeCloudRunLister.seed(
            PROJECT_ID,
            listOf(
                FakeCloudRunLister.deploy(PROJECT_ID, "us-central1", "svc", "rev-old", NOW, null),
                FakeCloudRunLister.deploy(PROJECT_ID, "europe-west1", "svc", "rev-new", NOW, null),
            ),
        )

        useCase.execute()

        assertEquals(0, fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.RUN).size)
        assertEquals(listOf("uploads/svc-rev-old"), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `RUN - live revision link (same location, service and revision) is kept untouched`() {
        fakeDeploymentLinkRepository.save(
            link(deploymentType = DeploymentType.RUN, serviceId = "svc", versionId = "rev-1", location = "europe-west1"),
        )
        fakeCloudRunLister.seed(
            PROJECT_ID,
            listOf(FakeCloudRunLister.deploy(PROJECT_ID, "europe-west1", "svc", "rev-1", NOW, null)),
        )

        useCase.execute()

        assertEquals(1, fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.RUN).size)
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
    }

    @Test
    fun `Grace period - a link younger than the grace period is never deleted, even when absent from the listing`() {
        fakeDeploymentLinkRepository.save(
            link(serviceId = "web", versionId = "v42", collectTimestamp = NOW.minus(GRACE_PERIOD).plusSeconds(1)),
        )
        fakeAppEngineLister.seed(PROJECT_ID, emptyList())

        useCase.execute()

        assertEquals(1, fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.GAE).size)
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
    }

    @Test
    fun `Grace period - a link exactly as old as the grace period is not yet judged (must be strictly older)`() {
        fakeDeploymentLinkRepository.save(
            link(serviceId = "web", versionId = "v42", collectTimestamp = NOW.minus(GRACE_PERIOD)),
        )
        fakeAppEngineLister.seed(PROJECT_ID, emptyList())

        useCase.execute()

        assertEquals(1, fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.GAE).size)
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
    }

    @Test
    fun `Listing failure - every project and type with a failing listing is skipped, links are kept`() {
        fakeDeploymentLinkRepository.save(link(serviceId = "web", versionId = "v42"))
        fakeAppEngineLister.throwOnEveryList = true

        useCase.execute()

        assertEquals(1, fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.GAE).size)
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
    }

    @Test
    fun `Listing failure - a Cloud Run listing failure leaves the RUN links alone while GAE is still cleaned`() {
        fakeDeploymentLinkRepository.save(link(serviceId = "web", versionId = "v42"))
        fakeDeploymentLinkRepository.save(
            link(deploymentType = DeploymentType.RUN, serviceId = "svc", versionId = "rev-1", location = "europe-west1"),
        )
        fakeAppEngineLister.seed(PROJECT_ID, emptyList())
        fakeCloudRunLister.throwOnEveryList = true

        useCase.execute()

        assertEquals(0, fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.GAE).size)
        assertEquals(1, fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.RUN).size)
        assertEquals(listOf("uploads/web-v42"), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `Listing failure - another project fails, so only the projects that listed fine are cleaned`() {
        fakeProjectRepository.save(project2)
        fakeDeploymentLinkRepository.save(link(serviceId = "web", versionId = "v42"))
        fakeDeploymentLinkRepository.save(
            link(projectId = PROJECT_2_ID, serviceId = "web", versionId = "v42"),
        )
        // Both projects' GAE listings exist; project 2's listing throws.
        fakeAppEngineLister.seed(PROJECT_ID, emptyList())
        fakeAppEngineLister.seed(PROJECT_2_ID, emptyList())
        fakeAppEngineLister.throwOnProject = { it == PROJECT_2_ID }

        useCase.execute()

        assertEquals(0, fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.GAE).size)
        assertEquals(1, fakeDeploymentLinkRepository.getAllFor(PROJECT_2_ID, DeploymentType.GAE).size)
        assertEquals(listOf("uploads/web-v42"), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `Storage failure - the Datastore entry is still deleted and the sweep continues`() {
        fakeDeploymentLinkRepository.save(link(serviceId = "web", versionId = "v42"))
        fakeAppEngineLister.seed(PROJECT_ID, emptyList())
        fakeStorageCleaner.throwOnEveryDelete = true

        useCase.execute()

        assertEquals(0, fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.GAE).size)
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
    }

    @Test
    fun `Unconfigured project - a link whose project is no longer configured is left untouched`() {
        fakeDeploymentLinkRepository.save(
            link(projectId = "removed-project", serviceId = "web", versionId = "v42"),
        )
        fakeAppEngineLister.seed(PROJECT_ID, emptyList())
        fakeCloudRunLister.seed(PROJECT_ID, emptyList())

        useCase.execute()

        assertEquals(1, fakeDeploymentLinkRepository.getAllFor("removed-project", DeploymentType.GAE).size)
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
    }

    @Test
    fun `Nothing stale - nothing is deleted at all`() {
        fakeDeploymentLinkRepository.save(link(serviceId = "web", versionId = "v42"))
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(FakeAppEngineLister.deploy(PROJECT_ID, "web", "v42", NOW, null)),
        )

        useCase.execute()

        assertEquals(1, fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.GAE).size)
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
    }
}