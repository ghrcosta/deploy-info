plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.4.6"
	id("io.spring.dependency-management") version "1.1.7"
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

// See: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3
val mockitoAgent = configurations.create("mockitoAgent")

extra["springCloudGcpVersion"] = "6.2.1"
extra["springCloudVersion"] = "2024.0.1"
val mockitoVersion = "5.14.2"
val mockitoKotlinVersion = "5.4.0" // https://github.com/mockito/mockito-kotlin/releases
dependencies {
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	implementation("org.springframework.boot:spring-boot-starter-web")

	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

	implementation("com.google.cloud:spring-cloud-gcp-starter")
	implementation("com.google.cloud:spring-cloud-gcp-starter-logging")
	implementation("com.google.cloud:spring-cloud-gcp-starter-storage")
	implementation("com.google.cloud:spring-cloud-gcp-starter-data-datastore")


	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.mockito:mockito-core:${mockitoVersion}")
	testImplementation("org.mockito.kotlin:mockito-kotlin:${mockitoKotlinVersion}")
	mockitoAgent("org.mockito:mockito-core:${mockitoVersion}") { isTransitive = false }

	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("com.google.cloud:spring-cloud-gcp-dependencies:${property("springCloudGcpVersion")}")
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
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