package application.collector

import infrastructure.config.DeployInfoProperties

/**
 * Returns the name of the Cloud Storage bucket the collector uploads go to, as configured in the
 * portal's `deploy-info.storage-bucket` property. Consumed by the collector-facing
 * `GET /collector/bucket` endpoint (see `documentation/api.md`).
 *
 * The application layer stays Spring-free; the bound properties object is constructor-injected.
 */
class GetStorageBucketUseCase(
    private val properties: DeployInfoProperties,
) {

    sealed class Output {
        /** The bucket is configured; uploads go to this Cloud Storage bucket. */
        data class Bucket(val name: String) : Output()

        /** The bucket is not configured (a server misconfiguration, not a user issue). */
        data object NotConfigured : Output()
    }

    fun execute(): Output {
        val storageBucket = properties.storageBucket
        return if (storageBucket.isBlank()) {
            Output.NotConfigured
        } else {
            Output.Bucket(storageBucket)
        }
    }
}