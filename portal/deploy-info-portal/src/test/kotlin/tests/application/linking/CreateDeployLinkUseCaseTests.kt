package tests.application.linking

import application.linking.CreateDeployLinkUseCase
import domain.DeployType
import domain.Project
import org.junit.jupiter.api.Test
import tests.fakes.*
import java.time.Duration
import java.time.Instant
import kotlin.test.*

private const val PROJECT_NAME = "testProject"
private val COLLECT_TIME: Instant = Instant.ofEpochSecond(1_000_000)
private val RETRY_DELAY_TOLERANCE: Duration = Duration.ofHours(1)

private fun input(
    deployType: DeployType = DeployType.GAE,
    userEmail: String = "deployer@example.com",
    collectTimestamp: Instant = COLLECT_TIME,
) = CreateDeployLinkUseCase.Input(
    projectName = PROJECT_NAME,
    deployType = deployType,
    storageFolder = "uploads/2026-01-01T00-00-00Z",
    userEmail = userEmail,
    collectTimestamp = collectTimestamp,
)

class CreateDeployLinkUseCaseTests {

    private val window: Duration = Duration.ofMinutes(15)
    private val project = Project(name = PROJECT_NAME, group = null, serviceAccount = "sa@test.iam.gserviceaccount.com")

    private lateinit var fakeProjectRepository: FakeProjectRepository
    private lateinit var fakeAppEngineLister: FakeAppEngineLister
    private lateinit var fakeCloudRunLister: FakeCloudRunLister
    private lateinit var fakeDeployLinkRepository: FakeDeployLinkRepository
    private lateinit var fakeStorageCleaner: FakeStorageCleaner
    private lateinit var fakeRetryScheduler: FakeRetryScheduler
    private lateinit var useCase: CreateDeployLinkUseCase

    @BeforeTest
    fun setup() {
        fakeProjectRepository = FakeProjectRepository()
        fakeAppEngineLister = FakeAppEngineLister()
        fakeCloudRunLister = FakeCloudRunLister()
        fakeDeployLinkRepository = FakeDeployLinkRepository()
        fakeStorageCleaner = FakeStorageCleaner()
        fakeRetryScheduler = FakeRetryScheduler()
        fakeProjectRepository.save(project)
        useCase = CreateDeployLinkUseCase(
            projectRepository = fakeProjectRepository,
            appEngineLister = fakeAppEngineLister,
            cloudRunLister = fakeCloudRunLister,
            deployLinkRepository = fakeDeployLinkRepository,
            storageCleaner = fakeStorageCleaner,
            retryScheduler = fakeRetryScheduler,
            window = window,
        )
    }

    @Test
    fun `GAE - link created from the matching version with all collector fields stored correctly`() {
        fakeAppEngineLister.seed(
            PROJECT_NAME,
            listOf(
                FakeAppEngineLister.deploy(
                    projectId = PROJECT_NAME,
                    serviceId = "web",
                    versionId = "v42",
                    createTime = COLLECT_TIME.minusSeconds(60),
                    createdBy = "Deployer@example.com", // case-insensitive match
                ),
            ),
        )

        val output = useCase.execute(input())

        val created = assertIs<CreateDeployLinkUseCase.Output.Created>(output)
        assertEquals("web", created.deployLink.serviceId)
        assertEquals("v42", created.deployLink.versionId)
        assertNull(created.deployLink.location)
        assertEquals("uploads/2026-01-01T00-00-00Z", created.deployLink.storageFolder)
        assertEquals("deployer@example.com", created.deployLink.userEmail)
        assertEquals(COLLECT_TIME, created.deployLink.collectTimestamp)
        assertEquals(created.deployLink, fakeDeployLinkRepository.get(PROJECT_NAME, DeployType.GAE, null, "web", "v42"))
        assertTrue(fakeRetryScheduler.scheduledTasks().isEmpty())
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
    }

