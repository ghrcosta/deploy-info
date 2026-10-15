import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)

    `java-gradle-plugin`
    alias(libs.plugins.plugin.publish)
}

group = "io.github.ghrcosta"
version = "1.0.3"

kotlin {
    // Build with the latest LTS, but let the plugin work on JDK 11+. Verified by CompatibilityTest.
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

java {
    // Keep the Java side aligned with the Kotlin jvmTarget
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenCentral()
}

dependencies {
    @Suppress("AvoidDuplicateDependencies") testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    implementation(libs.gson)
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    website = "https://github.com/ghrcosta/deploy-info"
    vcsUrl = "https://github.com/ghrcosta/deploy-info.git"
    plugins {
        create("deployInfoCollectorPlugin") {
            id = "${group}.deploy-info-collector"
            displayName = "DeployInfo Collector"
            description = "Collects git state and uploads to your own deployment of DeployInfo portal."
            tags = listOf("git", "build-info")
            implementationClass = "${group}.CollectorPlugin"
            compatibility {
                features {
                    // Declared after being verified by the functional tests in CompatibilityTest,
                    // which run the plugin under --configuration-cache and isolated projects.
                    configurationCache = true
                    isolatedProjects = true
                }
            }
        }
    }
}

publishing {
    repositories {
        maven { url = uri(layout.buildDirectory.dir("localPublication")) }
    }
}

// ---------------------------------------------------------------------------
// Functional tests (Gradle TestKit compatibility matrix)
// ---------------------------------------------------------------------------
// The functionalTest source set contains the TestKit compatibility tests plus the test-only
// "probe" plugin/task used by them. It is intentionally NOT part of the published plugin jar:
// nothing from this source set is wired into `jar`, `publishPlugins` or any production artifact.
val functionalTestSourceSet: SourceSet = sourceSets.create("functionalTest")

// JUnit 6 (used only by the functionalTest source set) requires Java 17, while the plugin's
// published bytecode targets Java 11. The functionalTest source set is never published and
// always runs on the Gradle daemon's JDK (21), so its classpath configurations resolve against
// Java 17 instead of the project-wide Java 11 target, allowing JUnit 6 on the classpath.
listOf("functionalTestCompileClasspath", "functionalTestRuntimeClasspath").forEach { configName ->
    configurations.getByName(configName) {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
        }
    }
}

dependencies {
    "functionalTestImplementation"(gradleApi())
    "functionalTestImplementation"(gradleTestKit())
    @Suppress("AvoidDuplicateDependencies") "functionalTestImplementation"(kotlin("test"))
    "functionalTestImplementation"(libs.junit.jupiter)
    "functionalTestRuntimeOnly"(libs.junit.launcher)
    // The TestKit runner locates the plugin-under-test metadata file on the TEST runtime classpath,
    // so the pluginUnderTestMetadata output must be part of it.
    "functionalTestRuntimeOnly"(files(tasks.pluginUnderTestMetadata))
    // The feature-compatibility test reads the generated plugin descriptor (carrying the
    // compatibility.feature.* flags) from the TEST runtime classpath.
    "functionalTestRuntimeOnly"(files(tasks.pluginDescriptors))
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
    description = "Runs the Gradle TestKit compatibility matrix tests (7.6+11, 7.6+17 and latest+21), " +
        "including configuration-cache and isolated-projects feature tests, plus real CollectorTask " +
        "execution under each Gradle version's embedded Kotlin stdlib."
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    dependsOn(functionalTestSourceSet.classesTaskName)
    dependsOn(tasks.pluginUnderTestMetadata)

    // Resolve (and auto-provision, via the foojay resolver) the JVMs used to run the consumer
    // builds, and hand their JAVA_HOMEs to the tests.
    val javaToolchainService = project.extensions.getByType(JavaToolchainService::class.java)
    val jdk11 = javaToolchainService.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) }
    val jdk17 = javaToolchainService.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
    val jdk21 = javaToolchainService.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
    systemProperty("compat.java11Home", jdk11.map { launcher -> launcher.metadata.installationPath.asFile.absolutePath }.get())
    systemProperty("compat.java17Home", jdk17.map { launcher -> launcher.metadata.installationPath.asFile.absolutePath }.get())
    systemProperty("compat.java21Home", jdk21.map { launcher -> launcher.metadata.installationPath.asFile.absolutePath }.get())
}

tasks.check {
    dependsOn(functionalTest)
}
