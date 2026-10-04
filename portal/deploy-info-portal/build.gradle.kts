plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.spring)
	alias(libs.plugins.spring.boot)
	alias(libs.plugins.spring.dependencyManagement)
}

group = "io.github.ghrcosta"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

// https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3
val mockitoAgent: Configuration = configurations.create("mockitoAgent")

dependencies {
	implementation(libs.kotlin.reflect)

	implementation(platform(libs.spring.cloud.bom))
	implementation(libs.spring.boot.starter.web)

	implementation(libs.jackson.kotlin)

	implementation(libs.google.cloud.appengine.admin)
	implementation(libs.google.cloud.run)

	implementation(platform(libs.spring.cloud.gcp.bom))
	implementation(libs.spring.cloud.gcp.starter)
	implementation(libs.spring.cloud.gcp.starter.datastore)
	implementation(libs.spring.cloud.gcp.starter.logging)
	implementation(libs.spring.cloud.gcp.starter.storage)

	testImplementation(libs.spring.boot.starter.test)
	testImplementation(libs.spring.boot.starter.webmvc.test)
	testImplementation(libs.junit)
	@Suppress("AvoidDuplicateDependencies") testImplementation(libs.mockito.core)
	@Suppress("AvoidDuplicateDependencies") mockitoAgent(libs.mockito.core) { isTransitive = false }
	testImplementation(libs.mockito.kotlin)

	testRuntimeOnly(libs.junit.launcher)
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
tasks.test {
	jvmArgs("-javaagent:${mockitoAgent.asPath}")
}