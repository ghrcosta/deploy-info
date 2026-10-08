package tests.application.collector

import application.collector.GetStorageBucketUseCase
import infrastructure.config.DeployInfoProperties
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class GetStorageBucketUseCaseTests {

    private fun useCaseWithBucket(storageBucket: String): GetStorageBucketUseCase {
        val properties = DeployInfoProperties(
            storageBucket = storageBucket,
            linking = DeployInfoProperties.Linking(window = Duration.ofMinutes(15)),
            cleanup = DeployInfoProperties.Cleanup(
                enabled = false,
                interval = Duration.ofMinutes(10),
                gracePeriod = Duration.ofMinutes(10),
            ),
            cors = DeployInfoProperties.Cors(allowedOrigins = listOf("http://localhost:4200")),
        )
        return GetStorageBucketUseCase(properties)
    }

    @Test
    fun `Return the configured bucket`() {
        val output = useCaseWithBucket("deploy-info-uploads-test").execute()
        assertEquals(GetStorageBucketUseCase.Output.Bucket("deploy-info-uploads-test"), output)
    }

    @Test
    fun `Return NotConfigured when the bucket property is blank`() {
        val output = useCaseWithBucket(" ").execute()
        assertEquals(GetStorageBucketUseCase.Output.NotConfigured, output)
    }
}