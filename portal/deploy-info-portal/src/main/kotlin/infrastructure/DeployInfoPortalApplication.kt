package infrastructure

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import java.util.logging.Logger

@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan(basePackages = ["infrastructure", "ui"])
class DeployInfoPortalApplication

fun main(args: Array<String>) {
	val logger = Logger.getLogger(DeployInfoPortalApplication::class.java.name)

	runApplication<DeployInfoPortalApplication>(*args) {
		// https://cloud.google.com/appengine/docs/standard/java-gen2/runtime#Environment_variables
		val isRunningOnGCP = System.getenv("GAE_DEPLOYMENT_ID") != null

		val profile = if (isRunningOnGCP) "prod" else "local"
		logger.info("isRunningOnGCP=${isRunningOnGCP}, profile=${profile}")

		this.setAdditionalProfiles(profile)
	}
}
