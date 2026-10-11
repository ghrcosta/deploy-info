plugins {
    // Allows Gradle to automatically provision JDK toolchains (e.g. JDK 17 for the compatibility tests).
    // The version stays inline: Gradle does not support version catalog aliases in the settings file's
    // plugins block ("You cannot use a plugin declared in a version catalog in your settings file or
    // settings plugin"), so this is the one remaining declaration outside gradle/libs.versions.toml.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "deploy-info-collector"
