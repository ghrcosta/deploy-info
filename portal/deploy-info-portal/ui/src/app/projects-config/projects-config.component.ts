import { Component, DestroyRef, inject, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatDialog } from '@angular/material/dialog';
import { ProjectDataDialogComponent, ProjectDataDialogAction } from "./project-data-dialog/project-data-dialog.component";
import { ProjectsConfigNetworkService } from './projects-config.network.service';

@Component({
    selector: 'projects-config',
    templateUrl: './projects-config.component.html',
    styleUrl: './projects-config.component.scss',
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [
        MatIconModule,
        MatProgressSpinnerModule,
    ],
})
export class ProjectsConfigComponent implements OnInit {
    readonly dialog = inject(MatDialog);
    readonly network = inject(ProjectsConfigNetworkService);
    private destroyRef = inject(DestroyRef);

    /**
     * The watched projects, `null` while `GET /project/all` is in flight — the
     * page contents are only rendered once it is set. A failed request resolves
     * to an empty list so the "No projects yet" state shows.
     */
    projects: Project[] | null = null;

    /** Page subtitle with a properly pluralized project count. */
    get watchedProjectsSubtitle(): string {
        const projects = this.projects ?? [];
        return `${projects.length} ${projects.length === 1 ? 'project' : 'projects'} watched for deploys`;
    }

    ngOnInit() {
        this.network.getProjects()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: projects => this.projects = projects,
                error: () => this.projects = [],
            });
    }

    onAddProjectClicked = () => {
        const dialogRef = this.dialog.open(ProjectDataDialogComponent, {
            disableClose: true,
            data: {
                action: ProjectDataDialogAction.ADD
            }
        });
        dialogRef.afterClosed().subscribe(result => this.refreshPage(result))
    }

    onEditProjectClicked = (project: Project) => {
        const dialogRef = this.dialog.open(ProjectDataDialogComponent, {
            disableClose: true,
            data: {
                action: ProjectDataDialogAction.EDIT,
                project: { ...project } // Shallow copy
            }
        });
        dialogRef.afterClosed().subscribe(result => this.refreshPage(result))
    }

    onDeleteProjectClicked = (project: Project) => {
        const dialogRef = this.dialog.open(ProjectDataDialogComponent, {
            disableClose: true,
            data: {
                action: ProjectDataDialogAction.DELETE,
                project: { ...project } // Shallow copy
            }
        });
        dialogRef.afterClosed().subscribe(result => this.refreshPage(result))
    }

    refreshPage = (projects: Project[] | undefined) => {
        if (projects) {
            this.projects = projects;
        }
    }
}

export interface Project {
    projectId: string;
    group?: string;
    serviceAccount: string;
}
