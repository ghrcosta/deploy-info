package tests.infrastructure

import infrastructure.DeployInfoPortalApplication
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(classes = [DeployInfoPortalApplication::class])
@ActiveProfiles("test")
class DeployInfoPortalApplicationTests {
	@Test
	fun contextLoads() {}
}