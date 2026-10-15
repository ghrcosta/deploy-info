package tests.application.collector

import application.collector.CreateDeploymentLinkUseCase
import domain.DeploymentType
import domain.InvalidDirectoryNameException
import domain.Project
import org.junit.jupiter.api.Test
import tests.fakes.*
import java.time.Duration
import java.time.Instant
import kotlin.test.*

private const val PROJECT_ID = "testProject"
private const val OTHER_PROJECT_ID = "otherProject"
private val COLLECT_TIME: Instant = Instant.ofEpochMilli(1_735_689_600_000)
private val RETRY_DELAY_TOLERANCE: Duration = Duration.ofHours(1)

private fun input(
    directoryName: String? = null,
    projects: List<String> = listOf(PROJECT_ID),
    deploymentType: DeploymentType = DeploymentType.GAE,
    userEmail: String = "deployer@example.com",
    collectTimestamp: Instant = COLLECT_TIME,
) = CreateDeploymentLinkUseCase.Input(
    // The directory name encodes the collect timestamp (convention: CreateDeploymentLinkUseCase.parseCollectTimestamp)
    directoryName = directoryName ?: "deployer_${deploymentType.name}_${collectTimestamp.toEpochMilli()}",
    projects = projects,
    deploymentType = deploymentType,
    userEmail = userEmail,
)

class CreateDeploymentLinkUseCaseTests {

    private val window: Duration = Duration.ofMinutes(15)
    private val project = Project(projectId = PROJECT_ID, group = null, serviceAccount = "sa@test.iam.gserviceaccount.com")

    private lateinit var fakeProjectRepository: FakeProjectRepository
    private lateinit var fakeAppEngineLister: FakeAppEngineLister
    private lateinit var fakeCloudRunLister: FakeCloudRunLister
    private lateinit var fakeDeploymentLinkRepository: FakeDeploymentLinkRepository
    private lateinit var fakeStorageCleaner: FakeStorageCleaner
    private lateinit var fakeRetryScheduler: FakeRetryScheduler
    private lateinit var useCase: CreateDeploymentLinkUseCase

    @BeforeTest
    fun setup() {
        fakeProjectRepository = FakeProjectRepository()
        fakeAppEngineLister = FakeAppEngineLister()
        fakeCloudRunLister = FakeCloudRunLister()
        fakeDeploymentLinkRepository = FakeDeploymentLinkRepository()
        fakeStorageCleaner = FakeStorageCleaner()
        fakeRetryScheduler = FakeRetryScheduler()
        fakeProjectRepository.save(project)
        useCase = CreateDeploymentLinkUseCase(
            projectRepository = fakeProjectRepository,
            appEngineLister = fakeAppEngineLister,
            cloudRunLister = fakeCloudRunLister,
            deploymentLinkRepository = fakeDeploymentLinkRepository,
            storageCleaner = fakeStorageCleaner,
            retryScheduler = fakeRetryScheduler,
            window = window,
        )
    }

    @Test
    fun `GAE - link created from the matching version with all collector fields stored correctly`() {
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(
                FakeAppEngineLister.deploy(
                    projectId = PROJECT_ID,
                    serviceId = "web",
                    versionId = "v42",
                    createTime = COLLECT_TIME.minusSeconds(60),
                    createdBy = "Deployer@example.com", // case-insensitive match
                ),
            ),
        )

        val output = useCase.execute(input())

