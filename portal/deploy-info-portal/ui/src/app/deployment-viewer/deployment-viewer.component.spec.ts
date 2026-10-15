import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { of, Subject } from 'rxjs';

import { DeploymentViewerComponent } from './deployment-viewer.component';
import { DeploymentViewerService } from './deployment-viewer.service';
import { DeploymentTreeNetworkService } from './deployment-tree.network.service';
import { DeploymentContentNetworkService } from './deployment-content.network.service';
import { GroupEntry } from './tree-model';
import { DeploymentContent } from './content-model';

// Minimal stand-ins for the SVG icons the navigator and file viewer render;
// the real ones are registered by AppComponent at runtime, which the specs
// don't include. Without these, MatIconRegistry logs an error for every icon
// the templates fetch via URL.
const STUB_SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M0 0h24v24H0z"/></svg>';
const registerStubIcons = (registry: MatIconRegistry, sanitizer: DomSanitizer) =>
    ['search', 'chevron_right', 'project', 'GAE', 'RUN', 'deployed_code', 'deployed_code_alert', 'no_data', 'link', 'arrow_outward', 'person', 'schedule']
        .forEach(name => registry.addSvgIconLiteral(name, sanitizer.bypassSecurityTrustHtml(STUB_SVG)));

const TREE: GroupEntry[] = [
  {
    name: 'PROD',
    projects: [
      {
        projectId: 'Project C',
        services: [
          {
            name: 'web',
            type: 'GAE',
            versions: [
              { id: 'proj-qa_GAE_-_web_v42', name: 'v42', type: 'GAE', location: null, url: null, author: 'a@b.c', timestamp: 123, storageFolder: 'folder-42' },
            ],
          },
        ],
      },
    ],
  },
];

const CONTENT: DeploymentContent = {
  git: {
    gitlog: 'commit abc123\nAuthor: a@b.c\n',
    gitstatus: ' M src/app/app.component.ts\n',
    changes: [
      { filepath: 'src/app/app.component.ts', content: '@@ -1 +1 @@\n-old line\n+new line\n', status: 'modified' },
    ],
  },
  extras: [],
};

describe('DeploymentViewerComponent', () => {
  let component: DeploymentViewerComponent;
  let fixture: ComponentFixture<DeploymentViewerComponent>;
  let contentNetwork: jasmine.SpyObj<DeploymentContentNetworkService>;
  let treeNetwork: jasmine.SpyObj<DeploymentTreeNetworkService>;

  beforeEach(async () => {
    treeNetwork = jasmine.createSpyObj<DeploymentTreeNetworkService>('DeploymentTreeNetworkService', ['getTree']);
    treeNetwork.getTree.and.returnValue(of(TREE));
    contentNetwork = jasmine.createSpyObj<DeploymentContentNetworkService>('DeploymentContentNetworkService', ['getContent']);
    contentNetwork.getContent.and.returnValue(of(CONTENT));

    await TestBed.configureTestingModule({
      imports: [DeploymentViewerComponent],
      providers: [
        { provide: DeploymentTreeNetworkService, useValue: treeNetwork },
        { provide: DeploymentContentNetworkService, useValue: contentNetwork },
      ],
    })
    .compileComponents();

    const registry = TestBed.inject(MatIconRegistry);
    const sanitizer = TestBed.inject(DomSanitizer);
    registerStubIcons(registry, sanitizer);

    fixture = TestBed.createComponent(DeploymentViewerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render the shell grid: navigator column next to the viewer column', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.shell')).toBeTruthy();
    expect(el.querySelector('aside.navigator#navigator')).toBeTruthy();
    expect(el.querySelector('main.viewer')).toBeTruthy();
    expect(el.querySelector('aside.navigator deployment-navigator')).toBeTruthy();
    expect(el.querySelector('main.viewer file-viewer')).toBeTruthy();
  });

  it('shows the no-selection empty state until a version is picked', () => {
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No deployment selected');
    expect(contentNetwork.getContent).not.toHaveBeenCalled();
  });

  const versionButton = () =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.nav-version'))
      .find(btn => btn.textContent!.includes('v42')) as HTMLButtonElement;

  it('wires the navigator selection to the file viewer and keys the content fetch on the storage folder', () => {
    const button = versionButton();
    expect(button).withContext('the navigator rendered the version from the tree').toBeTruthy();

    button.click();
    fixture.detectChanges();

    const viewer = fixture.debugElement.query(By.css('file-viewer')).componentInstance;
    expect(viewer.selection.project).toBe('Project C');
    expect(viewer.selection.service).toBe('web');
    expect(viewer.selection.version.id).toBe('proj-qa_GAE_-_web_v42');
    expect(contentNetwork.getContent).toHaveBeenCalledWith('folder-42');
  });

  it('renders the picked deployment\'s header from the tree and its content blocks', () => {
    versionButton().click();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const crumbs = el.querySelectorAll('.deployment-crumbs .chip.crumb');
    expect(crumbs[0].textContent).toContain('Project C');
    expect(crumbs[1].textContent).toContain('web');
    expect(crumbs[2].textContent).toContain('v42');
    expect(el.querySelector('.deployment-card .meta-author')!.textContent).toContain('a@b.c');

    // git log block + git status block + one change block
    expect(el.querySelectorAll('details.file-block').length).toBe(3);
    expect(el.textContent).toContain('src/app/app.component.ts');
    expect(el.textContent).not.toContain('No deployment selected');
  });

  describe('while the tree request is in flight', () => {
    let treeSubject: Subject<GroupEntry[]>;

    beforeEach(() => {
      treeSubject = new Subject<GroupEntry[]>();
      treeNetwork.getTree.and.returnValue(treeSubject);
    });

    const pendingFixture = () => {
      const pending = TestBed.createComponent(DeploymentViewerComponent);
      pending.detectChanges();
      return pending;
    };

    it('hides the shell contents and shows the loading indicator until the tree request completes', () => {
      const pending = pendingFixture();
      const el = pending.nativeElement as HTMLElement;
      expect(el.querySelector('.shell-loading mat-spinner')).toBeTruthy();
      expect(el.querySelector('.shell')).toBeNull();
      expect(el.textContent).not.toContain('No deployments found');
      expect(el.textContent).not.toContain('No deployment selected');
      expect(contentNetwork.getContent).not.toHaveBeenCalled();

      treeSubject.next(TREE);
      pending.detectChanges();

      expect(el.querySelector('.shell-loading')).toBeNull();
      expect(el.querySelector('.shell')).toBeTruthy();
      expect(el.textContent).toContain('No deployment selected');
    });

    it('shows the navigator empty state when the tree request fails', () => {
      const pending = pendingFixture();
      treeSubject.error(new Error('boom'));
      pending.detectChanges();

      const el = pending.nativeElement as HTMLElement;
      expect(el.querySelector('.shell-loading')).toBeNull();
      expect(el.querySelector('.shell')).toBeTruthy();
      expect(el.textContent).toContain('No deployments found');
    });
  });
});

