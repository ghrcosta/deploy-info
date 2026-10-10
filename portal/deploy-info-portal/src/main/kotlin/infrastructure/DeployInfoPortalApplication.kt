package infrastructure

import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan(basePackages = ["infrastructure", "ui"])
class DeployInfoPortalApplication

fun main(args: Array<String>) {
	val logger = LoggerFactory.getLogger(DeployInfoPortalApplication::class.java)

	// https://cloud.google.com/appengine/docs/standard/java-gen2/runtime#Environment_variables
	val isRunningOnGCP = System.getenv("GAE_DEPLOYMENT_ID") != null
	val profile = if (isRunningOnGCP) "prod" else "local"
	logger.info("isRunningOnGCP=${isRunningOnGCP}, profile=${profile}")

	// The profile must be set on the SpringApplication before run(): the logging system is
	// initialized from the environment early in startup — before initializer customizers run — so
	// logback-spring.xml's <springProfile> branches only see the profile when it is set here
	// (otherwise the prod branch would never activate and GAE logging would stay unstructured).
	val application = SpringApplication(DeployInfoPortalApplication::class.java)
	application.setAdditionalProfiles(profile)
	application.run(*args)
}
