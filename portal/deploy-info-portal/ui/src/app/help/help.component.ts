import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { HomeService, HomeView } from '../home/home.service';

/** One rendered line of the help page's code blocks (Gradle and Python snippets). */
interface CodeLine {
    /** 1-based line number. */
    n: number;
    text: string;
}

const COLLECTOR_SNIPPET = [
    'import io.github.ghrcosta.CollectorTask',
    '',
    '// Add the plugin',
    'plugins {',
    '    id("io.github.ghrcosta.deploy-info-collector") version "1.0.3"',
    '}',
    '',
    '// Configure the task',
    'tasks.deployInfoCollect {',
    '    deployType.set(CollectorTask.DeployType.GAE)      // Deployment target: GAE or RUN',
    '    portalUrl.set("https://deploy-info.example.com")  // URL of this portal',
    '    projects.set(listOf("proj-prod", "proj-dev"))     // GCP projects the code deploys to',
    '    maxFileSize.set(50)                               // Max collected file size in KB (default 50)',
    '    collectGitStatus.set(true)                        // Collect git log/status/diffs (default true)',
    '    maxGitLogLines.set(30)                            // Max git log entries collected (default 30)',
    '    extraFilesToCollect.set(listOf("path/to/file"))   // Extra project files to include',
    '}',
    '',
    '// Automate task execution after deployment',
    'tasks.appengineDeploy {',
    '    finalizedBy("deployInfoCollect")',
    '}',
].join('\n');

const PYTHON_COLLECTOR_CLI_SNIPPET = [
    '# All parameters:',
    '#   --deploy-type         Deployment target: GAE or RUN (required)',
    '#   --portal-url          URL of this portal (required)',
    '#   --projects            GCP projects the code deploys to (required, comma-separated)',
    '#   --project-dir         Project root (default: the current directory)',
    '#   --max-file-size       Max collected file size in KB (default 50)',
    '#   --max-git-log-lines   Max git log entries collected (default 30)',
    '#   --collect-git-status  Collect git log/status/diffs (default true)',
    '#   --extra-files         Extra project files to include',
    'python3 deploy_info_collector.py \\',
    '    --deploy-type GAE \\',
    '    --portal-url https://deploy-info.example.com \\',
    '    --projects proj-prod,proj-dev \\',
    '    --project-dir . \\',
    '    --max-file-size 50 \\',
    '    --max-git-log-lines 30 \\',
    '    --collect-git-status \\',
    '    --extra-files path/to/file',
].join('\n');

const PYTHON_COLLECTOR_SCRIPT_SNIPPET = [
    'import deploy_info_collector as dic',
    'from pathlib import Path',
    '',
    'exit_code = dic.run(dic.CollectorParams(',
    '    deploy_type=dic.DeployType.GAE,                # Deployment target: GAE or RUN',
    '    portal_url="https://deploy-info.example.com",  # URL of this portal',
    '    projects=["proj-prod", "proj-dev"],            # GCP projects the code deploys to',
    '    project_dir=Path("."),                         # Project root (default: the current directory)',
    '    max_file_size=50,                              # Max collected file size in KB (default 50)',
    '    collect_git_status=True,                       # Collect git log/status/diffs (default true)',
    '    max_git_log_lines=30,                          # Max git log entries collected (default 30)',
    '    extra_files=["path/to/file"],                  # Extra project files to include',
    '))',
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

    /** Pre-rendered lines of the Python collector's CLI-snippet code block (built once). */
    readonly pythonCliSnippetLines: CodeLine[] = PYTHON_COLLECTOR_CLI_SNIPPET.split('\n')
        .map((text, i) => ({ n: i + 1, text }));

    /** Pre-rendered lines of the Python collector's script-call code block (built once). */
    readonly pythonScriptSnippetLines: CodeLine[] = PYTHON_COLLECTOR_SCRIPT_SNIPPET.split('\n')
        .map((text, i) => ({ n: i + 1, text }));

    /** Internal links switch the shell's view instead of navigating. */
    openView = (view: HomeView, event?: Event) => {
        event?.preventDefault();
        this.homeService.openView(view);
    };
}
