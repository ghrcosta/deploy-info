import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
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
    ],
})
export class ProjectsConfigComponent {
    readonly dialog = inject(MatDialog);
    readonly network = inject(ProjectsConfigNetworkService);

    projects: Project[] = [];

    /** Page subtitle with a properly pluralized project count. */
    get watchedProjectsSubtitle(): string {
        return `${this.projects.length} ${this.projects.length === 1 ? 'project' : 'projects'} watched for deploys`;
    }

    ngOnInit() {
        this.network.getProjects().subscribe(projects => {
            this.projects = projects;
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
