import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DeploymentViewerService, DeploymentSelection } from '../deployment-viewer.service';
import { DeploymentContentNetworkService } from '../deployment-content.network.service';
import { ContentFile, DeploymentContent, GitFileStatus } from '../content-model';
import { fmtTime, timeAgo } from '../time-ago';

/** One rendered code line of a `.code` block (see toCodeLines). */
interface CodeLine {
    /** 1-based line number. */
    n: number;
    text: string;
    /** Diff tint class: `cl-add`/`cl-del`/`cl-hunk`, or '' for unhighlighted lines. */
    cls: string;
}

/** A `.file-block`'s view model: header fields plus pre-rendered code lines. */
interface FileBlock {
    filepath: string;
    status: GitFileStatus;
    lines: CodeLine[];
}

/**
 * File viewer: a deployment card with crumb chips, the segmented Git/Extras tabs
 * and the per-file blocks with the per-line code renderer (diff tinting).
 * Screen design: documentation/frontend/main-screen.md.
 */
@Component({
    selector: 'file-viewer',
    imports: [
        MatIconModule,
        MatProgressSpinnerModule,
    ],
    templateUrl: './file-viewer.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './file-viewer.component.scss'
})
export class FileViewerComponent {
    private deploymentViewerService = inject(DeploymentViewerService);
    private contentNetworkService = inject(DeploymentContentNetworkService);
    private destroyRef = inject(DestroyRef);

    /** Header data of the currently selected version, straight from the tree (see `tree-model.ts`). */
    selection: DeploymentSelection | null = null;
    /** Content of the selected version's upload folder, from `GET /portal/deploy/content`. */
    content: DeploymentContent | null = null;
    isLoading = false;
    /** True when the content endpoint answered 404 — e.g. the folder was cleaned up after the tree was loaded. */
    notFound = false;
    /** True on any other content-fetch error (e.g. a Cloud Storage failure surfaces as 502). */
    loadFailed = false;

    /** The active segmented tab: the GIT section or the extras. */
    activeTab: 'git' | 'extras' = 'git';

    /** Pre-rendered code lines of the GIT section blocks (built once per loaded content). */
    gitLogLines: CodeLine[] = [];
    gitStatusLines: CodeLine[] = [];
    changeBlocks: FileBlock[] = [];
    extraBlocks: FileBlock[] = [];

    /** Number of files in the GIT section tab (log + status + changes). */
    get gitCount(): number {
        return this.changeBlocks.length
            + (this.gitLogLines.length > 0 ? 1 : 0)
            + (this.gitStatusLines.length > 0 ? 1 : 0);
    }

    /** Relative/absolute timestamp formatters shared with the navigator. */
    timeAgo = timeAgo;
    fmtTime = fmtTime;

    ngOnInit() {
        this.deploymentViewerService.deploymentClickedEventObservable
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(selection => this.loadContent(selection));
    }

    setTab = (tab: 'git' | 'extras') => {
        this.activeTab = tab;
    };

    statusChipClass = (status: GitFileStatus) =>
        status === 'new' ? 'st-new'
        : status === 'untracked' ? 'st-untracked'
        : status === 'modified' ? 'st-modified'
        : '';

    statusChipLabel = (status: GitFileStatus) =>
        status === 'new' ? 'New'
        : status === 'untracked' ? 'Untracked'
        : status === 'modified' ? 'Modified'
        : status;

    private loadContent(selection: DeploymentSelection) {
        this.selection = selection;
        this.content = null;
        this.notFound = false;
        this.loadFailed = false;
        this.isLoading = true;
        this.activeTab = 'git';

        this.contentNetworkService.getContent(selection.version.storageFolder).subscribe({
            next: content => {
                if (this.selection !== selection) return; // A newer selection superseded this request
                this.content = content;
                this.buildViewModels(content);
                this.isLoading = false;
            },
            error: (error: HttpErrorResponse) => {
                if (this.selection !== selection) return;
                this.isLoading = false;
                this.notFound = error.status === 404;
                this.loadFailed = error.status !== 404;
            },
        });
    }

    /** Pre-render the code lines once per content, instead of per change-detection pass. */
    private buildViewModels(content: DeploymentContent) {
        this.gitLogLines = content.git?.gitlog ? toCodeLines(content.git.gitlog, false) : [];
        this.gitStatusLines = content.git?.gitstatus ? toCodeLines(content.git.gitstatus, false) : [];
        this.changeBlocks = (content.git?.changes ?? []).map(change => toFileBlock(change, true));
        this.extraBlocks = content.extras.map(extra => toFileBlock(extra, false));
    }
}

/**
 * Per-line code renderer: every line becomes a `.cl` span with a `.cln` number
 * and a `.clt` text; when `diff` is set, lines starting with +, - or @@ get the
 * `cl-add`/`cl-del`/`cl-hunk` tint classes driven by the add/del/hunk design tokens.
 */
function toCodeLines(text: string, diff: boolean): CodeLine[] {
    const lines = text.split('\n');
    if (lines.length && lines[lines.length - 1] === '') lines.pop();
    return lines.map((line, i) => {
        let cls = '';
        if (diff) {
            if (line.startsWith('+')) cls = 'cl-add';
            else if (line.startsWith('-')) cls = 'cl-del';
            else if (line.startsWith('@@')) cls = 'cl-hunk';
        }
        return { n: i + 1, text: line, cls };
    });
}

function toFileBlock(file: ContentFile, diff: boolean): FileBlock {
    return { filepath: file.filepath, status: file.status, lines: toCodeLines(file.content, diff) };
}
