package infrastructure

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DeployInfoPortalApplication

fun main(args: Array<String>) {
	runApplication<DeployInfoPortalApplication>(*args)
}
