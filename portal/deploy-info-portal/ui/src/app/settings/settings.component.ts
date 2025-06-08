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

    projects = EXAMPLE;

    onAddProjectClicked = () => {
        const dialogRef = this.dialog.open(ProjectDataDialogComponent, {
            disableClose: true,
            data: {
                action: ProjectDataDialogAction.ADD
            }
        });
        dialogRef.afterClosed().subscribe(result => this.refreshPage())
    }

    onEditProjectClicked = (project: Project) => {
        const dialogRef = this.dialog.open(ProjectDataDialogComponent, {
            disableClose: true,
            data: {
                action: ProjectDataDialogAction.EDIT,
                project: { ...project } // Shallow copy
            }
        });
        dialogRef.afterClosed().subscribe(result => this.refreshPage())
    }

    onDeleteProjectClicked = (project: Project) => {
        const dialogRef = this.dialog.open(ProjectDataDialogComponent, {
            disableClose: true,
            data: {
                action: ProjectDataDialogAction.DELETE,
                project: { ...project } // Shallow copy
            }
        });
        dialogRef.afterClosed().subscribe(result => this.refreshPage())
    }

    refreshPage = () => {
        // TODO
    }
}

export interface Project {
    name: string;
    category?: string;
    serviceAccount: string;
}
const EXAMPLE: Project[] = [
    {
        name: "Project A",
        category: "Some category",
        serviceAccount: "project-a@serviceaccount.comdli ylsdiuv lasylfi ifgawubswçiouçufgiurçouaeroçeoçr aeoçrugvaeo urçouwg4çouerçfu grwçfg srgoçerug çoeurgeuvrvçeu gçorguv eçrou"
    },
    {
        name: "Project B",
        category: "Some other category",
        serviceAccount: "project-b@serviceaccount.com"
    }
];