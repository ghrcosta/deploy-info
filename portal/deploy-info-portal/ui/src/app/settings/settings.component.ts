import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { ProjectDataDialogComponent, ProjectDataDialogAction } from "./project-data-dialog/project-data-dialog.component";
import { SettingsNetworkService } from './settings.network.service';

@Component({
    selector: 'settings',
    templateUrl: './settings.component.html',
    styleUrl: './settings.component.scss',
    imports: [
        FormsModule,
        MatCardModule,
        MatButtonModule,
        MatExpansionModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
    ],
})
export class SettingsComponent {
    readonly dialog = inject(MatDialog);
    readonly network = inject(SettingsNetworkService)

    projects: Project[] = [];

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
    name: string;
    group?: string;
    serviceAccount: string;
}