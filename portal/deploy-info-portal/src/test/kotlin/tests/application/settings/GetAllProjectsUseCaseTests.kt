package tests.application.settings

import application.settings.GetAllProjectsUseCase
import domain.Project
import tests.fakes.FakeProjectRepository
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class GetAllProjectsUseCaseTests {

    private lateinit var fakeProjectRepository: FakeProjectRepository

    private lateinit var getAllProjectsUseCase: GetAllProjectsUseCase

    @BeforeTest
    fun setup() {
        fakeProjectRepository = FakeProjectRepository()

        getAllProjectsUseCase = GetAllProjectsUseCase(fakeProjectRepository)
    }

    @Test
    fun `Return empty list when repository is empty`() {
        val output = getAllProjectsUseCase.execute()
        assertTrue(output.projectsInRepository.isEmpty())
    }

    @Test
    fun `Return all projects from the repository`() {
        val newProject1 = Project(projectId = "testProject1", group = "test", serviceAccount = "test@account.com")
        val newProject2 = Project(projectId = "testProject2", group = "test", serviceAccount = "test@account.com")
        val newProject3 = Project(projectId = "testProject3", group = "test", serviceAccount = "test@account.com")
        fakeProjectRepository.save(newProject1)
        fakeProjectRepository.save(newProject2)
        fakeProjectRepository.save(newProject3)

        val output = getAllProjectsUseCase.execute()
        assertEquals(3, output.projectsInRepository.size)
        assertEquals(newProject1, output.projectsInRepository[0])
        assertEquals(newProject2, output.projectsInRepository[1])
        assertEquals(newProject3, output.projectsInRepository[2])
    }
}