import { Component, ChangeDetectionStrategy, DestroyRef } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Highlight, HighlightAuto, HighlightJS } from 'ngx-highlightjs';
import { HighlightLineNumbers } from 'ngx-highlightjs/line-numbers';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DeployViewerService, DeploySelection } from '../deploy-viewer.component';
import { DeployContentNetworkService } from '../deploy-content.network.service';
import { DeployContent } from '../content-model';

import highlightGitLanguage from '../../highlightjs/git.js';

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
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './file-viewer.component.scss'
})
export class FileViewerComponent {
    constructor(
        private deployViewerService: DeployViewerService,
        private contentNetworkService: DeployContentNetworkService,
        private highlightService: HighlightJS,
        private destroyRef: DestroyRef,
    ) {}

    /** Header data of the currently selected version, straight from the tree (see `tree-model.ts`). */
    selection: DeploySelection | null = null;
    /** Content of the selected version's upload folder, from `GET /portal/deploy/content`. */
    content: DeployContent | null = null;
    isLoading = false;
    /** True when the content endpoint answered 404 — e.g. the folder was cleaned up after the tree was loaded. */
    notFound = false;
    /** True on any other content-fetch error (e.g. a Cloud Storage failure surfaces as 502). */
    loadFailed = false;

    ngOnInit() {
        this.highlightService.registerLanguage('git', highlightGitLanguage);

        this.deployViewerService.deployClickedEventObservable
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(selection => this.loadContent(selection));
    }

    private loadContent(selection: DeploySelection) {
        this.selection = selection;
        this.content = null;
        this.notFound = false;
        this.loadFailed = false;
        this.isLoading = true;

        this.contentNetworkService.getContent(selection.version.storageFolder).subscribe({
            next: content => {
                if (this.selection !== selection) return; // A newer selection superseded this request
                this.content = content;
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

    timestampToUtc = (timestamp: number) => new Date(timestamp).toUTCString();
}