package tests.application.content

import application.content.GetDeployContentUseCase
import domain.ContentFile
import org.junit.jupiter.api.Test
import tests.fakes.FakeFileContentReader
import kotlin.test.*

class GetDeployContentUseCaseTests {

    private val folder = "uploads/deployer_GAE_1735689600000"

    private lateinit var fakeFileContentReader: FakeFileContentReader
    private lateinit var useCase: GetDeployContentUseCase

    @BeforeTest
    fun setup() {
        fakeFileContentReader = FakeFileContentReader()
        useCase = GetDeployContentUseCase(fileContentReader = fakeFileContentReader)
    }

    private fun execute(folder: String = this.folder) =
        useCase.execute(GetDeployContentUseCase.Input(storageFolder = folder))

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
        seedFullFolder()

        val output = execute()

        val content = assertIs<GetDeployContentUseCase.Output.Content>(output).deployContent
        val git = assertNotNull(content.git)
        assertEquals("log output", git.gitLog)
        assertEquals("status output", git.gitStatus)
        assertEquals(listOf(ContentFile("/src/main.kt", "diff --git a/src/main.kt")), git.changes)
        assertEquals(listOf(ContentFile("/docs/readme.md", "# readme")), content.extras)
    }

    @Test
    fun `No git files - the git section is null`() {
        fakeFileContentReader.seed(folder, "uuid-extra.properties", "extra-uuid-1=/docs/readme.md\n")
        fakeFileContentReader.seed(folder, "extra-uuid-1", "# readme")

        val output = execute()

        val content = assertIs<GetDeployContentUseCase.Output.Content>(output).deployContent
        assertNull(content.git)
        assertEquals(1, content.extras.size)
    }

    @Test
    fun `Only git status collected - the git section carries the status but no log`() {
        fakeFileContentReader.seed(folder, "git-status.txt", "status output")

        val output = execute()

        val git = assertIs<GetDeployContentUseCase.Output.Content>(output).deployContent.git
        assertNotNull(git)
        assertNull(git.gitLog)
        assertEquals("status output", git.gitStatus)
        assertTrue(git.changes.isEmpty())
    }

    @Test
    fun `Uuid map entry referencing a missing file - the entry is skipped, the rest is returned`() {
        fakeFileContentReader.seed(folder, "git-log.txt", "log output")
        fakeFileContentReader.seed(folder, "uuid-git.properties", "git-uuid-1=/a.kt\ngit-uuid-2=/b.kt\n")
        fakeFileContentReader.seed(folder, "git-uuid-1", "diff a")

        val output = execute()

        val git = assertIs<GetDeployContentUseCase.Output.Content>(output).deployContent.git
        assertEquals(listOf(ContentFile("/a.kt", "diff a")), git?.changes)
    }

    @Test
    fun `Absent folder - FolderNotFound`() {
        val output = execute()

        assertEquals(GetDeployContentUseCase.Output.FolderNotFound, output)
    }

    @Test
    fun `Reader failure - StorageError`() {
        fakeFileContentReader.seed(folder, "git-log.txt", "log output")
        fakeFileContentReader.failAllReads()

        val output = execute()

        assertEquals(GetDeployContentUseCase.Output.StorageError, output)
    }
}