        val created = assertIs<CreateDeploymentLinkUseCase.Output.Created>(output)
        assertEquals("web", created.deploymentLink.serviceId)
        assertEquals("v42", created.deploymentLink.versionId)
        assertNull(created.deploymentLink.location)
        assertEquals("deployer_GAE_1735689600000", created.deploymentLink.storageFolder)
        assertEquals("deployer@example.com", created.deploymentLink.userEmail)
        assertEquals(COLLECT_TIME, created.deploymentLink.collectTimestamp)
        assertEquals(COLLECT_TIME.minusSeconds(60), created.deploymentLink.deploymentTimestamp)
        assertEquals(created.deploymentLink, fakeDeploymentLinkRepository.get(PROJECT_ID, DeploymentType.GAE, null, "web", "v42"))
        assertTrue(fakeRetryScheduler.scheduledTasks().isEmpty())
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
    }

    @Test
    fun `Cloud Run - link created including location`() {
        fakeCloudRunLister.seed(
            PROJECT_ID,
            listOf(
                FakeCloudRunLister.deploy(
                    projectId = PROJECT_ID,
                    location = "europe-west1",
                    serviceId = "web-api",
                    revisionId = "rev-7",
                    createTime = COLLECT_TIME.minusSeconds(60),
                    createdBy = "deployer@example.com",
                ),
            ),
        )

        val output = useCase.execute(input(deploymentType = DeploymentType.RUN))

        val created = assertIs<CreateDeploymentLinkUseCase.Output.Created>(output)
        assertEquals("europe-west1", created.deploymentLink.location)
        assertEquals("web-api", created.deploymentLink.serviceId)
        assertEquals("rev-7", created.deploymentLink.versionId)
        assertEquals(COLLECT_TIME.minusSeconds(60), created.deploymentLink.deploymentTimestamp)
        assertEquals(created.deploymentLink, fakeDeploymentLinkRepository.get(PROJECT_ID, DeploymentType.RUN, "europe-west1", "web-api", "rev-7"))
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
    }

    @Test
    fun `Closest match - with two matching deployments before the collect time, the later one wins`() {
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(
                FakeAppEngineLister.deploy(PROJECT_ID, "web", "older", COLLECT_TIME.minusSeconds(600), "deployer@example.com"),
                FakeAppEngineLister.deploy(PROJECT_ID, "web", "newer", COLLECT_TIME.minusSeconds(10), "deployer@example.com"),
            ),
        )

        val output = useCase.execute(input())

        val created = assertIs<CreateDeploymentLinkUseCase.Output.Created>(output)
        assertEquals("newer", created.deploymentLink.versionId)
    }

    @Test
    fun `Deployer filter - a newer deployment by a different user is skipped and the matching user's deploy is chosen`() {
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(
                FakeAppEngineLister.deploy(PROJECT_ID, "web", "someone-else", COLLECT_TIME.minusSeconds(10), "other@example.com"),
                FakeAppEngineLister.deploy(PROJECT_ID, "web", "mine", COLLECT_TIME.minusSeconds(60), "deployer@example.com"),
            ),
        )

        val output = useCase.execute(input())

        assertEquals("mine", assertIs<CreateDeploymentLinkUseCase.Output.Created>(output).deploymentLink.versionId)
    }

    @Test
    fun `Deployer filter - deployments with unknown creator are never matched`() {
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(FakeAppEngineLister.deploy(PROJECT_ID, "web", "v1", COLLECT_TIME.minusSeconds(60), null)),
        )

        val output = useCase.execute(input())

        assertIs<CreateDeploymentLinkUseCase.Output.NotLinked>(output)
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)
        assertTrue(fakeStorageCleaner.deletedFolders.contains(input().directoryName))
    }

    @Test
    fun `Time direction - a deployment created after the collect timestamp is never matched`() {
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(
                FakeAppEngineLister.deploy(PROJECT_ID, "web", "later", COLLECT_TIME.plusSeconds(60), "deployer@example.com"),
            ),
        )

        val output = useCase.execute(input())

        assertIs<CreateDeploymentLinkUseCase.Output.NotLinked>(output)
        assertTrue(fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.GAE).isEmpty())
    }

    @Test
    fun `Window - a deployment exactly 15 minutes before the collect time matches`() {
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(
                FakeAppEngineLister.deploy(PROJECT_ID, "web", "v1", COLLECT_TIME.minus(window), "deployer@example.com"),
            ),
        )

        val output = useCase.execute(input())

        assertIs<CreateDeploymentLinkUseCase.Output.Created>(output)
    }

    @Test
    fun `Window - a deployment just outside the 15 minute window is rejected`() {
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(
                FakeAppEngineLister.deploy(
                    projectId = PROJECT_ID,
                    serviceId = "web",
                    versionId = "old",
                    createTime = COLLECT_TIME.minus(window).minusSeconds(1),
                    createdBy = "deployer@example.com",
                ),
            ),
        )

        val output = useCase.execute(input())

        assertIs<CreateDeploymentLinkUseCase.Output.NotLinked>(output)
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)
        assertEquals(listOf(input().directoryName), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `Already linked - the same deploy's existing link is returned untouched and the old storage folder is kept`() {
        val createTime = COLLECT_TIME.minusSeconds(60)
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(FakeAppEngineLister.deploy(PROJECT_ID, "web", "v1", createTime, "deployer@example.com")),
        )
        val existingLink = domain.DeploymentLink(
            projectId = PROJECT_ID,
            deploymentType = DeploymentType.GAE,
            serviceId = "web",
            versionId = "v1",
            location = null,
            storageFolder = "uploads/old-folder",
            userEmail = "deployer@example.com",
            collectTimestamp = COLLECT_TIME.minusSeconds(120),
            deploymentTimestamp = createTime, // the very same deployment the listing reports
        )
        fakeDeploymentLinkRepository.save(existingLink)

        val output = useCase.execute(input())

        val alreadyLinked = assertIs<CreateDeploymentLinkUseCase.Output.AlreadyLinked>(output)
        assertEquals(existingLink, alreadyLinked.existing)
        assertEquals("uploads/old-folder", fakeDeploymentLinkRepository.get(PROJECT_ID, DeploymentType.GAE, null, "web", "v1")?.storageFolder)
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
        assertTrue(fakeRetryScheduler.scheduledTasks().isEmpty())
    }

    @Test
    fun `Redeploy - a new deployment with the same version id replaces the existing link and deletes the old upload's folder`() {
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(FakeAppEngineLister.deploy(PROJECT_ID, "web", "v1", COLLECT_TIME.minusSeconds(60), "deployer@example.com")),
        )
        fakeDeploymentLinkRepository.save(
            domain.DeploymentLink(
                projectId = PROJECT_ID,
                deploymentType = DeploymentType.GAE,
                serviceId = "web",
                versionId = "v1",
                location = null,
                storageFolder = "uploads/old-folder",
                userEmail = "deployer@example.com",
                collectTimestamp = COLLECT_TIME.minusSeconds(120),
                deploymentTimestamp = COLLECT_TIME.minusSeconds(600), // an older deployment with the same version id
            ),
        )

        val output = useCase.execute(input())

        val created = assertIs<CreateDeploymentLinkUseCase.Output.Created>(output)
        assertEquals(COLLECT_TIME.minusSeconds(60), created.deploymentLink.deploymentTimestamp)
        assertEquals(created.deploymentLink, fakeDeploymentLinkRepository.get(PROJECT_ID, DeploymentType.GAE, null, "web", "v1"))
        // Only the replaced link's storage folder is deleted; the new upload's folder is kept.
        assertEquals(listOf("uploads/old-folder"), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `Redeploy - a Cloud Run revision id that is used again replaces the link too`() {
        fakeCloudRunLister.seed(
            PROJECT_ID,
            listOf(
                FakeCloudRunLister.deploy(
                    projectId = PROJECT_ID,
                    location = "europe-west1",
                    serviceId = "web-api",
                    revisionId = "rev-7",
                    createTime = COLLECT_TIME.minusSeconds(60),
                    createdBy = "deployer@example.com",
                ),
            ),
        )
        fakeDeploymentLinkRepository.save(
            domain.DeploymentLink(
                projectId = PROJECT_ID,
                deploymentType = DeploymentType.RUN,
                serviceId = "web-api",
                versionId = "rev-7",
                location = "europe-west1",
                storageFolder = "uploads/old-folder",
                userEmail = "deployer@example.com",
                collectTimestamp = COLLECT_TIME.minusSeconds(120),
                deploymentTimestamp = COLLECT_TIME.minusSeconds(600),
            ),
        )

        val output = useCase.execute(input(deploymentType = DeploymentType.RUN))

        assertIs<CreateDeploymentLinkUseCase.Output.Created>(output)
        assertEquals(listOf("uploads/old-folder"), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `Unknown project - not linked and the upload is deleted after the retry`() {
        val output = useCase.execute(input(projects = listOf("not-configured")))

        assertIs<CreateDeploymentLinkUseCase.Output.UnknownProject>(output)
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)
        assertEquals(listOf(input().directoryName), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `No candidates - NotLinked and the upload is deleted exactly once after the retry`() {
        fakeAppEngineLister.seed(PROJECT_ID, emptyList())

        val output = useCase.execute(input())

        assertIs<CreateDeploymentLinkUseCase.Output.NotLinked>(output)
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)
        assertEquals(listOf(input().directoryName), fakeStorageCleaner.deletedFolders)
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)
        assertEquals(listOf(input().directoryName), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `Retry - the deployment appears between the first attempt and the retry, so the link is created and not deleted`() {
        fakeAppEngineLister.seed(PROJECT_ID, emptyList())

        val output = useCase.execute(input())

        assertIs<CreateDeploymentLinkUseCase.Output.NotLinked>(output)
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(FakeAppEngineLister.deploy(PROJECT_ID, "web", "late", COLLECT_TIME.minusSeconds(60), "deployer@example.com")),
        )
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)

        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
        assertEquals(1, fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.GAE).size)
    }

    @Test
    fun `Retry - the retry still finds nothing, so the upload is deleted once`() {
        fakeAppEngineLister.seed(PROJECT_ID, emptyList())

        useCase.execute(input())
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)

        assertEquals(listOf(input().directoryName), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `Retry - a transient listing failure on the first attempt, success on the retry, so the link is created`() {
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(FakeAppEngineLister.deploy(PROJECT_ID, "web", "v1", COLLECT_TIME.minusSeconds(60), "deployer@example.com")),
        )
        fakeAppEngineLister.throwOnNextList()

        val output = useCase.execute(input())

        assertIs<CreateDeploymentLinkUseCase.Output.NotLinked>(output)
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)

        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
        assertEquals(1, fakeDeploymentLinkRepository.getAllFor(PROJECT_ID, DeploymentType.GAE).size)
    }

    @Test
    fun `Retry - a listing failure on the retry too ends with the upload deleted`() {
        fakeAppEngineLister.throwOnEveryList = true

        val output = useCase.execute(input())

        assertIs<CreateDeploymentLinkUseCase.Output.NotLinked>(output)
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)

        assertEquals(listOf(input().directoryName), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `Multi-project - the winner is chosen globally across projects, not per project`() {
        fakeProjectRepository.save(Project(projectId = OTHER_PROJECT_ID, group = null, serviceAccount = "sa@other.iam.gserviceaccount.com"))
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(FakeAppEngineLister.deploy(PROJECT_ID, "web", "older", COLLECT_TIME.minusSeconds(600), "deployer@example.com")),
        )
        fakeAppEngineLister.seed(
            OTHER_PROJECT_ID,
            listOf(FakeAppEngineLister.deploy(OTHER_PROJECT_ID, "web", "closest", COLLECT_TIME.minusSeconds(10), "deployer@example.com")),
        )

        val output = useCase.execute(input(projects = listOf(PROJECT_ID, OTHER_PROJECT_ID)))

        val created = assertIs<CreateDeploymentLinkUseCase.Output.Created>(output)
        assertEquals(OTHER_PROJECT_ID, created.deploymentLink.projectId)
        assertEquals("closest", created.deploymentLink.versionId)
        assertEquals(
            created.deploymentLink,
            fakeDeploymentLinkRepository.get(OTHER_PROJECT_ID, DeploymentType.GAE, null, "web", "closest"),
        )
    }

    @Test
    fun `Multi-project - a mix of configured and unknown projects still matches in the configured ones`() {
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(FakeAppEngineLister.deploy(PROJECT_ID, "web", "v1", COLLECT_TIME.minusSeconds(60), "deployer@example.com")),
        )

        val output = useCase.execute(input(projects = listOf("not-configured", PROJECT_ID)))

        assertIs<CreateDeploymentLinkUseCase.Output.Created>(output)
    }

    @Test
    fun `Multi-project - a deployment outside the window in one project is rejected even when listed`() {
        fakeProjectRepository.save(Project(projectId = OTHER_PROJECT_ID, group = null, serviceAccount = "sa@other.iam.gserviceaccount.com"))
        fakeAppEngineLister.seed(
            OTHER_PROJECT_ID,
            listOf(FakeAppEngineLister.deploy(OTHER_PROJECT_ID, "web", "old", COLLECT_TIME.minus(window).minusSeconds(1), "deployer@example.com")),
        )

        val output = useCase.execute(input(projects = listOf(OTHER_PROJECT_ID)))

        assertIs<CreateDeploymentLinkUseCase.Output.NotLinked>(output)
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)
        assertEquals(listOf(input().directoryName), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `Directory name - the collection timestamp is parsed from the directory name and forwarded to the linking`() {
        fakeAppEngineLister.seed(
            PROJECT_ID,
            listOf(FakeAppEngineLister.deploy(PROJECT_ID, "web", "v1", COLLECT_TIME.minusSeconds(60), "deployer@example.com")),
        )

        val output = useCase.execute(input())

        assertEquals(COLLECT_TIME, assertIs<CreateDeploymentLinkUseCase.Output.Created>(output).deploymentLink.collectTimestamp)
    }

    @Test
    fun `Directory name - a name with a mismatched deployment type is rejected`() {
        assertFailsWith<InvalidDirectoryNameException> {
            useCase.execute(input(directoryName = "deployer_RUN_${COLLECT_TIME.toEpochMilli()}"))
        }
    }

    @Test
    fun `Directory name - a name with a non-numeric timestamp is rejected`() {
        assertFailsWith<InvalidDirectoryNameException> {
            useCase.execute(input(directoryName = "deployer_GAE_not-a-number"))
        }
    }

    @Test
    fun `Directory name - a name that is too short is rejected`() {
        assertFailsWith<InvalidDirectoryNameException> {
            useCase.execute(input(directoryName = "only-two_parts"))
        }
    }

    @Test
    fun `Directory name - a name whose user part has an invalid character is rejected`() {
        assertFailsWith<InvalidDirectoryNameException> {
            useCase.execute(input(directoryName = "depl oy er_GAE_${COLLECT_TIME.toEpochMilli()}"))
        }
    }
}
