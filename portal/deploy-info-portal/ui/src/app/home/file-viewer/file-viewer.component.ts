import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Highlight, HighlightAuto } from 'ngx-highlightjs';
import { HighlightLineNumbers } from 'ngx-highlightjs/line-numbers';
import { HomeService } from '../home.component';

@Component({
    selector: 'file-viewer',
    imports: [
        Highlight,
        HighlightAuto,
        HighlightLineNumbers,
        MatExpansionModule,
        MatIconModule,
        MatProgressSpinnerModule,
        MatTabsModule,
    ],
    templateUrl: './file-viewer.component.html',
    styleUrl: './file-viewer.component.scss'
})
export class FileViewerComponent {
    constructor(
        private homeService: HomeService
    ) {}

    isLoading = false;
    data: DeployData | null = null;

    ngOnInit() {
        this.homeService.deployClickedEventObservable.subscribe(versionId => {
            (async () => {
                this.isLoading = true;
                
                await this.delay(1000);

                if (versionId == 'id13') {
                    this.data = EXAMPLE_DATA_FULL;
                } else if (versionId == 'id14') {
                    this.data = EXAMPLE_DATA_NO_EXTRA;
                } else if (versionId == 'id15') {
                    this.data = EXAMPLE_DATA_NO_GIT;
                } else {
                    this.data = null;
                }
                this.isLoading = false;
            })();
        })
    }

    delay(ms: number) {
        return new Promise( resolve => setTimeout(resolve, ms) );
    }

    timestampToUtc = (timestamp: number) => new Date(timestamp).toUTCString();
}

interface DeployData {
    project: string;
    service: string;
    type: string;
    version: string;
    link: string;
    author: string;
    timestamp: number;
    git?: GitData;
    extras?: FileData[];
}
interface GitData {
    gitlog?: string;
    gitstatus?: string;
    changes?: FileData[];
}
interface FileData {
    filepath: string;
    content: string;
}

const EXAMPLE_DATA_NO_GIT: DeployData = {
    project: 'my-project',
    service: 'my-service',
    type: 'GAE',
    version: '20250503-1635',
    link: 'https://20250503-1635-dot-my-service.my-project.appspot.com',
    author: 'email@email.com',
    timestamp: 1746322088662,
    extras: [
        {
            filepath: '/somepath1',
            content: 'example content'
        },
        {
            filepath: '/somepath2',
            content: 'example content'
        }
    ]
};

const EXAMPLE_DATA_NO_EXTRA: DeployData = {
    project: 'my-project',
    service: 'my-service',
    type: 'GAE',
    version: '20250503-1635',
    link: 'https://20250503-1635-dot-my-service.my-project.appspot.com',
    author: 'email@email.com',
    timestamp: 1746322088662,
    git: {
        gitlog: `98b7f3d Fix handling of participant names with diacritics`,
        gitstatus: `On branch home`,
        changes: [
        {
            filepath: '/springboot-flutter/springboot/build.gradle.kts',
            content: `diff --git a/springboot-flutter/springboot/build.gradle.kts b/springboot-flutter/springboot/build.gradle.kts`
        },
        {
            filepath: '/somepath',
            content: `example content`
        }
    ]
    }
};

