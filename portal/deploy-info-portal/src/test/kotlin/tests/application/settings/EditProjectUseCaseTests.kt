package tests.application.settings

import application.ServiceAccountIssue
import application.settings.EditProjectUseCase
import domain.Project
import tests.fakes.FakeProjectRepository
import tests.fakes.FakeServiceAccountValidator
import kotlin.test.*


class EditProjectUseCaseTests {

    private lateinit var fakeProjectRepository: FakeProjectRepository
    private lateinit var fakeServiceAccountValidator: FakeServiceAccountValidator

    private lateinit var editProjectUseCase: EditProjectUseCase

    @BeforeTest
    fun setup() {
        fakeProjectRepository = FakeProjectRepository()
        fakeServiceAccountValidator = FakeServiceAccountValidator()

        editProjectUseCase = EditProjectUseCase(fakeProjectRepository, fakeServiceAccountValidator)
    }

    @Test
    fun `Edit project without issues`() {
        val project = Project(name = "testProject1", group = "test", serviceAccount = "test@account.com")
        fakeProjectRepository.save(project)

        val modifiedProject = Project(name = "testProject1", group = "test2", serviceAccount = "test2@account.com")
        val output = editProjectUseCase.execute(modifiedProject)
        assertFalse(output.issuesFound())
        assertFalse(output.issueServiceAccountError)
        assertEquals(ServiceAccountIssue.NONE, output.serviceAccountIssue)
        assertEquals(1, output.projectsInRepository.size)
        // The changed service account was validated before the save.
        assertEquals(listOf(modifiedProject), fakeServiceAccountValidator.validatedProjects)

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
        // There is no project whose service account could be validated.
        assert(fakeServiceAccountValidator.validatedProjects.isEmpty())
    }

    @Test
    fun `Do not validate the service account when it did not change`() {
        val project = Project(name = "testProject1", group = "test", serviceAccount = "test@account.com")
        fakeProjectRepository.save(project)

        val modifiedProject = Project(name = "testProject1", group = "test2", serviceAccount = "test@account.com")
        val output = editProjectUseCase.execute(modifiedProject)
        assertFalse(output.issuesFound())
        assertEquals(1, output.projectsInRepository.size)
        assert(fakeServiceAccountValidator.validatedProjects.isEmpty())
    }

    @Test
    fun `Skip the save and report the issue when the changed service account cannot be validated`() {
        fakeServiceAccountValidator.issue = ServiceAccountIssue.MISSING_LISTING_PERMISSION
        val project = Project(name = "testProject1", group = "test", serviceAccount = "test@account.com")
        fakeProjectRepository.save(project)

        val modifiedProject = Project(name = "testProject1", group = "test", serviceAccount = "test2@account.com")
        val output = editProjectUseCase.execute(modifiedProject)

        assertTrue(output.issuesFound())
        assertTrue(output.issueServiceAccountError)
        assertEquals(ServiceAccountIssue.MISSING_LISTING_PERMISSION, output.serviceAccountIssue)
        assert(output.projectsInRepository.isEmpty())
        // The project keeps its previously validated service account.
        assertEquals("test@account.com", fakeProjectRepository.get(project.name)?.serviceAccount)
    }
}
