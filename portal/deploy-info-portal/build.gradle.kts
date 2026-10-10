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

// ---------------------------------------------------------------------------
// Frontend (ui/): compile the Angular app and embed it into the bootJar, so
// the portal fat jar serves the UI itself from the same origin (CORS-free).
// ---------------------------------------------------------------------------

val uiDirectory = layout.projectDirectory.dir("ui")
val uiBuildOutput = uiDirectory.dir("dist/ui/browser")
val uiStaticResources = layout.buildDirectory.dir("ui-static")

// `npm` is only available as a .bat wrapper (npm.cmd) on Windows, which a plain Exec task cannot
// spawn — the same cross-platform concern handled for gcloud in the backend code.
val npmCommand: String =
if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

val npmInstallUi = tasks.register<Exec>("npmInstallUi") {
	group = "build"
	description = "Installs the frontend dependencies (npm ci) in the ui/ directory."
	workingDir = uiDirectory.asFile

	inputs.files(uiDirectory.file("package.json"), uiDirectory.file("package-lock.json"))
	outputs.dir(uiDirectory.dir("node_modules"))
	commandLine(npmCommand, "ci")
}

val buildUi = tasks.register<Exec>("buildUi") {
	group = "build"
	description = "Compiles the Angular frontend (production configuration)."
	dependsOn(npmInstallUi)
	workingDir = uiDirectory.asFile

	inputs.dir(uiDirectory.dir("src"))
	inputs.dir(uiDirectory.dir("public"))
	inputs.files(
		uiDirectory.file("angular.json"),
		uiDirectory.file("package.json"),
		uiDirectory.file("tsconfig.json"),
		uiDirectory.file("tsconfig.app.json"),
	)
	outputs.dir(uiDirectory.dir("dist"))
	commandLine(npmCommand, "run", "build")
}

val packageUi = tasks.register<Copy>("packageUi") {
	group = "build"
	description = "Copies the compiled frontend to a build folder embedded into the bootJar."
	dependsOn(buildUi)

	from(uiBuildOutput)
	into(uiStaticResources)
}

// The UI lives in `BOOT-INF/classes/static` at runtime — Spring Boot's default classpath static
// location, served with the frontend fallback registered in FrontendServingConfiguration. It is embedded only
// in the bootJar: `bootRun` and `test` keep working without Node/npm (the local dev pairing is
// `ng serve` on 4200 + backend on 8080, per the documentation).
tasks.bootJar {
	dependsOn(packageUi)
	from(uiStaticResources) {
		into("BOOT-INF/classes/static")
	}
}
