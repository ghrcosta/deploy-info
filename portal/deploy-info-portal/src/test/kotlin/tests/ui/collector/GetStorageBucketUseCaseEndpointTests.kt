package tests.ui.collector

import application.collector.GetStorageBucketUseCase
import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import infrastructure.DeployInfoPortalApplication
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import tools.jackson.core.type.TypeReference
import tools.jackson.module.kotlin.jacksonObjectMapper
import ui.collector.BucketResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(classes = [DeployInfoPortalApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GetStorageBucketUseCaseEndpointTests {

    @Autowired
    private lateinit var mvc: MockMvc

    @MockitoBean
    private lateinit var datastoreTemplate: DatastoreTemplate

    @MockitoBean
    private lateinit var useCase: GetStorageBucketUseCase

    private val uri = "/collector/bucket"

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `Happy path returns the configured bucket`() {
        whenever(useCase.execute()).thenReturn(GetStorageBucketUseCase.Output.Bucket("deploy-info-uploads-test"))

        val result = mvc.perform(get(uri)).andReturn()
        assertEquals(HttpStatus.OK.value(), result.response.status)

        val dto = objectMapper.readValue(result.response.contentAsString, object : TypeReference<BucketResponse>() {})
        assertEquals("deploy-info-uploads-test", dto.bucket)
    }

    @Test
    fun `Not configured returns internal server error`() {
        whenever(useCase.execute()).thenReturn(GetStorageBucketUseCase.Output.NotConfigured)

        val result = mvc.perform(get(uri)).andReturn()
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), result.response.status)
        assertTrue(result.response.contentAsString.isEmpty())
    }
}