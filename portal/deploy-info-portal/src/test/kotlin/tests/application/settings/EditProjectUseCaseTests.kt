package tests.application.settings

import application.settings.EditProjectUseCase
import domain.Project
import tests.fakes.FakeProjectRepository
import kotlin.test.*


class EditProjectUseCaseTests {

    private lateinit var fakeProjectRepository: FakeProjectRepository

    private lateinit var editProjectUseCase: EditProjectUseCase

    @BeforeTest
    fun setup() {
        fakeProjectRepository = FakeProjectRepository()

        editProjectUseCase = EditProjectUseCase(fakeProjectRepository)
    }

    @Test
    fun `Edit project without issues`() {
        val project = Project(name = "testProject1", group = "test", serviceAccount = "test@account.com")
        fakeProjectRepository.save(project)

        val modifiedProject = Project(name = "testProject1", group = "test2", serviceAccount = "test2@account.com")
        val output = editProjectUseCase.execute(modifiedProject)
        assertFalse(output.issuesFound())
        assertEquals(1, output.projectsInRepository.size)

        val savedProject = fakeProjectRepository.get(project.name)
        assertNotNull(savedProject)
        assertEquals(modifiedProject.group, savedProject.group)
        assertEquals(modifiedProject.serviceAccount, savedProject.serviceAccount)
    }

    @Test
    fun `Notify issue when editing project that does not exist`() {
        val project = Project(name = "testProject", group = "test", serviceAccount = "test@account.com")

        val output = editProjectUseCase.execute(project)
        assertTrue(output.issuesFound())
        assertTrue(output.issueProjectNotFound)
        assert(output.projectsInRepository.isEmpty())
    }

    @Test
    fun `Do not report service account issue while validation is not implemented`() {
        // TODO: update when Phase 2 (Settings hardening) implements the service-account validation.
        val project = Project(name = "testProject1", group = "test", serviceAccount = "test@account.com")
        fakeProjectRepository.save(project)

        val modifiedProject = Project(name = "testProject1", group = "test", serviceAccount = "test2@account.com")
        val output = editProjectUseCase.execute(modifiedProject)
        assertFalse(output.issuesFound())
        assertFalse(output.issueServiceAccountError)
        assertEquals(1, output.projectsInRepository.size)
    }
}