import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { HomeService, HomeView } from '../home/home.service';

/** One rendered line of the help page's Gradle snippet code block. */
interface CodeLine {
    /** 1-based line number. */
    n: number;
    text: string;
}

/**
 * The `deployInfoCollect` Gradle snippet shown on the help page: all task
 * parameters, each with a short comment on what it carries, matching
 * `CollectorTask` on the collector plugin. All trailing comments are aligned
 * on one column (the code is padded to the longest line).
 */
const COLLECTOR_SNIPPET = [
    'import io.github.ghrcosta.CollectorTask',
    '',
    '// Add the plugin',
    'plugins {',
    '    id("io.github.ghrcosta.deploy-info-collector") version "1.0.1"',
    '}',
    '',
    '// Configure the task',
    'tasks.deployInfoCollect {',
    '    deployType.set(CollectorTask.DeployType.GAE)      // Deploy target: GAE or RUN',
    '    portalUrl.set("https://deploy-info.example.com")  // URL of this portal',
    '    projects.set(listOf("proj-prod", "proj-dev"))     // GCP projects the code deploys to',
    '    maxFileSize.set(50)                               // Max collected file size in KB (default 50)',
    '    collectGitStatus.set(true)                        // Collect git log/status/diffs (default true)',
    '    maxGitLogLines.set(30)                            // Max git log entries collected (default 30)',
    '    extraFilesToCollect.set(listOf("path/to/file"))   // Extra project files to include',
    '}',
    '',
    '// Automate task execution after deploy',
    'tasks.appengineDeploy {',
    '    finalizedBy("deployInfoCollect")',
    '}',
].join('\n');

@Component({
    selector: 'help',
    templateUrl: './help.component.html',
    styleUrl: './help.component.scss',
    changeDetection: ChangeDetectionStrategy.Eager,
})
export class HelpComponent {
    private homeService = inject(HomeService);

    /** Pre-rendered lines of the Gradle snippet code block (built once). */
    readonly snippetLines: CodeLine[] = COLLECTOR_SNIPPET.split('\n')
        .map((text, i) => ({ n: i + 1, text }));

    /** Internal links switch the shell's view instead of navigating. */
    openView = (view: HomeView, event?: Event) => {
        event?.preventDefault();
        this.homeService.openView(view);
    };
}
