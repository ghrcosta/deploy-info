plugins {
    // Allows Gradle to automatically provision JDK toolchains (e.g. JDK 17 for the compatibility tests).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "deploy-info-collector"
