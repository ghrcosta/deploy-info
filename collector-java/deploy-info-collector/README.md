# deploy-info-collector

Gradle plugin to collect files and make them available to the portal.


## Usage

Example for `build.gradle.kts`:
```kotlin
// Add the plugin
plugins {
    id("io.github.ghrcosta.deploy-info-collector")
}

// Configure the task
tasks.deployInfoCollect {
    storageBucket.set("my-bucket-name")
    deployType.set(CollectorTask.DeployType.GAE)
}

// Automate task execution after deploy
tasks.appengineDeploy {
    finalizedBy("deployInfoCollect")
}
```

Task parameters:
<table>
<tr>
<td>Parameter name</td>
<td>Description</td>
<td>Example</td>
</tr>

<tr>
<td>maxFileSize</td>
<td>OPTIONAL. Maximum size of files that can be included. Default is 50KB.</td>
<td>

```kotlin
tasks.deployInfoCollect {
    maxFileSize.set(100 * 1024)
}
```

</td>
</tr>

<tr>
<td>collectGitStatus</td>
<td>OPTIONAL. If git-related data must be collected. Default is 'true'.</td>
<td>

```kotlin
tasks.deployInfoCollect {
    collectGitStatus.set(false)
}
```

</td>
</tr>

<tr>
<td>extraFilesToCollect</td>
<td>
OPTIONAL. Extra files to include in the collection. Each item in the list must be a path relative to
this projects' root directory. Directories are not supported. Note that only the file itself will be
collected, without its parent directories, so if two files have the same name, only one will be
included.
</td>
<td>

```kotlin
tasks.deployInfoCollect {
    extraFilesToCollect.set(listOf(
        "src/main/resources/application.properties",
        "src/main/resources/application-local.properties",
        "src/main/resources/application-prod.properties",
    ))
}
```

</td>
</tr>

<tr>
<td>storageBucket</td>
<td>REQUIRED. Name of the Cloud Storage bucket where files will be stored.</td>
<td>

```kotlin
tasks.deployInfoCollect {
    storageBucket.set("my-bucket-name")
}
```

</td>
</tr>

<tr>
<td>deployType</td>
<td>

REQUIRED. Type of deploy. Value must be from enum `CollectorTask.DeployType`.
Supports AppEngine ('GAE') and CloudRun ('RUN').

</td>
<td>

```kotlin
tasks.deployInfoCollect {
    deployType.set(CollectorTask.DeployType.GAE)
}
```

</td>
</tr>
</table>


## Development

1. On this project:
   1. Execute gradle task `publishToMavenLocal` to generate the plugin and put it into the local maven directory
2. On the project you want to test the plugin:
   1. Modify `settings.gradle.kts` to include the `mavenLocal()` repository. Note that if the project has modules,
      it may have multiple `settings.gradle.kts`. If so, you must modify the root one.
      ```kotlin
      pluginManagement {
          repositories {
              mavenLocal() // <-- This line
              gradlePluginPortal()
              mavenCentral()
          }
      }
      ```
   2. Modify `build.gradle.kts` to import the plugin into the project (or module) and configure it. Since you are
      loading a plugin from local maven, you must include the plugin version.
      ```kotlin
      plugins {
          id("io.github.ghrcosta.deploy-info-collector") version "0.0.1"
      }

      tasks.deployInfoCollect {
          storageBucket.set("my-bucket-name")
          deployType.set(CollectorTask.DeployType.GAE)
      }
      tasks.appengineDeploy {
          finalizedBy("deployInfoCollect")
      }
      ```