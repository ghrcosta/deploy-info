plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.spring)
	alias(libs.plugins.spring.boot)
	alias(libs.plugins.spring.dependencyManagement)
	alias(libs.plugins.google.appengine)
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
	// Pinned jar name so the app.yaml entrypoint (`java -jar portal.jar`) never changes with the
	// project version — the deployed version identity is carried by the App Engine version name.
	archiveFileName = "portal.jar"
}

// ---------------------------------------------------------------------------
// GAE deploy: deploy the portal fat jar to GCP App Engine Standard (Java 21).
//
//	./gradlew appengineDeploy -PgaeProjectId=<gcp-project> [-PgaeService=<service>]
//
// - gaeProjectId (required) — the GCP project to deploy to
// - gaeService (default "default") — the App Engine service to deploy to
// - gaePromote (default true) — route traffic to the new version (also stops the previous
//   version, matching gcloud's default pairing of the two flags)
//
// These properties can also be set permanently instead of per-command: in `gradle.properties`
// next to this build script (or in `~/.gradle/gradle.properties` for every project) as
// `gaeProjectId=<gcp-project>`, or in an environment variable `ORG_GRADLE_PROJECT_gaeProjectId`
// (CI-friendly). They surface exactly like `-P` flags, which always win over them.
//
// The App Engine version name is "v<portal_version>" (e.g. v0-0-1): GAE version IDs must start
// with a letter and only contain lowercase letters, digits and hyphens, so the project version
// is lowercased, "-SNAPSHOT" is dropped and every other character becomes a hyphen. See
// documentation/deployment.md.
// ---------------------------------------------------------------------------

val gaeProjectId: String? = findProperty("gaeProjectId")?.toString()
val gaeService: String = (findProperty("gaeService") ?: "default").toString()
val gaePromote: Boolean = (findProperty("gaePromote") ?: "true").toString().toBoolean()

val gaePortalVersion = version.toString()
	.lowercase()
	.removeSuffix("-snapshot")
	.replace(Regex("[^a-z0-9-]"), "-")
	.trimEnd('-')
val gaeVersion = "v${gaePortalVersion}"

val gaeAppEngineDirectory = layout.buildDirectory.dir("generated-appengine")

// The final app.yaml is generated from the template in src/main/appengine/app.yaml with the
// service name injected, so the target service is configurable from the command line without
// editing the template. The generated directory is what the plugin stages from.
val generateGaeConfig = tasks.register("generateGaeConfig") {
	group = "build"
	description = "Generates the app.yaml staged for the App Engine deploy (service from -PgaeService)."
	inputs.file(layout.projectDirectory.file("src/main/appengine/app.yaml"))
	inputs.property("service", gaeService)
	outputs.dir(gaeAppEngineDirectory)
	doLast {
		if (!Regex("^[a-z][a-z0-9-]{0,62}$").matches(gaeService)) {
			throw GradleException(
				"Invalid gaeService '$gaeService': App Engine service IDs must start with a letter and " +
					"contain only lowercase letters, digits and hyphens (max 63 characters)."
			)
		}
		val outDir = gaeAppEngineDirectory.get().asFile
		outDir.deleteRecursively()
		outDir.mkdirs()
		File(outDir, "app.yaml").writeText(
			layout.projectDirectory.file("src/main/appengine/app.yaml").asFile.readText()
				.replace("@GAE_SERVICE@", gaeService)
		)
	}
}

appengine {
	stage {
		setArtifact(tasks.bootJar.flatMap { it.archiveFile })
		setAppEngineDirectory(gaeAppEngineDirectory)
	}
	deploy {
		version = gaeVersion
		promote = gaePromote
		stopPreviousVersion = gaePromote
		gaeProjectId?.let { projectId = it }
	}
}

// The plugin resolves its staging inputs eagerly (plain setters), so the task dependencies from
// staging to the jar build and to the generated app.yaml must be wired explicitly.
tasks.appengineStage {
	dependsOn(tasks.bootJar, generateGaeConfig)
}

tasks.named("appengineDeploy") {
	doFirst {
		if (gaeProjectId == null) {
			throw GradleException("Missing gaeProjectId: run appengineDeploy with -PgaeProjectId=<gcp-project>.")
		}
	}
}
