import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { of } from 'rxjs';

import { DeployViewerComponent } from './deploy-viewer.component';
import { DeployViewerService } from './deploy-viewer.service';
import { DeployTreeNetworkService } from './deploy-tree.network.service';
import { DeployContentNetworkService } from './deploy-content.network.service';
import { GroupEntry } from './tree-model';
import { DeployContent } from './content-model';

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

const CONTENT: DeployContent = {
  git: {
    gitlog: 'commit abc123\nAuthor: a@b.c\n',
    gitstatus: ' M src/app/app.component.ts\n',
    changes: [
      { filepath: 'src/app/app.component.ts', content: '@@ -1 +1 @@\n-old line\n+new line\n', status: 'modified' },
    ],
  },
  extras: [],
};

describe('DeployViewerComponent', () => {
  let component: DeployViewerComponent;
  let fixture: ComponentFixture<DeployViewerComponent>;
  let contentNetwork: jasmine.SpyObj<DeployContentNetworkService>;

  beforeEach(async () => {
    const treeNetwork = jasmine.createSpyObj<DeployTreeNetworkService>('DeployTreeNetworkService', ['getTree']);
    treeNetwork.getTree.and.returnValue(of(TREE));
    contentNetwork = jasmine.createSpyObj<DeployContentNetworkService>('DeployContentNetworkService', ['getContent']);
    contentNetwork.getContent.and.returnValue(of(CONTENT));

    await TestBed.configureTestingModule({
      imports: [DeployViewerComponent],
      providers: [
        { provide: DeployTreeNetworkService, useValue: treeNetwork },
        { provide: DeployContentNetworkService, useValue: contentNetwork },
      ],
    })
    .compileComponents();

    const registry = TestBed.inject(MatIconRegistry);
    const sanitizer = TestBed.inject(DomSanitizer);
    registerStubIcons(registry, sanitizer);

    fixture = TestBed.createComponent(DeployViewerComponent);
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
    expect(el.querySelector('aside.navigator deploy-navigator')).toBeTruthy();
    expect(el.querySelector('main.viewer file-viewer')).toBeTruthy();
  });

  it('shows the no-selection empty state until a version is picked', () => {
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No deploy selected');
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

  it('renders the picked deploy\'s header from the tree and its content blocks', () => {
    versionButton().click();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const crumbs = el.querySelectorAll('.deploy-crumbs .chip.crumb');
    expect(crumbs[0].textContent).toContain('Project C');
    expect(crumbs[1].textContent).toContain('web');
    expect(crumbs[2].textContent).toContain('v42');
    expect(el.querySelector('.deploy-card .meta-author')!.textContent).toContain('a@b.c');

    // git log block + git status block + one change block
    expect(el.querySelectorAll('details.file-block').length).toBe(3);
    expect(el.textContent).toContain('src/app/app.component.ts');
    expect(el.textContent).not.toContain('No deploy selected');
  });
});

