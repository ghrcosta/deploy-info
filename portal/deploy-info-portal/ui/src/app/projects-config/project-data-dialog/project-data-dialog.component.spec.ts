import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';

import { ProjectDataDialogAction, ProjectDataDialogComponent } from './project-data-dialog.component';
import { ProjectDataDialogNetworkService } from './project-data-dialog.network.service';
import { ServiceAccountErrorDialogComponent } from './service-account-error-dialog.component';
import { Project } from '../projects-config.component';

const PROJECT: Project = {
  projectId: 'proj-x',
  group: 'Platform',
  serviceAccount: 'sa@proj-x.iam.gserviceaccount.com',
};
const UPDATED_PROJECTS: Project[] = [
  { projectId: 'proj-x', group: 'Platform', serviceAccount: 'new@proj-x.iam.gserviceaccount.com' },
];

describe('ProjectDataDialogComponent', () => {
  let fixture: ComponentFixture<ProjectDataDialogComponent>;
  let component: ProjectDataDialogComponent;
  let network: jasmine.SpyObj<ProjectDataDialogNetworkService>;
  let dialogRef: { close: jasmine.Spy };
  let matDialogOpen: jasmine.Spy;
  let snackBarOpen: jasmine.Spy;

  const setup = async (action: ProjectDataDialogAction, project?: Project) => {
    network = jasmine.createSpyObj<ProjectDataDialogNetworkService>('ProjectDataDialogNetworkService', ['addProject', 'editProject', 'deleteProject']);
    dialogRef = { close: jasmine.createSpy('close') };

    await TestBed.configureTestingModule({
      imports: [ProjectDataDialogComponent],
      providers: [
        { provide: ProjectDataDialogNetworkService, useValue: network },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { action, project } },
      ],
    }).compileComponents();

    matDialogOpen = spyOn(TestBed.inject(MatDialog), 'open');
    snackBarOpen = spyOn(TestBed.inject(MatSnackBar), 'open');

    fixture = TestBed.createComponent(ProjectDataDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  const saveButton = () => fixture.nativeElement.querySelector('.btn.primary') as HTMLButtonElement;

  it('renders the add form with empty fields and an editable project id', async () => {
    await setup(ProjectDataDialogAction.ADD);

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.dialog-title')!.textContent).toContain('Add new project');
    expect(component.projectIdFormControl.value).toBe('');
    expect(component.projectIdFormControl.disabled).toBeFalse();
    expect(component.groupFormControl.value).toBe('');
    expect(component.serviceAccountFormControl.value).toBe('');
    expect(saveButton().disabled).withContext('required fields are empty').toBeTrue();
  });

  it('renders the edit form prefilled, with the project id disabled', async () => {
    await setup(ProjectDataDialogAction.EDIT, PROJECT);

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.dialog-title')!.textContent).toContain('Edit project');
    expect(component.projectIdFormControl.value).toBe('proj-x');
    expect(component.projectIdFormControl.disabled).withContext('the project id cannot be changed').toBeTrue();
    expect(component.groupFormControl.value).toBe('Platform');
    expect(component.serviceAccountFormControl.value).toBe('sa@proj-x.iam.gserviceaccount.com');
  });

  it('renders the delete confirmation with the project id and no form', async () => {
    await setup(ProjectDataDialogAction.DELETE, PROJECT);

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.dialog-title')!.textContent).toContain('Delete project');
    expect(el.textContent).toContain('proj-x');
    expect(el.querySelector('input')).toBeNull();
  });

  it('enables saving once the required add fields are filled', async () => {
    await setup(ProjectDataDialogAction.ADD);

    expect(saveButton().disabled).toBeTrue();

    component.projectIdFormControl.setValue('new-proj');
    fixture.detectChanges();
    expect(saveButton().disabled).withContext('service account still missing').toBeTrue();

    component.serviceAccountFormControl.setValue('collector@new-proj.iam.gserviceaccount.com');
    fixture.detectChanges();
    expect(saveButton().disabled).toBeFalse();
  });

  it('rejects an invalid service account email with a visible error', async () => {
    await setup(ProjectDataDialogAction.ADD);

    component.projectIdFormControl.setValue('new-proj');
    component.serviceAccountFormControl.setValue('not-an-email');
    component.serviceAccountFormControl.markAsTouched();
    fixture.detectChanges();

    expect(saveButton().disabled).toBeTrue();
    expect((fixture.nativeElement as HTMLElement).textContent)
      .toContain('Enter a valid service account email address');
  });

  it('keeps saving disabled in edit mode until something changes', async () => {
    await setup(ProjectDataDialogAction.EDIT, PROJECT);

    expect(saveButton().disabled).withContext('nothing was changed').toBeTrue();

    component.serviceAccountFormControl.setValue('changed@proj-x.iam.gserviceaccount.com');
    fixture.detectChanges();
    expect(saveButton().disabled).toBeFalse();
  });
  it('adds the project and closes the dialog with the updated list', async () => {
    await setup(ProjectDataDialogAction.ADD);
    component.projectIdFormControl.setValue('new-proj');
    component.serviceAccountFormControl.setValue('collector@new-proj.iam.gserviceaccount.com');
    network.addProject.and.returnValue(of({ projects: UPDATED_PROJECTS }));
    fixture.detectChanges();

    saveButton().click();

    expect(network.addProject).toHaveBeenCalledWith({
      projectId: 'new-proj',
      group: '',
      serviceAccount: 'collector@new-proj.iam.gserviceaccount.com',
    });
    expect(dialogRef.close).toHaveBeenCalledWith(UPDATED_PROJECTS);
  });

  it('opens the service-account error dialog on a validation issue and retries the submission', async () => {
    await setup(ProjectDataDialogAction.ADD);
    component.projectIdFormControl.setValue('new-proj');
    component.serviceAccountFormControl.setValue('collector@new-proj.iam.gserviceaccount.com');
    network.addProject.and.returnValues(
      of({
        issues: {
          issueProjectIdConflict: false,
          issueServiceAccountError: true,
          serviceAccountIssue: 'MISSING_IMPERSONATION_PERMISSION',
          portalServiceAccount: 'portal@corp.iam.gserviceaccount.com',
        },
      }),
      of({ projects: UPDATED_PROJECTS }),
    );
    matDialogOpen.and.returnValue({ afterClosed: () => of('retry') } as unknown as MatDialogRef<ServiceAccountErrorDialogComponent>);
    fixture.detectChanges();

    saveButton().click();

    expect(matDialogOpen).toHaveBeenCalledWith(
      ServiceAccountErrorDialogComponent,
      jasmine.objectContaining({ disableClose: true, panelClass: 'service-account-error-dialog' }));
    const openedData = (matDialogOpen.calls.mostRecent().args[1] as { data: unknown }).data;
    expect(openedData).toEqual(jasmine.objectContaining({
      serviceAccountIssue: 'MISSING_IMPERSONATION_PERMISSION',
      projectId: 'new-proj',
      serviceAccount: 'collector@new-proj.iam.gserviceaccount.com',
      portalServiceAccount: 'portal@corp.iam.gserviceaccount.com',
    }));
    // The error dialog answered 'retry', so the form was re-submitted and succeeded.
    expect(network.addProject).toHaveBeenCalledTimes(2);
    expect(dialogRef.close).toHaveBeenCalledWith(UPDATED_PROJECTS);
  });

  it('shows a snackbar for a project id conflict without closing the dialog', async () => {
    await setup(ProjectDataDialogAction.ADD);
    component.projectIdFormControl.setValue('proj-x');
    component.serviceAccountFormControl.setValue('collector@proj-x.iam.gserviceaccount.com');
    network.addProject.and.returnValue(of({
      issues: { issueProjectIdConflict: true, issueServiceAccountError: false },
    }));
    fixture.detectChanges();

    saveButton().click();

    expect(snackBarOpen).toHaveBeenCalledWith('A project with this project ID already exists.', 'OK', jasmine.anything());
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('edits the project and closes the dialog with the updated list', async () => {
    await setup(ProjectDataDialogAction.EDIT, PROJECT);
    component.serviceAccountFormControl.setValue('new@proj-x.iam.gserviceaccount.com');
    network.editProject.and.returnValue(of({ projects: UPDATED_PROJECTS }));
    fixture.detectChanges();

    saveButton().click();

    expect(network.editProject).toHaveBeenCalledWith({
      projectId: 'proj-x',
      group: 'Platform',
      serviceAccount: 'new@proj-x.iam.gserviceaccount.com',
    });
    expect(dialogRef.close).toHaveBeenCalledWith(UPDATED_PROJECTS);
  });

  it('deletes the project and closes the dialog with the updated list', async () => {
    await setup(ProjectDataDialogAction.DELETE, PROJECT);
    network.deleteProject.and.returnValue(of(UPDATED_PROJECTS));

    (fixture.nativeElement.querySelector('.btn.danger') as HTMLButtonElement).click();

    expect(network.deleteProject).toHaveBeenCalledWith('proj-x');
    expect(dialogRef.close).toHaveBeenCalledWith(UPDATED_PROJECTS);
  });

  it('shows a snackbar with the error and re-enables saving when the request fails', async () => {
    await setup(ProjectDataDialogAction.ADD);
    component.projectIdFormControl.setValue('new-proj');
    component.serviceAccountFormControl.setValue('collector@new-proj.iam.gserviceaccount.com');
    network.addProject.and.returnValue(throwError(() => new Error('network down')));
    fixture.detectChanges();

    saveButton().click();

    expect(snackBarOpen).toHaveBeenCalledWith('network down', 'OK', jasmine.anything());
    expect(component.isRequestOngoing).toBeFalse();
    expect(saveButton().disabled).toBeFalse();
  });
});