    @Test
    fun `Cloud Run - link created including location`() {
        fakeCloudRunLister.seed(
            PROJECT_NAME,
            listOf(
                FakeCloudRunLister.deploy(
                    projectId = PROJECT_NAME,
                    location = "europe-west1",
                    serviceId = "web-api",
                    revisionId = "rev-7",
                    createTime = COLLECT_TIME.minusSeconds(60),
                    createdBy = "deployer@example.com",
                ),
            ),
        )

        val output = useCase.execute(input(deployType = DeployType.RUN))

        val created = assertIs<CreateDeployLinkUseCase.Output.Created>(output)
        assertEquals("europe-west1", created.deployLink.location)
        assertEquals("web-api", created.deployLink.serviceId)
        assertEquals("rev-7", created.deployLink.versionId)
        assertEquals(created.deployLink, fakeDeployLinkRepository.get(PROJECT_NAME, DeployType.RUN, "europe-west1", "web-api", "rev-7"))
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
    }

    @Test
    fun `Closest match - with two matching deploys before the collect time, the later one wins`() {
        fakeAppEngineLister.seed(
            PROJECT_NAME,
            listOf(
                FakeAppEngineLister.deploy(PROJECT_NAME, "web", "older", COLLECT_TIME.minusSeconds(600), "deployer@example.com"),
                FakeAppEngineLister.deploy(PROJECT_NAME, "web", "newer", COLLECT_TIME.minusSeconds(10), "deployer@example.com"),
            ),
        )

        val output = useCase.execute(input())

        val created = assertIs<CreateDeployLinkUseCase.Output.Created>(output)
        assertEquals("newer", created.deployLink.versionId)
    }

    @Test
    fun `Deployer filter - a newer deploy by a different user is skipped and the matching user's deploy is chosen`() {
        fakeAppEngineLister.seed(
            PROJECT_NAME,
            listOf(
                FakeAppEngineLister.deploy(PROJECT_NAME, "web", "someone-else", COLLECT_TIME.minusSeconds(10), "other@example.com"),
                FakeAppEngineLister.deploy(PROJECT_NAME, "web", "mine", COLLECT_TIME.minusSeconds(60), "deployer@example.com"),
            ),
        )

        val output = useCase.execute(input())

        assertEquals("mine", assertIs<CreateDeployLinkUseCase.Output.Created>(output).deployLink.versionId)
    }

