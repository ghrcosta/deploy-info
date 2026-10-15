package tests.application.tree

import application.tree.GetDeploymentTreeUseCase
import domain.DeploymentLink
import domain.DeploymentType
import domain.Project
import org.junit.jupiter.api.Test
import tests.fakes.FakeDeploymentLinkRepository
import tests.fakes.FakeProjectRepository
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

private val TIME: Instant = Instant.ofEpochMilli(1_735_689_600_000)

private fun link(
    projectId: String,
    serviceId: String,
    versionId: String,
    deploymentType: DeploymentType = DeploymentType.GAE,
    location: String? = null,
) = DeploymentLink(
    projectId = projectId,
    deploymentType = deploymentType,
    serviceId = serviceId,
    versionId = versionId,
    location = location,
    storageFolder = "$projectId-folder",
    userEmail = "deployer@example.com",
    collectTimestamp = TIME,
    deploymentTimestamp = TIME,
)

private fun project(projectId: String, group: String? = null) =
    Project(projectId = projectId, group = group, serviceAccount = "sa@test.iam.gserviceaccount.com")

/** Navigator tree ordering (see GetDeploymentTreeUseCase): case-insensitive alphabetical everywhere,
 *  natural number-aware order for version names, ungrouped last, deployment type as service tiebreak. */
class GetDeploymentTreeUseCaseTests {

    private lateinit var fakeProjectRepository: FakeProjectRepository
    private lateinit var fakeDeploymentLinkRepository: FakeDeploymentLinkRepository
    private lateinit var useCase: GetDeploymentTreeUseCase

    @BeforeTest
    fun setup() {
        fakeProjectRepository = FakeProjectRepository()
        fakeDeploymentLinkRepository = FakeDeploymentLinkRepository()
        useCase = GetDeploymentTreeUseCase(
            projectRepository = fakeProjectRepository,
            deploymentLinkRepository = fakeDeploymentLinkRepository,
        )
    }

    @Test
    fun `Groups are sorted case-insensitively with the ungrouped group last`() {
        fakeProjectRepository.save(project("beta-app", group = "Zeta"))
        fakeProjectRepository.save(project("core-app", group = "alpha"))
        fakeProjectRepository.save(project("ops-app", group = "Misc"))
        fakeDeploymentLinkRepository.save(link("beta-app", "web", "v1"))
        fakeDeploymentLinkRepository.save(link("core-app", "web", "v1"))
        fakeDeploymentLinkRepository.save(link("ops-app", "web", "v1"))
        // A link whose project is no longer configured in the portal is ungrouped.
        fakeDeploymentLinkRepository.save(link("ghost-app", "web", "v1"))

        val tree = useCase.execute()

        assertEquals(listOf("alpha", "Misc", "Zeta", ""), tree.map { it.name })
    }

    @Test
    fun `Projects are sorted case-insensitively within their group`() {
        fakeProjectRepository.save(project("zeta", group = "g"))
        fakeProjectRepository.save(project("alpha", group = "g"))
        fakeProjectRepository.save(project("Beta", group = "g"))
        fakeDeploymentLinkRepository.save(link("zeta", "web", "v1"))
        fakeDeploymentLinkRepository.save(link("alpha", "web", "v1"))
        fakeDeploymentLinkRepository.save(link("Beta", "web", "v1"))

        val tree = useCase.execute()

        assertEquals(listOf("alpha", "Beta", "zeta"), tree.single().projects.map { it.projectId })
    }

    @Test
    fun `Services are sorted case-insensitively by name with the deployment type as tiebreak`() {
        fakeProjectRepository.save(project("p"))
        fakeDeploymentLinkRepository.save(link("p", "auth", "v1"))
        fakeDeploymentLinkRepository.save(link("p", "API", "v1"))
        fakeDeploymentLinkRepository.save(link("p", "api", "r1", deploymentType = DeploymentType.RUN, location = "us-central1"))

        val tree = useCase.execute()

        assertEquals(
            listOf("API" to DeploymentType.GAE, "api" to DeploymentType.RUN, "auth" to DeploymentType.GAE),
            tree.single().projects.single().services.map { it.name to it.type },
        )
    }

    @Test
    fun `Versions are sorted naturally with embedded numbers compared numerically`() {
        fakeProjectRepository.save(project("p"))
        for (version in listOf("v10", "v2", "v1.10", "v1.9", "V3", "v02"))
            fakeDeploymentLinkRepository.save(link("p", "web", version))

        val tree = useCase.execute()

        assertEquals(
            listOf("v1.9", "v1.10", "v02", "v2", "V3", "v10"),
            tree.single().projects.single().services.single().versions.map { it.name },
        )
    }
}
