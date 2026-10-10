package tests.application.settings

import application.settings.DeleteProjectUseCase
import domain.Project
import tests.fakes.FakeProjectRepository
import kotlin.test.*


class DeleteProjectUseCaseTests {

    private lateinit var fakeProjectRepository: FakeProjectRepository

    private lateinit var deleteProjectUseCase: DeleteProjectUseCase

    @BeforeTest
    fun setup() {
        fakeProjectRepository = FakeProjectRepository()

        deleteProjectUseCase = DeleteProjectUseCase(fakeProjectRepository)
    }

    @Test
    fun `Delete project without issues`() {
        val project = Project(projectId = "testProject1", group = "test", serviceAccount = "test@account.com")
        fakeProjectRepository.save(project)

        val output = deleteProjectUseCase.execute(project.projectId)
        assertNotNull(output.projectsInRepository)
        assertTrue(output.projectsInRepository.isEmpty())
    }

    @Test
    fun `Ignore when trying to delete project that does not exist`() {
        val project = Project(projectId = "testProject", group = "test", serviceAccount = "test@account.com")
        fakeProjectRepository.save(project)

        val output = deleteProjectUseCase.execute("wrong-name")
        assertEquals(1, output.projectsInRepository.size)
    }

    @Test
    fun `Ignore when trying to delete project with repository empty`() {
        val output = deleteProjectUseCase.execute("wrong-name")
        assertNotNull(output.projectsInRepository)
        assertTrue(output.projectsInRepository.isEmpty())
    }
}