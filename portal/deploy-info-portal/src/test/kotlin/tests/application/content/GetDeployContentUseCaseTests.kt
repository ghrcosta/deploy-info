package tests.application.content

import application.content.GetDeployContentUseCase
import domain.ContentFile
import domain.DeployLink
import domain.DeployType
import org.junit.jupiter.api.Test
import tests.fakes.FakeDeployLinkRepository
import tests.fakes.FakeFileContentReader
import kotlin.test.*

private const val PROJECT_NAME = "testProject"

private fun input(
    deployType: DeployType = DeployType.GAE,
    location: String? = null,
) = GetDeployContentUseCase.Input(
    projectName = PROJECT_NAME,
    deployType = deployType,
    location = location,
    serviceId = "web",
    versionId = "v42",
)

class GetDeployContentUseCaseTests {

    private val folder = "uploads/deployer_2026-01-01T00-00-00Z"

    private lateinit var fakeDeployLinkRepository: FakeDeployLinkRepository
    private lateinit var fakeFileContentReader: FakeFileContentReader
    private lateinit var useCase: GetDeployContentUseCase

    @BeforeTest
    fun setup() {
        fakeDeployLinkRepository = FakeDeployLinkRepository()
        fakeFileContentReader = FakeFileContentReader()
        useCase = GetDeployContentUseCase(
            deployLinkRepository = fakeDeployLinkRepository,
            fileContentReader = fakeFileContentReader,
        )
    }

    private fun seedLink(deployType: DeployType = DeployType.GAE, location: String? = null) {
        fakeDeployLinkRepository.save(
            DeployLink(
                projectName = PROJECT_NAME,
                deployType = deployType,
                serviceId = "web",
                versionId = "v42",
                location = location,
                storageFolder = folder,
                userEmail = "deployer@example.com",
                collectTimestamp = java.time.Instant.ofEpochSecond(1_000_000),
            ),
        )
    }

    private fun seedFullFolder() {
        fakeFileContentReader.seed(folder, "git-log.txt", "log output")
        fakeFileContentReader.seed(folder, "git-status.txt", "status output")
        fakeFileContentReader.seed(folder, "uuid-git.properties", "git-uuid-1=/src/main.kt\n")
        fakeFileContentReader.seed(folder, "git-uuid-1", "diff --git a/src/main.kt")
        fakeFileContentReader.seed(folder, "uuid-extra.properties", "extra-uuid-1=/docs/readme.md\n")
        fakeFileContentReader.seed(folder, "extra-uuid-1", "# readme")
    }

    @Test
    fun `Full folder - git section and extras are returned with paths from the uuid maps`() {
        seedLink()
        seedFullFolder()

        val output = useCase.execute(input())

        val content = assertIs<GetDeployContentUseCase.Output.Content>(output).deployContent
        val git = assertNotNull(content.git)
        assertEquals("log output", git.gitLog)
        assertEquals("status output", git.gitStatus)
        assertEquals(listOf(ContentFile("/src/main.kt", "diff --git a/src/main.kt")), git.changes)
        assertEquals(listOf(ContentFile("/docs/readme.md", "# readme")), content.extras)
    }

    @Test
    fun `Cloud Run identity - the link is looked up with the location`() {
        seedLink(DeployType.RUN, location = "europe-west1")
        seedFullFolder()

        val output = useCase.execute(input(deployType = DeployType.RUN, location = "europe-west1"))

        assertIs<GetDeployContentUseCase.Output.Content>(output)
    }

    @Test
    fun `No git files - the git section is null`() {
        seedLink()
        fakeFileContentReader.seed(folder, "uuid-extra.properties", "extra-uuid-1=/docs/readme.md\n")
        fakeFileContentReader.seed(folder, "extra-uuid-1", "# readme")

        val output = useCase.execute(input())

        val content = assertIs<GetDeployContentUseCase.Output.Content>(output).deployContent
        assertNull(content.git)
        assertEquals(1, content.extras.size)
    }

    @Test
    fun `Empty folder - git is null and extras are empty`() {
        seedLink()

        val output = useCase.execute(input())

        val content = assertIs<GetDeployContentUseCase.Output.Content>(output).deployContent
        assertNull(content.git)
        assertTrue(content.extras.isEmpty())
    }

    @Test
    fun `Only git status collected - the git section carries the status but no log`() {
        seedLink()
        fakeFileContentReader.seed(folder, "git-status.txt", "status output")

        val output = useCase.execute(input())

        val git = assertIs<GetDeployContentUseCase.Output.Content>(output).deployContent.git
        assertNotNull(git)
        assertNull(git.gitLog)
        assertEquals("status output", git.gitStatus)
        assertTrue(git.changes.isEmpty())
    }

    @Test
    fun `Uuid map entry referencing a missing file - the entry is skipped, the rest is returned`() {
        seedLink()
        fakeFileContentReader.seed(folder, "git-log.txt", "log output")
        fakeFileContentReader.seed(folder, "uuid-git.properties", "git-uuid-1=/a.kt\ngit-uuid-2=/b.kt\n")
        fakeFileContentReader.seed(folder, "git-uuid-1", "diff a")

        val output = useCase.execute(input())

        val git = assertIs<GetDeployContentUseCase.Output.Content>(output).deployContent.git
        assertEquals(listOf(ContentFile("/a.kt", "diff a")), git?.changes)
    }

    @Test
    fun `Unknown link - LinkNotFound`() {
        val output = useCase.execute(input())

        assertEquals(GetDeployContentUseCase.Output.LinkNotFound, output)
    }

    @Test
    fun `Reader failure - StorageError`() {
        seedLink()
        fakeFileContentReader.failAllReads()

        val output = useCase.execute(input())

        assertEquals(GetDeployContentUseCase.Output.StorageError, output)
    }
}