const EXAMPLE_DATA_FULL: DeployData = {
    project: 'my-project',
    service: 'my-service',
    type: 'GAE',
    version: '20250503-1635',
    link: 'https://20250503-1635-dot-my-service.my-project.appspot.com',
    author: 'email@email.com',
    timestamp: 1746322088662,
    git: {
        gitlog: `98b7f3d Fix handling of participant names with diacritics
1b761e5 Add missing vote option, minor improvements to readme
f33baff Always show all participants
7143376 Improve documentation
880d6c2 Minor error message improvement
65f43ff Remove unused endpoint
e84534c Fix focus on name selection
fb820b2 UI improvements, logging improvements, build improvements
af4ba1a GCP Logging
9127de0 Change Kotlin Multiplatform deploy version prefix
f87131e Fix Flutter UI .gitignore
cfeb137 Flutter UI, App Engine deploy
0c3b5e2 Flutter UI
3dafe93 WIP Flutter UI, set up firestore emulator, Kotlin 1.9.0 update
1cf3d58 WIP Flutter UI
22cb642 WIP Flutter UI, Validation library
9dbb714 WIP Flutter UI
a8f441c Fixed local dev CORS, Swagger docs
81ad87b Remove cookies
f0803a6 Spring Boot profiles, in-memory storage, GAE settings
c09763f Settings for App Engine + run configurations
370fcc2 Renamed project in IntelliJ IDEA
fa225b9 Backend implementation using Spring Boot + Flutter
12d1aab Putting all code into separate folder
5af7da4 Update README
b64c023 Add average value
e3febb9 Update README
86064dd Update README, remove Cloud Functions references
cf0b29c Remove long polling code, add client firebase sync
3784c35 Fix typo on app.yaml
9ccbfb5 Update README
ba59390 Update README
ab7888d Update README
25d1f66 Update README
40cd23e Update README
2d0f074 Update README, change deploy settings
65c2ee5 Update README, minor fixes post deploy
9035fd9 Update README, minor deploy fixes
abe6283 Room UI, cleanup cronjob, GAE logging
1508c35 Index UI, partial code for Room UI, minor server improvements
a5abe22 Organize code, setup GAE deploy
a48a8ea Fix sync polling, add cloud function
248f841 Basic server implementation
d5c334b Project setup
21c5300 Initial commit`,
        gitstatus: `On branch home
Your branch is up to date with 'origin/home'.

Changes to be committed:
    (use "git restore --staged <file>..." to unstage)
    modified:   springboot/build.gradle.kts

Changes not staged for commit:
    (use "git add <file>..." to update what will be committed)
    (use "git restore <file>..." to discard changes in working directory)
    modified:   .idea/compiler.xml
    modified:   .idea/modules.xml
    modified:   .idea/modules/planning-poker.iml
    modified:   .idea/modules/springboot/planning-poker.springboot.home.iml
    modified:   settings.gradle.kts
    modified:   springboot/build.gradle.kts

Untracked files:
    (use "git add <file>..." to include in what will be committed)
    .idea/springboot-flutter.iml
    Untitled.png
    test file.txt
    test.txt
    ../springboot-nextjs/`,
        changes: [
        {
            filepath: '/springboot-flutter/springboot/build.gradle.kts',
            content: `diff --git a/springboot-flutter/springboot/build.gradle.kts b/springboot-flutter/springboot/build.gradle.kts
index 7c7a0cc..11329bf 100644
--- a/springboot-flutter/springboot/build.gradle.kts
+++ b/springboot-flutter/springboot/build.gradle.kts
@@ -1,6 +1,8 @@
+import io.github.ghrcosta.CollectorTask
    import org.apache.tools.ant.taskdefs.condition.Os
    import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
    
+
    plugins {
        val kotlinVersion = "1.9.0"
    
@@ -22,6 +24,21 @@ plugins {
        // https://github.com/GoogleCloudPlatform/app-gradle-plugin#using-plugins-block
        // https://github.com/GoogleCloudPlatform/app-gradle-plugin/blob/master/USER_GUIDE.md#applying-the-plugin
        id("com.google.cloud.tools.appengine-appyaml") version "2.4.5"
+
+	id("io.github.ghrcosta.deploy-info-collector") version "0.0.1"
+}
+
+tasks.deployInfoCollect {
+	extraFilesToCollect.set(listOf(
+		"springboot/src/home/resources/application.properties",
+		"springboot/src/home/resources/application-local.properties",
+		"/springboot/src/home/resources/application-prod.properties",
+	))
+	storageBucket.set("fake")
+	deployType.set(CollectorTask.DeployType.GAE)
+}
+tasks.appengineDeploy {
+	finalizedBy("deployInfoCollect")
    }
    
    noArg {
@@ -168,15 +185,4 @@ appengine {
                setCloudSdkHome(googleCloudSdkHome)
            }
        }
-}
-
-tasks.register<Exec>("test") {
-	val command = "git log --oneline".split(" ").toList()
-	doFirst {
-		project.logger.lifecycle("Running git log")
-	}
-	commandLine(command)
-	// TODO: salvar output
-	// TODO: processar output do git status para pegar arquivos
-	// TODO: dá pra fazer tudo em gradle? precisa kotlin library + gradle plugin?
    }
\ No newline at end of file`
        },
        {
            filepath: '/somepath',
            content: `example content`
        }
    ]
    },
    extras: [
        {
            filepath: '/somepath1',
            content: 'example content'
        },
        {
            filepath: '/somepath2',
            content: 'example content'
        }
    ]
};