    @Test
    fun `Deployer filter - deploys with unknown creator are never matched`() {
        fakeAppEngineLister.seed(
            PROJECT_NAME,
            listOf(FakeAppEngineLister.deploy(PROJECT_NAME, "web", "v1", COLLECT_TIME.minusSeconds(60), null)),
        )

        val output = useCase.execute(input())

        assertIs<CreateDeployLinkUseCase.Output.NotLinked>(output)
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)
        assertTrue(fakeStorageCleaner.deletedFolders.contains(input().storageFolder))
    }

    @Test
    fun `Time direction - a deploy created after the collect timestamp is never matched`() {
        fakeAppEngineLister.seed(
            PROJECT_NAME,
            listOf(
                FakeAppEngineLister.deploy(PROJECT_NAME, "web", "later", COLLECT_TIME.plusSeconds(60), "deployer@example.com"),
            ),
        )

        val output = useCase.execute(input())

        assertIs<CreateDeployLinkUseCase.Output.NotLinked>(output)
        assertTrue(fakeDeployLinkRepository.getAllFor(PROJECT_NAME, DeployType.GAE).isEmpty())
    }

    @Test
    fun `Window - a deploy exactly 15 minutes before the collect time matches`() {
        fakeAppEngineLister.seed(
            PROJECT_NAME,
            listOf(
                FakeAppEngineLister.deploy(PROJECT_NAME, "web", "v1", COLLECT_TIME.minus(window), "deployer@example.com"),
            ),
        )

        val output = useCase.execute(input())

        assertIs<CreateDeployLinkUseCase.Output.Created>(output)
    }

    @Test
    fun `Window - a deploy just outside the 15 minute window is rejected`() {
        fakeAppEngineLister.seed(
            PROJECT_NAME,
            listOf(
                FakeAppEngineLister.deploy(
                    projectId = PROJECT_NAME,
                    serviceId = "web",
                    versionId = "old",
                    createTime = COLLECT_TIME.minus(window).minusSeconds(1),
                    createdBy = "deployer@example.com",
                ),
            ),
        )

        val output = useCase.execute(input())

        assertIs<CreateDeployLinkUseCase.Output.NotLinked>(output)
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)
        assertEquals(listOf(input().storageFolder), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `Already linked - the existing link is returned untouched and the old storage folder is kept`() {
        val createTime = COLLECT_TIME.minusSeconds(60)
        fakeAppEngineLister.seed(
            PROJECT_NAME,
            listOf(FakeAppEngineLister.deploy(PROJECT_NAME, "web", "v1", createTime, "deployer@example.com")),
        )
        val existingLink = domain.DeployLink(
            projectName = PROJECT_NAME,
            deployType = DeployType.GAE,
            serviceId = "web",
            versionId = "v1",
            location = null,
            storageFolder = "uploads/old-folder",
            userEmail = "deployer@example.com",
            collectTimestamp = COLLECT_TIME.minusSeconds(120),
        )
        fakeDeployLinkRepository.save(existingLink)

        val output = useCase.execute(input())

        val alreadyLinked = assertIs<CreateDeployLinkUseCase.Output.AlreadyLinked>(output)
        assertEquals(existingLink, alreadyLinked.existing)
        assertEquals("uploads/old-folder", fakeDeployLinkRepository.get(PROJECT_NAME, DeployType.GAE, null, "web", "v1")?.storageFolder)
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
        assertTrue(fakeRetryScheduler.scheduledTasks().isEmpty())
    }

    @Test
    fun `Unknown project - not linked and the upload is deleted after the retry`() {
        val output = useCase.execute(input().copy(projectName = "not-configured"))

        assertIs<CreateDeployLinkUseCase.Output.UnknownProject>(output)
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)
        assertEquals(listOf(input().storageFolder), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `No candidates - NotLinked and the upload is deleted exactly once after the retry`() {
        fakeAppEngineLister.seed(PROJECT_NAME, emptyList())

        val output = useCase.execute(input())

        assertIs<CreateDeployLinkUseCase.Output.NotLinked>(output)
        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)
        assertEquals(listOf(input().storageFolder), fakeStorageCleaner.deletedFolders)
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)
        assertEquals(listOf(input().storageFolder), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `Retry - the deploy appears between the first attempt and the retry, so the link is created and not deleted`() {
        fakeAppEngineLister.seed(PROJECT_NAME, emptyList())

        val output = useCase.execute(input())

        assertIs<CreateDeployLinkUseCase.Output.NotLinked>(output)
        fakeAppEngineLister.seed(
            PROJECT_NAME,
            listOf(FakeAppEngineLister.deploy(PROJECT_NAME, "web", "late", COLLECT_TIME.minusSeconds(60), "deployer@example.com")),
        )
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)

        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
        assertEquals(1, fakeDeployLinkRepository.getAllFor(PROJECT_NAME, DeployType.GAE).size)
    }

    @Test
    fun `Retry - the retry still finds nothing, so the upload is deleted once`() {
        fakeAppEngineLister.seed(PROJECT_NAME, emptyList())

        useCase.execute(input())
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)

        assertEquals(listOf(input().storageFolder), fakeStorageCleaner.deletedFolders)
    }

    @Test
    fun `Retry - a transient listing failure on the first attempt, success on the retry, so the link is created`() {
        fakeAppEngineLister.seed(
            PROJECT_NAME,
            listOf(FakeAppEngineLister.deploy(PROJECT_NAME, "web", "v1", COLLECT_TIME.minusSeconds(60), "deployer@example.com")),
        )
        fakeAppEngineLister.throwOnNextList()

        val output = useCase.execute(input())

        assertIs<CreateDeployLinkUseCase.Output.NotLinked>(output)
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)

        assertTrue(fakeStorageCleaner.deletedFolders.isEmpty())
        assertEquals(1, fakeDeployLinkRepository.getAllFor(PROJECT_NAME, DeployType.GAE).size)
    }

    @Test
    fun `Retry - a listing failure on the retry too ends with the upload deleted`() {
        fakeAppEngineLister.throwOnEveryList = true

        val output = useCase.execute(input())

        assertIs<CreateDeployLinkUseCase.Output.NotLinked>(output)
        fakeRetryScheduler.advanceBy(RETRY_DELAY_TOLERANCE)

        assertEquals(listOf(input().storageFolder), fakeStorageCleaner.deletedFolders)
    }
}
