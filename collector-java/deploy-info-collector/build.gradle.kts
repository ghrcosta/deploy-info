import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.10"

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
    // Build with the latest LTS, but emit Java 17 bytecode so consumers running Gradle on JDK 17 can load it.
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    // Keep the Java side aligned with the Kotlin jvmTarget (Java 17 bytecode).
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    @Suppress("AvoidDuplicateDependencies") testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.14.11")
    implementation("com.google.code.gson:gson:2.14.0")
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

// ---------------------------------------------------------------------------
// Functional tests (Gradle TestKit compatibility matrix)
// ---------------------------------------------------------------------------
// The functionalTest source set contains the TestKit compatibility tests plus the test-only
// "probe" plugin/task used by them. It is intentionally NOT part of the published plugin jar:
// nothing from this source set is wired into `jar`, `publishPlugins` or any production artifact.
val functionalTestSourceSet: SourceSet = sourceSets.create("functionalTest")

dependencies {
    "functionalTestImplementation"(gradleApi())
    "functionalTestImplementation"(gradleTestKit())
    @Suppress("AvoidDuplicateDependencies") "functionalTestImplementation"(kotlin("test"))
    "functionalTestImplementation"("org.junit.jupiter:junit-jupiter:5.12.2")
    "functionalTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    // The TestKit runner locates the plugin-under-test metadata file on the TEST runtime classpath,
    // so the pluginUnderTestMetadata output must be part of it.
    "functionalTestRuntimeOnly"(files(tasks.pluginUnderTestMetadata))
}

// Both the production plugin (main runtime classpath, which also carries the Kotlin stdlib)
// and the probe plugin (functionalTest output) must be visible to the TestKit consumer build
// through the injected plugin-under-test classpath. No Maven repository resolution of the
// plugin is needed anywhere in the functional tests.
tasks.pluginUnderTestMetadata {
    pluginClasspath.from(sourceSets.main.get().output)
    pluginClasspath.from(functionalTestSourceSet.output)
}

val functionalTest = tasks.register<Test>("functionalTest") {
    group = "verification"
    description = "Runs the Gradle TestKit compatibility matrix tests (7.6+17 and latest+21)."
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    dependsOn(functionalTestSourceSet.classesTaskName)
    dependsOn(tasks.pluginUnderTestMetadata)

    // Resolve (and auto-provision, via the foojay resolver) the JVMs used to run the consumer
    // builds, and hand their JAVA_HOMEs to the tests.
    val javaToolchainService = project.extensions.getByType(JavaToolchainService::class.java)
    val jdk17 = javaToolchainService.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
    val jdk21 = javaToolchainService.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
    systemProperty("compat.java17Home", jdk17.map { launcher -> launcher.metadata.installationPath.asFile.absolutePath }.get())
    systemProperty("compat.java21Home", jdk21.map { launcher -> launcher.metadata.installationPath.asFile.absolutePath }.get())
}

tasks.check {
    dependsOn(functionalTest)
}
