plugins {
    kotlin("jvm") version "2.1.10"

    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.3.1"
}

group = "io.github.ghrcosta"

val pluginId = "${group}.deploy-info-collector"
val pluginMainClass = "${group}.CollectorPlugin"
val pluginVersion = "0.0.1"
val pluginName = "DeployInfo Collector"
val pluginDescription = "Used to collect the data shown on DeployInfo portal."

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.14.0")
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create(pluginId) {
            id = pluginId
            implementationClass = pluginMainClass
            version = pluginVersion
            displayName = pluginName
            description = pluginDescription
        }
    }
}