import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogTitle, MatDialogContent, MatDialogActions } from '@angular/material/dialog';
import { FormsModule, ReactiveFormsModule, FormControl, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { Project } from "../settings.component";

@Component({
    selector: 'project-data-dialog',
    imports: [
        FormsModule,
        ReactiveFormsModule,
        MatButtonModule,
        MatInputModule,
        MatFormFieldModule,
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

    isActionAdd = () => { return this.data.action == ProjectDataDialogAction.ADD}
    isActionEdit = () => { return this.data.action == ProjectDataDialogAction.EDIT}
    isActionDelete = () => { return this.data.action == ProjectDataDialogAction.DELETE}

    getInitialNameValue = () => {
        if (this.isActionEdit()) { return this.data.project.name } else { return '' }
    }

    getInitialCategoryValue = () => {
        if (this.isActionEdit()) { return this.data.project.category } else { return '' }
    }

    getInitialServiceAccountValue = () => {
        if (this.isActionEdit()) { return this.data.project.serviceAccount } else { return '' }
    }

    nameFormControl = new FormControl({value: this.getInitialNameValue(), disabled: this.isActionEdit()}, [Validators.required]);
    categoryFormControl = new FormControl(this.getInitialCategoryValue(), []);
    serviceAccountFormControl = new FormControl(this.getInitialServiceAccountValue(), [Validators.required, Validators.email]);

    isFormInvalid = () => {
        const formContainsErrors = this.nameFormControl.invalid
            || this.categoryFormControl.invalid
            || this.serviceAccountFormControl.invalid;

        const noDataWasChanged = (this.nameFormControl.value == this.getInitialNameValue())
            && (this.categoryFormControl.value == this.getInitialCategoryValue())
            && (this.serviceAccountFormControl.value == this.getInitialServiceAccountValue());

        return formContainsErrors || noDataWasChanged;
    }

    onSaveClicked = () => {
        if (this.isActionAdd()) {
            // TODO: Send request to backend
            // TODO: Handle response
        } else if (this.isActionEdit()) {
            // TODO: Send request to backend
            // TODO: Handle response
        } else if (this.isActionDelete()) {
            // TODO: Send request to backend
            // TODO: Handle response
        }
        this.dialogRef.close();
    }

    onCancelClicked = () => {
        this.dialogRef.close();
    }
}

export enum ProjectDataDialogAction {
    ADD, EDIT, DELETE
}

export interface DialogData {
  action: ProjectDataDialogAction;
  project: Project;
}
