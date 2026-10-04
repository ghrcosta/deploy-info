package tests.application.settings

import application.settings.AddProjectUseCase
import domain.Project
import tests.fakes.FakeProjectRepository
import kotlin.test.*


class AddProjectUseCaseTests {

    private lateinit var fakeProjectRepository: FakeProjectRepository

    private lateinit var addProjectUseCase: AddProjectUseCase

    @BeforeTest
    fun setup() {
        fakeProjectRepository = FakeProjectRepository()

        addProjectUseCase = AddProjectUseCase(fakeProjectRepository)
    }


    @Test
    fun `Add project without issues`() {
        val newProject1 = Project(name = "testProject1", group = "test", serviceAccount = "test@account.com")
        val output = addProjectUseCase.execute(newProject1)
        assertFalse(output.issuesFound())
        assertEquals(1, output.projectsInRepository.size)
    }

    @Test
    fun `Notify issue when adding project with same name twice`() {
        val newProject1 = Project(name = "testProject", group = "test", serviceAccount = "test@account.com")
        fakeProjectRepository.save(newProject1)

        val newProject2 = Project(name = "testProject", group = "test2", serviceAccount = "test2@account.com")
        val output = addProjectUseCase.execute(newProject2)
        assertTrue(output.issuesFound())
        assertTrue(output.issueNameConflict)
        assert(output.projectsInRepository.isEmpty())
    }

    @Test
    fun `Do not report service account issue while validation is not implemented`() {
        // TODO: update when Phase 2 (Settings hardening) implements the service-account validation.
        val newProject1 = Project(name = "testProject1", group = "test", serviceAccount = "test@account.com")

        val output = addProjectUseCase.execute(newProject1)
        assertFalse(output.issuesFound())
        assertFalse(output.issueServiceAccountError)
        assertEquals(1, output.projectsInRepository.size)
    }
}