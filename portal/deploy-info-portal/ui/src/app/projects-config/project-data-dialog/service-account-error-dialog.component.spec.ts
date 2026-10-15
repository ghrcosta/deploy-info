import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';

import { ServiceAccountErrorDialogComponent, ServiceAccountErrorDialogData, ServiceAccountIssueCode } from './service-account-error-dialog.component';

// Minimal icon stand-ins: the real ones are registered by AppComponent at
// runtime, which the specs do not include (why these are needed at all: deployment-viewer.component.spec.ts).
const STUB_SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="M0-960h960V0Z"/></svg>';

const BASE: ServiceAccountErrorDialogData = {
  serviceAccountIssue: '',
  projectId: 'proj-x',
  serviceAccount: 'sa@proj-x.iam.gserviceaccount.com',
  portalServiceAccount: 'portal@corp.iam.gserviceaccount.com',
};

describe('ServiceAccountErrorDialogComponent', () => {
  let fixture: ComponentFixture<ServiceAccountErrorDialogComponent>;
  let component: ServiceAccountErrorDialogComponent;
  let dialogRef: { close: jasmine.Spy };

  const setup = async (data: Partial<ServiceAccountErrorDialogData> = {}) => {
    dialogRef = { close: jasmine.createSpy('close') };

    await TestBed.configureTestingModule({
      imports: [ServiceAccountErrorDialogComponent],
      providers: [
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { ...BASE, ...data } },
      ],
    }).compileComponents();

    const iconRegistry = TestBed.inject(MatIconRegistry);
    const sanitizer = TestBed.inject(DomSanitizer);
    iconRegistry.addSvgIconLiteral('deployed_code_alert', sanitizer.bypassSecurityTrustHtml(STUB_SVG));

    fixture = TestBed.createComponent(ServiceAccountErrorDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  it('names the project and offers no retry for the portal-side issue', async () => {
    await setup({ serviceAccountIssue: ServiceAccountIssueCode.PORTAL_ISSUE });

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.dialog-title')!.textContent).toContain('Failed to validate the service account');
    expect(el.textContent).toContain('proj-x');
    expect(el.textContent).toContain('portal-side problem');
    expect(component.hasInstructions()).toBeFalse();
    expect(el.querySelector('.btn.primary')).withContext('no Retry button for a portal issue').toBeNull();
  });

  it('explains the impersonation issue and builds its gcloud command', async () => {
    await setup({ serviceAccountIssue: ServiceAccountIssueCode.MISSING_IMPERSONATION_PERMISSION });

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('roles/iam.serviceAccountTokenCreator');
    expect(component.hasInstructions()).toBeTrue();
    expect(el.querySelector('.btn.primary')!.textContent).toContain('Retry');

    const command = component.gcloudCommand();
    expect(command).toContain('gcloud iam service-accounts add-iam-policy-binding');
    expect(command).toContain('sa@proj-x.iam.gserviceaccount.com');
    expect(command).toContain('serviceAccount:portal@corp.iam.gserviceaccount.com');
    expect(command).toContain('roles/iam.serviceAccountTokenCreator');
  });

  it('falls back to a placeholder when the portal service account is unknown', async () => {
    await setup({ serviceAccountIssue: ServiceAccountIssueCode.MISSING_IMPERSONATION_PERMISSION, portalServiceAccount: undefined });

    expect(component.gcloudCommand()).toContain('PORTAL_SERVICE_ACCOUNT');
  });

  it('explains the listing-permission issue and builds the appViewer/run.viewer commands', async () => {
    await setup({ serviceAccountIssue: ServiceAccountIssueCode.MISSING_LISTING_PERMISSION });

    const command = component.gcloudCommand();
    expect(command).toContain('gcloud projects add-iam-policy-binding proj-x');
    expect(command).toContain('roles/appengine.appViewer');
    expect(command).toContain('roles/run.viewer');
  });

  it('explains the apis-not-enabled issue and builds the services enable command', async () => {
    await setup({ serviceAccountIssue: ServiceAccountIssueCode.APIS_NOT_ENABLED });

    expect(component.gcloudCommand())
      .toContain('gcloud services enable appengine.googleapis.com run.googleapis.com --project=proj-x');
  });

  it('explains the project-not-found issue and points at gcloud projects list', async () => {
    await setup({ serviceAccountIssue: ServiceAccountIssueCode.PROJECT_NOT_FOUND });

    expect(component.gcloudCommand()).toBe('gcloud projects list');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('project ID');
  });

  it('closes with "retry" from the Retry button and without a result from Close', async () => {
    await setup({ serviceAccountIssue: ServiceAccountIssueCode.MISSING_IMPERSONATION_PERMISSION });

    const el = fixture.nativeElement as HTMLElement;
    (el.querySelector('.btn.primary') as HTMLButtonElement).click();
    expect(dialogRef.close).toHaveBeenCalledTimes(1);
    expect(dialogRef.close.calls.first().args).toEqual(['retry']);

    (el.querySelector('.btn.ghost') as HTMLButtonElement).click();
    expect(dialogRef.close).toHaveBeenCalledTimes(2);
    expect(dialogRef.close.calls.mostRecent().args).toEqual([]);
  });
});
