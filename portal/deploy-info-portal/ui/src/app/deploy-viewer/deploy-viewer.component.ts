import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DeployNavigatorComponent } from "./deploy-navigator/deploy-navigator.component";
import { FileViewerComponent } from './file-viewer/file-viewer.component';
import { DeployTreeNetworkService } from './deploy-tree.network.service';
import { GroupEntry } from './tree-model';

/**
 * Deploy viewer shell: an indefinite loading indicator until the navigator tree
 * has loaded, then the navigator column next to the viewer column.
 */
@Component({
  selector: 'deploy-viewer',
  imports: [
    DeployNavigatorComponent,
    FileViewerComponent,
    MatProgressSpinnerModule,
  ],
  templateUrl: './deploy-viewer.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './deploy-viewer.component.scss'
})
export class DeployViewerComponent implements OnInit {
    private treeNetworkService = inject(DeployTreeNetworkService);
    private destroyRef = inject(DestroyRef);

    /**
     * The navigator tree, `null` while `GET /portal/tree` is in flight — the
     * shell contents are only rendered once it is set. A failed request resolves
     * to an empty tree so the navigator's "No deploys found" state shows.
     */
    tree: GroupEntry[] | null = null;

    ngOnInit() {
        this.treeNetworkService.getTree()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: entries => this.tree = entries,
                error: () => this.tree = [],
            });
    }
}
