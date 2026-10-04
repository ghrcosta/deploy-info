package tests.infrastructure

import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import infrastructure.DeployInfoPortalApplication
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(classes = [DeployInfoPortalApplication::class])
@ActiveProfiles("test")
class DeployInfoPortalApplicationTests {

    @MockitoBean
    private lateinit var datastoreTemplate: DatastoreTemplate

    @Test
    fun contextLoads() {}
}