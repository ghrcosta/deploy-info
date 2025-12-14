import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogTitle, MatDialogContent, MatDialogActions } from '@angular/material/dialog';
import { FormsModule, ReactiveFormsModule, FormControl, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Project } from "../settings.component";
import { ProjectDataDialogNetworkService } from './project-data-dialog.network.service';

@Component({
    selector: 'project-data-dialog',
    imports: [
        FormsModule,
        ReactiveFormsModule,
        MatButtonModule,
        MatInputModule,
        MatFormFieldModule,
        MatProgressSpinnerModule,
        MatDialogTitle,
        MatDialogContent,
        MatDialogActions,
    ],
    templateUrl: './project-data-dialog.component.html',
    styleUrl: './project-data-dialog.component.scss'
})
export class ProjectDataDialogComponent {
    readonly dialogRef = inject(MatDialogRef<ProjectDataDialogComponent>);
    readonly data = inject<DialogData>(MAT_DIALOG_DATA);
    readonly network = inject(ProjectDataDialogNetworkService)
    readonly snackBar = inject(MatSnackBar);

    isRequestOngoing = false;

    isActionAdd = () => { return this.data.action == ProjectDataDialogAction.ADD}
    isActionEdit = () => { return this.data.action == ProjectDataDialogAction.EDIT}
    isActionDelete = () => { return this.data.action == ProjectDataDialogAction.DELETE}

    getInitialNameValue = () => {
        if (this.isActionEdit()) { return this.data.project.name } else { return '' }
    }

    getInitialGroupValue = () => {
        if (this.isActionEdit()) { return this.data.project.group } else { return '' }
    }

    getInitialServiceAccountValue = () => {
        if (this.isActionEdit()) { return this.data.project.serviceAccount } else { return '' }
    }

    nameFormControl = new FormControl({value: this.getInitialNameValue(), disabled: this.isActionEdit()}, [Validators.required]);
    groupFormControl = new FormControl(this.getInitialGroupValue(), []);
    serviceAccountFormControl = new FormControl(this.getInitialServiceAccountValue(), [Validators.required, Validators.email]);

    isFormInvalid = () => {
        const formContainsErrors = this.nameFormControl.invalid
            || this.groupFormControl.invalid
            || this.serviceAccountFormControl.invalid;

        const noDataWasChanged = (this.nameFormControl.value == this.getInitialNameValue())
            && (this.groupFormControl.value == this.getInitialGroupValue())
            && (this.serviceAccountFormControl.value == this.getInitialServiceAccountValue());

        return formContainsErrors || noDataWasChanged;
    }

    onSaveClicked = () => {
        this.snackBar.dismiss();

        if (this.isActionAdd()) {
            this.addProject();
        } else if (this.isActionEdit()) {
            this.editProject();
        } else if (this.isActionDelete()) {
            this.deleteProject();
        }
    }

    addProject() {
        this.isRequestOngoing = true;
        const project: Project = {
            name: this.nameFormControl.value ?? '',
            group: this.groupFormControl.value ?? '',
            serviceAccount: this.serviceAccountFormControl.value ?? ''
        }
        this.network.addProject(project).subscribe({
            next: result => {
                if (result.issues) {
                    let issuesText = [];
                    if (result.issues.issueNameConflict) {
                        issuesText.push('A project with this name already exists.');
                    }
                    if (result.issues.issueServiceAccountError) {
                        issuesText.push('Failed to validate service account.');
                    }
                    let text = issuesText.join('\n\n');
                    this.openSnackBar(text);
                } else {
                    this.dialogRef.close(result.projects);
                }
                this.isRequestOngoing = false;
            },
            error: error => {
                this.openSnackBar(error.message);
                this.isRequestOngoing = false;
            }
        })
    }

    editProject() {
        this.isRequestOngoing = true;
        const project: Project = {
            name: this.nameFormControl.value ?? '',
            group: this.groupFormControl.value ?? '',
            serviceAccount: this.serviceAccountFormControl.value ?? ''
        }
        this.network.editProject(project).subscribe({
            next: result => {
                if (result.issues) {
                    let issuesText = [];
                    if (result.issues.issueProjectNotFound) {
                        issuesText.push('Project not found.');
                    }
                    if (result.issues.issueServiceAccountError) {
                        issuesText.push('Failed to validate service account.');
                    }
                    let text = issuesText.join('\n\n');
                    this.openSnackBar(text);
                } else {
                    this.dialogRef.close(result.projects);
                }
                this.isRequestOngoing = false;
            },
            error: error => {
                this.openSnackBar(error.message);
                this.isRequestOngoing = false;
            }
        })
    }

    deleteProject() {
        this.isRequestOngoing = true;
        const projectName = this.data.project.name;
        this.network.deleteProject(projectName).subscribe({
            next: projects => {
                this.dialogRef.close(projects);
                this.isRequestOngoing = false;
            },
            error: error => {
                this.openSnackBar(error.message);
                this.isRequestOngoing = false;
            }
        })
    }

    onCancelClicked = () => {
        this.snackBar.dismiss();
        this.dialogRef.close();
    }

    openSnackBar(text: any) {
        this.snackBar.open(text, 'OK', {
            horizontalPosition: 'start',
            verticalPosition: 'top',
            panelClass: ['snackbar']
        })
    }
}

export enum ProjectDataDialogAction {
    ADD, EDIT, DELETE
}

export interface DialogData {
  action: ProjectDataDialogAction;
  project: Project;
}

export interface AddProjectResult {
    issues?: AddProjectIssues;
    projects?: Project[];
}
export interface AddProjectIssues {
    issueNameConflict: boolean;
    issueServiceAccountError: boolean;
}

export interface EditProjectResult {
    issues?: EditProjectIssues;
    projects?: Project[];
}
export interface EditProjectIssues {
    issueProjectNotFound: boolean;
    issueServiceAccountError: boolean;
}
