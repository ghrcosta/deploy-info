package tests.application.settings

import application.ServiceAccountIssue
import application.settings.AddProjectUseCase
import domain.Project
import tests.fakes.FakeProjectRepository
import tests.fakes.FakeServiceAccountValidator
import kotlin.test.*


class AddProjectUseCaseTests {

    private lateinit var fakeProjectRepository: FakeProjectRepository
    private lateinit var fakeServiceAccountValidator: FakeServiceAccountValidator

    private lateinit var addProjectUseCase: AddProjectUseCase

    @BeforeTest
    fun setup() {
        fakeProjectRepository = FakeProjectRepository()
        fakeServiceAccountValidator = FakeServiceAccountValidator()

        addProjectUseCase = AddProjectUseCase(fakeProjectRepository, fakeServiceAccountValidator)
    }


    @Test
    fun `Add project without issues`() {
        val newProject1 = Project(projectId = "testProject1", group = "test", serviceAccount = "test@account.com")
        val output = addProjectUseCase.execute(newProject1)
        assertFalse(output.issuesFound())
        assertFalse(output.issueServiceAccountError)
        assertEquals(ServiceAccountIssue.NONE, output.serviceAccountIssue)
        assertEquals(1, output.projectsInRepository.size)
        // The new project's service account was validated before the save.
        assertEquals(listOf(newProject1), fakeServiceAccountValidator.validatedProjects)
    }

    @Test
    fun `Notify issue when adding project with same name twice`() {
        val newProject1 = Project(projectId = "testProject", group = "test", serviceAccount = "test@account.com")
        fakeProjectRepository.save(newProject1)

        val newProject2 = Project(projectId = "testProject", group = "test2", serviceAccount = "test2@account.com")
        val output = addProjectUseCase.execute(newProject2)
        assertTrue(output.issuesFound())
        assertTrue(output.issueProjectIdConflict)
        assert(output.projectsInRepository.isEmpty())
        // The name conflict is reported without even trying the service account.
        assert(fakeServiceAccountValidator.validatedProjects.isEmpty())
    }

    @Test
    fun `Skip the save and report the issue when the service account cannot be validated`() {
        fakeServiceAccountValidator.issue = ServiceAccountIssue.MISSING_IMPERSONATION_PERMISSION
        val newProject1 = Project(projectId = "testProject1", group = "test", serviceAccount = "test@account.com")

        val output = addProjectUseCase.execute(newProject1)

        assertTrue(output.issuesFound())
        assertTrue(output.issueServiceAccountError)
        assertEquals(ServiceAccountIssue.MISSING_IMPERSONATION_PERMISSION, output.serviceAccountIssue)
        assert(output.projectsInRepository.isEmpty())
        assert(fakeProjectRepository.getAll().isEmpty())
    }
}
