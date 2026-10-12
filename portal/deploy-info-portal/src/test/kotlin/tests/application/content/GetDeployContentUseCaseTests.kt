package tests.application.content

import application.content.GetDeployContentUseCase
import domain.ContentFile
import domain.GitFileStatus
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
        fakeFileContentReader.seed(
            folder,
            "uuid-git.csv",
            "uuid,filepath,status\ngit-uuid-1,/src/main.kt,modified\ngit-uuid-2,/src/new-file.kt,untracked\n",
        )
        fakeFileContentReader.seed(folder, "git-uuid-1", "diff --git a/src/main.kt")
        fakeFileContentReader.seed(folder, "git-uuid-2", "full file content")
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
        assertEquals(
            listOf(
                ContentFile("/src/main.kt", "diff --git a/src/main.kt", status = GitFileStatus.MODIFIED),
                ContentFile("/src/new-file.kt", "full file content", status = GitFileStatus.UNTRACKED),
            ),
            git.changes,
        )
        assertEquals(listOf(ContentFile("/docs/readme.md", "# readme", status = GitFileStatus.MODIFIED)), content.extras)
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
    fun `Csv filepaths containing commas or quotes - RFC 4180 quoting is parsed correctly`() {
        fakeFileContentReader.seed(
            folder,
            "uuid-git.csv",
            "uuid,filepath,status\ngit-uuid-1,\"/a,comma.kt\",new\ngit-uuid-2,\"/say \"\"hi\"\".kt\",modified\ngit-uuid-3,\"/new,comma.kt\",untracked\n",
        )
        fakeFileContentReader.seed(folder, "git-uuid-1", "content a")
        fakeFileContentReader.seed(folder, "git-uuid-2", "content b")
        fakeFileContentReader.seed(folder, "git-uuid-3", "content c")

        val output = execute()

        val git = assertIs<GetDeployContentUseCase.Output.Content>(output).deployContent.git
        assertEquals(
            listOf(
                ContentFile("/a,comma.kt", "content a", status = GitFileStatus.NEW),
                ContentFile("/say \"hi\".kt", "content b", status = GitFileStatus.MODIFIED),
                ContentFile("/new,comma.kt", "content c", status = GitFileStatus.UNTRACKED),
            ),
            git?.changes,
        )
    }

    @Test
    fun `Csv rows with an unknown status are skipped`() {
        fakeFileContentReader.seed(
            folder,
            "uuid-git.csv",
            "uuid,filepath,status\ngit-uuid-1,/a.kt,renamed\ngit-uuid-2,/b.kt,modified\n",
        )
        fakeFileContentReader.seed(folder, "git-uuid-1", "content a")
        fakeFileContentReader.seed(folder, "git-uuid-2", "content b")

        val output = execute()

        val git = assertIs<GetDeployContentUseCase.Output.Content>(output).deployContent.git
        assertEquals(listOf(ContentFile("/b.kt", "content b", status = GitFileStatus.MODIFIED)), git?.changes)
    }

    @Test
    fun `Uuid map entry referencing a missing file - the entry is skipped, the rest is returned`() {
        fakeFileContentReader.seed(folder, "git-log.txt", "log output")
        fakeFileContentReader.seed(folder, "uuid-git.csv", "uuid,filepath,status\ngit-uuid-1,/a.kt,modified\ngit-uuid-2,/b.kt,new\n")
        fakeFileContentReader.seed(folder, "git-uuid-1", "diff a")

        val output = execute()

        val git = assertIs<GetDeployContentUseCase.Output.Content>(output).deployContent.git
        assertEquals(listOf(ContentFile("/a.kt", "diff a", status = GitFileStatus.MODIFIED)), git?.changes)
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
