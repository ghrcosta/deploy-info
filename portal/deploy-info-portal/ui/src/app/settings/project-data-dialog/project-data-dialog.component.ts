import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef, MatDialogTitle, MatDialogContent, MatDialogActions } from '@angular/material/dialog';
import { FormsModule, ReactiveFormsModule, FormControl, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Project } from "../settings.component";
import { ProjectDataDialogNetworkService } from './project-data-dialog.network.service';
import { ServiceAccountErrorDialogComponent, ServiceAccountErrorDialogData } from './service-account-error-dialog.component';

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
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './project-data-dialog.component.scss'
})
export class ProjectDataDialogComponent {
    readonly dialogRef = inject(MatDialogRef<ProjectDataDialogComponent>);
    readonly data = inject<DialogData>(MAT_DIALOG_DATA);
    readonly dialog = inject(MatDialog);
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
                    if (result.issues.issueServiceAccountError) {
                        this.openServiceAccountErrorDialog(result.issues.serviceAccountIssue, result.issues.portalServiceAccount);
                    } else {
                        let issuesText = [];
                        if (result.issues.issueNameConflict) {
                            issuesText.push('A project with this name already exists.');
                        }
                        let text = issuesText.join('\n\n');
                        this.openSnackBar(text);
                    }
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
                    if (result.issues.issueServiceAccountError) {
                        this.openServiceAccountErrorDialog(result.issues.serviceAccountIssue, result.issues.portalServiceAccount);
                    } else {
                        let issuesText = [];
                        if (result.issues.issueProjectNotFound) {
                            issuesText.push('Project not found.');
                        }
                        let text = issuesText.join('\n\n');
                        this.openSnackBar(text);
                    }
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

    /**
     * Error dialog for a failed service-account validation, naming the missing permission with
     * expandable grant instructions. 'Retry' closes the dialog with 'retry' and re-submits the
     * form, so the user can retry right after fixing IAM in another tab.
     */
    openServiceAccountErrorDialog(serviceAccountIssue: string | undefined, portalServiceAccount: string | undefined) {
        const dialogRef = this.dialog.open(ServiceAccountErrorDialogComponent, {
            disableClose: true,
            panelClass: 'service-account-error-dialog',
            data: {
                serviceAccountIssue: serviceAccountIssue ?? '',
                projectName: this.nameFormControl.value ?? this.data.project?.name ?? '',
                serviceAccount: this.serviceAccountFormControl.value ?? this.data.project?.serviceAccount ?? '',
                portalServiceAccount: portalServiceAccount
            } as ServiceAccountErrorDialogData
        });
        dialogRef.afterClosed().subscribe(result => {
            if (result == 'retry') {
                if (this.isActionAdd()) {
                    this.addProject();
                } else if (this.isActionEdit()) {
                    this.editProject();
                }
            }
        });
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
    /** Issue code from the backend (`ServiceAccountIssue`), set only when `issueServiceAccountError`. */
    serviceAccountIssue?: string;
    /** Portal's own service account email, set only when `issueServiceAccountError`. */
    portalServiceAccount?: string;
}

export interface EditProjectResult {
    issues?: EditProjectIssues;
    projects?: Project[];
}
export interface EditProjectIssues {
    issueProjectNotFound: boolean;
    issueServiceAccountError: boolean;
    /** Issue code from the backend (`ServiceAccountIssue`), set only when `issueServiceAccountError`. */
    serviceAccountIssue?: string;
    /** Portal's own service account email, set only when `issueServiceAccountError`. */
    portalServiceAccount?: string;
}
