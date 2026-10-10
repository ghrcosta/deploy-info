pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
	resolutionStrategy {
		eachPlugin {
			// the App Engine Gradle plugin is published to Maven Central only, not to the
			// Gradle Plugin Portal, so map the plugin id to its Maven coordinates explicitly
			if (requested.id.id == "com.google.cloud.tools.appengine") {
				useModule("com.google.cloud.tools:appengine-gradle-plugin:${requested.version}")
			}
		}
	}
}

rootProject.name = "deploy-info-portal"
