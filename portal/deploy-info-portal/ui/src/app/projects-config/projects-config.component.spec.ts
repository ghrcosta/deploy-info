import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { of } from 'rxjs';

import { ProjectsConfigComponent } from './projects-config.component';
import { ProjectsConfigNetworkService } from './projects-config.network.service';
import { ProjectDataDialogAction, ProjectDataDialogComponent } from './project-data-dialog/project-data-dialog.component';

const PROJECTS = [
  { projectId: 'acme-web-prod', group: 'Platform', serviceAccount: 'collector@acme-web-prod.iam.gserviceaccount.com' },
  { projectId: 'acme-sandbox', group: '', serviceAccount: 'collector@acme-sandbox.iam.gserviceaccount.com' }
];

// Minimal icon stand-ins: the real ones are registered by AppComponent at
// runtime, which the specs do not include (why these are needed at all: deploy-viewer.component.spec.ts).
const ICON_STUB =
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="M0-960h960V0H0Z"/></svg>';
const ICON_NAMES = ['project', 'add', 'edit', 'delete', 'group', 'service_account'];

describe('ProjectsConfigComponent', () => {
  let component: ProjectsConfigComponent;
  let fixture: ComponentFixture<ProjectsConfigComponent>;
  let network: jasmine.SpyObj<ProjectsConfigNetworkService>;
  let dialogOpenSpy: jasmine.Spy;

  beforeEach(async () => {
    network = jasmine.createSpyObj<ProjectsConfigNetworkService>('ProjectsConfigNetworkService', ['getProjects']);
    network.getProjects.and.returnValue(of(PROJECTS));

    await TestBed.configureTestingModule({
      imports: [ProjectsConfigComponent],
      providers: [{ provide: ProjectsConfigNetworkService, useValue: network }]
    })
    .compileComponents();

    const iconRegistry = TestBed.inject(MatIconRegistry);
    const sanitizer = TestBed.inject(DomSanitizer);
    for (const name of ICON_NAMES) {
      iconRegistry.addSvgIconLiteral(name, sanitizer.bypassSecurityTrustHtml(ICON_STUB));
    }

    dialogOpenSpy = spyOn(TestBed.inject(MatDialog), 'open')
      .and.returnValue({ afterClosed: () => of(undefined) } as unknown as MatDialogRef<ProjectDataDialogComponent>);

    fixture = TestBed.createComponent(ProjectsConfigComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders a card per watched project with its fields', () => {
    const cards = fixture.nativeElement.querySelectorAll('.project-card');
    expect(cards.length).toBe(PROJECTS.length);
    expect(cards[0].querySelector('h3').textContent).toContain('acme-web-prod');
    const fields = cards[0].querySelectorAll('.project-field dd');
    expect(fields[0].textContent).toContain('Platform');
    expect(fields[1].textContent).toContain('collector@acme-web-prod.iam.gserviceaccount.com');
    // A project without a group shows the muted placeholder.
    expect(cards[1].querySelectorAll('.project-field dd')[0].textContent).toContain('No group');
  });

  it('pluralizes the watched-projects subtitle', () => {
    expect((fixture.nativeElement.querySelector('.settings-sub') as HTMLElement).textContent)
      .toContain('2 projects watched for deploys');

    network.getProjects.and.returnValue(of([PROJECTS[0]]));
    const singleFixture = TestBed.createComponent(ProjectsConfigComponent);
    singleFixture.detectChanges();
    expect((singleFixture.nativeElement.querySelector('.settings-sub') as HTMLElement).textContent)
      .toContain('1 project watched for deploys');
  });

  it('opens the add dialog from the header button', () => {
    (fixture.nativeElement.querySelector('.settings-head .btn.primary') as HTMLElement).click();

    expect(dialogOpenSpy).toHaveBeenCalledWith(jasmine.anything(), jasmine.objectContaining({
      disableClose: true,
      data: jasmine.objectContaining({ action: ProjectDataDialogAction.ADD })
    }));
  });

  it("opens the edit dialog from a card's edit button", () => {
    (fixture.nativeElement.querySelector('.project-actions .icon-btn') as HTMLElement).click();

    expect(dialogOpenSpy).toHaveBeenCalledWith(jasmine.anything(), jasmine.objectContaining({
      data: jasmine.objectContaining({ action: ProjectDataDialogAction.EDIT, project: PROJECTS[0] })
    }));
  });

  it("opens the delete dialog from a card's delete button", () => {
    (fixture.nativeElement.querySelector('.project-actions .icon-btn.danger') as HTMLElement).click();

    expect(dialogOpenSpy).toHaveBeenCalledWith(jasmine.anything(), jasmine.objectContaining({
      data: jasmine.objectContaining({ action: ProjectDataDialogAction.DELETE, project: PROJECTS[0] })
    }));
  });

  it('replaces the project list when a dialog closes with an updated list', () => {
    const updated = [{ projectId: 'new-project', group: undefined, serviceAccount: 'collector@new-project.iam.gserviceaccount.com' }];
    dialogOpenSpy.and.returnValue({ afterClosed: () => of(updated) } as unknown as MatDialogRef<ProjectDataDialogComponent>);

    (fixture.nativeElement.querySelector('.settings-head .btn.primary') as HTMLElement).click();

    expect(component.projects).toEqual(updated);
  });

  it('shows the empty state when no projects are watched', () => {
    network.getProjects.and.returnValue(of([]));
    const emptyFixture = TestBed.createComponent(ProjectsConfigComponent);
    emptyFixture.detectChanges();

    expect(emptyFixture.nativeElement.querySelector('.settings-empty')).toBeTruthy();
    expect(emptyFixture.nativeElement.querySelector('.project-grid')).toBeNull();
  });
});
