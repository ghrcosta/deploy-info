import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { of, Subject, throwError } from 'rxjs';

import { FileViewerComponent } from './file-viewer.component';
import { DeployContentNetworkService } from '../deploy-content.network.service';
import { DeployViewerService } from '../deploy-viewer.service';
import { DeployContent } from '../content-model';
import { VersionEntry } from '../tree-model';

// Minimal icon stand-ins: the real ones are registered by AppComponent at
// runtime, which the specs do not include (why these are needed at all: deploy-viewer.component.spec.ts).
const STUB_SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M0 0h24v24H0z"/></svg>';
const registerStubIcons = (registry: MatIconRegistry, sanitizer: DomSanitizer) =>
    ['project', 'GAE', 'RUN', 'deployed_code', 'deployed_code_alert', 'no_data', 'search', 'chevron_right', 'link', 'arrow_outward', 'person', 'schedule']
        .forEach(name => registry.addSvgIconLiteral(name, sanitizer.bypassSecurityTrustHtml(STUB_SVG)));

describe('FileViewerComponent', () => {
  let component: FileViewerComponent;
  let fixture: ComponentFixture<FileViewerComponent>;
  let networkService: jasmine.SpyObj<DeployContentNetworkService>;
  let deployViewerService: DeployViewerService;

  const VERSION: VersionEntry = {
    id: 'proj-qa_GAE_-_web_v42', name: 'v42', type: 'GAE', location: null,
    url: 'https://v42-dot-web.proj-qa.appspot.com', author: 'jdoe@example.com',
    timestamp: 1735689600000, storageFolder: 'jdoe_GAE_1735689600000',
  };
  const VERSION_2: VersionEntry = { ...VERSION, id: 'proj-qa_GAE_-_web_v7', name: 'v7', storageFolder: 'jdoe_GAE_1735689000000' };

  const FULL_CONTENT: DeployContent = {
    git: {
      gitlog: 'commit 1a2b3c ...\n',
      gitstatus: 'On branch main ...',
      changes: [
        { filepath: '/src/main.kt', content: 'diff --git a/x b/x\n@@ -1,1 +1,1 @@\n-old line\n+new line\nkept line\n', status: 'modified' },
        { filepath: '/src/new-file.kt', content: 'full file content\n', status: 'new' },
        { filepath: '/src/untracked-file.kt', content: 'untracked file content\n', status: 'untracked' },
      ],
    },
    extras: [{ filepath: '/docs/readme.md', content: '# readme\n', status: 'modified' }],
  };
  const EMPTY_CONTENT: DeployContent = { git: null, extras: [] };

  beforeEach(async () => {
    networkService = jasmine.createSpyObj('DeployContentNetworkService', ['getContent']);
    networkService.getContent.and.returnValue(of(FULL_CONTENT));

    await TestBed.configureTestingModule({
      imports: [FileViewerComponent],
      providers: [
        { provide: DeployContentNetworkService, useValue: networkService },
      ],
    })
    .compileComponents();

    registerStubIcons(TestBed.inject(MatIconRegistry), TestBed.inject(DomSanitizer));

    fixture = TestBed.createComponent(FileViewerComponent);
    component = fixture.componentInstance;
    deployViewerService = TestBed.inject(DeployViewerService);
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const select = (version: VersionEntry) => {
    deployViewerService.newDeployClickedEvent({ project: 'proj-qa', service: 'web', version });
  };
  const blockWithPath = (path: string) =>
    Array.from(element().querySelectorAll('.file-block'))
      .find(block => block.querySelector('.file-path')!.textContent!.includes(path))!;

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show the no-selection empty state until a version is selected', () => {
    expect(component.selection).toBeNull();
    expect(networkService.getContent).not.toHaveBeenCalled();
    expect(element().querySelector('.empty-state')!.textContent).toContain('No deploy selected');
    expect(element().querySelector('.deploy-card')).toBeNull();
  });

  it('should fetch the selected version\'s content and render the header from the tree', () => {
    select(VERSION);

    expect(networkService.getContent).toHaveBeenCalledWith(VERSION.storageFolder);
    expect(component.content).toEqual(FULL_CONTENT);
    expect(component.isLoading).toBeFalse();

    fixture.detectChanges();

    const el = element();
    const card = el.querySelector('.deploy-card')!.textContent!;
    expect(card).toContain('proj-qa');
    expect(card).toContain('web');
    expect(card).toContain('v42');

    const link = el.querySelector('.meta-link') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toEqual(VERSION.url!);
    expect(link.textContent).toContain(VERSION.url);
    expect(el.querySelector('.deploy-meta')!.textContent).toContain(VERSION.author);
    expect((el.querySelector('.meta-time') as HTMLElement).getAttribute('title')).toContain('UTC');
  });

  it('should render the GIT file blocks (log, status, changes) as the default tab', () => {
    select(VERSION);
    fixture.detectChanges();

    const blocks = element().querySelectorAll('.file-block');
    expect(blocks.length).toEqual(5);
    expect(blocks[0].querySelector('.file-path')!.textContent).toContain('git log');
    expect(blocks[1].querySelector('.file-path')!.textContent).toContain('git status');
    expect(blocks[2].querySelector('.file-path')!.textContent).toContain('/src/main.kt');
    expect(blocks[3].querySelector('.file-path')!.textContent).toContain('/src/new-file.kt');
    expect(blocks[4].querySelector('.file-path')!.textContent).toContain('/src/untracked-file.kt');
    expect(element().querySelectorAll('[aria-label="Extras"] .file-block').length).toEqual(0);
  });

  it('should mark the file blocks with the git status chips (extras get their own)', () => {
    select(VERSION);
    fixture.detectChanges();

    const blocks = element().querySelectorAll('.file-block');
    expect(blocks[0].querySelector('.status-chip')).toBeNull(); // git log carries no chip
    expect(blocks[1].querySelector('.status-chip')).toBeNull(); // git status carries no chip
    expect(blocks[2].querySelector('.status-chip.st-modified')!.textContent).toContain('Modified');
    expect(blocks[2].querySelector('.status-chip.st-new')).toBeNull();
    expect(blocks[3].querySelector('.status-chip.st-new')!.textContent).toContain('New');
    expect(blocks[4].querySelector('.status-chip.st-untracked')!.textContent).toContain('Untracked');
  });

  it('should render numbered code lines and the diff tints of changed files', () => {
    select(VERSION);
    fixture.detectChanges();

    const logLines = blockWithPath('git log').querySelectorAll('.cl');
    expect(logLines.length).toEqual(1);
    expect(logLines[0].querySelector('.cln')!.textContent).toEqual('1');
    expect(logLines[0].querySelector('.clt')!.textContent).toContain('commit 1a2b3c');
    expect(logLines[0].className).not.toContain('cl-add');

    const diffLines = blockWithPath('/src/main.kt').querySelectorAll('.cl');
    expect(diffLines.length).toEqual(5);
    expect(diffLines[1].classList).toContain('cl-hunk');
    expect(diffLines[2].classList).toContain('cl-del');
    expect(diffLines[3].classList).toContain('cl-add');
    expect(diffLines[4].classList).not.toContain('cl-');
    expect(diffLines[4].querySelector('.cln')!.textContent).toEqual('5');

    // Changed files render diff-tinted, but a line without +/-/@@ prefix stays plain
    expect(blockWithPath('/src/new-file.kt').querySelectorAll('.cl-add, .cl-del, .cl-hunk').length).toEqual(0);
  });

  it('should show the tab counts and switch between the Git and Extras panels', () => {
    select(VERSION);
    fixture.detectChanges();

    const tabs = element().querySelectorAll('.tab-btn');
    expect(tabs[0].textContent).toContain('5');
    expect(tabs[1].textContent).toContain('1');
    expect(tabs[0].classList.contains('active')).toBeTrue();

    (tabs[1] as HTMLButtonElement).click();
    fixture.detectChanges();

    const activeTabs = element().querySelectorAll('.tab-btn');
    expect(activeTabs[1].classList.contains('active')).toBeTrue();
    expect(activeTabs[0].classList.contains('active')).toBeFalse();
    const extrasPanel = element().querySelector('[aria-label="Extras"]')!;
    expect(extrasPanel.querySelector('.file-path')!.textContent).toContain('/docs/readme.md');
    expect(extrasPanel.querySelector('.status-chip.st-extra')!.textContent).toContain('Extra');
  });

  it('should render the file paths exactly as received, keeping the leading slash', () => {
    select(VERSION);
    fixture.detectChanges();

    // The <bdi> wrapper isolates the path text (the RTL ellipsis trick on .file-path would
    // otherwise visually move the leading "/" to the end of the displayed path).
    const changePath = element().querySelector('[aria-label="Git"] .file-path bdi')!;
    expect(changePath.textContent).toEqual('/src/main.kt');

    // The Extras panel only renders when its tab is active.
    (element().querySelectorAll('.tab-btn')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    const extraPath = element().querySelector('[aria-label="Extras"] .file-path bdi')!;
    expect(extraPath.textContent).toEqual('/docs/readme.md');
  });


  it('should render collapsible file blocks, closed by default and without copy buttons', () => {
    select(VERSION);
    fixture.detectChanges();

    const blocks = Array.from(element().querySelectorAll('details.file-block')) as HTMLDetailsElement[];
    expect(blocks.length).toEqual(5);
    blocks.forEach(block => expect(block.open).withContext('closed by default').toBeFalse());
    expect(element().querySelector('.copy-btn')).withContext('no copy button').toBeNull();
  });

  it('should expand a file block to reveal its code when the header is clicked', () => {
    select(VERSION);
    fixture.detectChanges();

    const block = blockWithPath('/src/main.kt') as HTMLDetailsElement;
    expect(block.open).toBeFalse();
    expect(block.querySelector('.code')!.checkVisibility()).toBeFalse(); // closed: code hidden

    (block.querySelector('.file-head') as HTMLElement).click();
    expect(block.open).toBeTrue();
    expect(block.querySelectorAll('.cl').length).toEqual(5);
  });

  it('should show the no-data empty state when there is no git data and no extras', () => {
    networkService.getContent.and.returnValue(of(EMPTY_CONTENT));
    select(VERSION);
    fixture.detectChanges();

    const el = element();
    expect(component.content).toEqual(EMPTY_CONTENT);
    expect(el.querySelector('[aria-label="Git"] .empty-state')!.textContent).toContain('No data found');

    (el.querySelectorAll('.tab-btn')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('[aria-label="Extras"] .empty-state')!.textContent)
      .toContain('No extra files were collected');
  });

  it('should show the not-found state when the folder is gone (404)', () => {
    networkService.getContent.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    select(VERSION);
    fixture.detectChanges();

    expect(component.isLoading).toBeFalse();
    expect(component.notFound).toBeTrue();
    expect(component.loadFailed).toBeFalse();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('not found');
  });

  it('should show the error state when the content fetch fails (e.g. 502)', () => {
    networkService.getContent.and.returnValue(throwError(() => new HttpErrorResponse({ status: 502 })));
    select(VERSION);
    fixture.detectChanges();

    expect(component.isLoading).toBeFalse();
    expect(component.loadFailed).toBeTrue();
    expect(component.notFound).toBeFalse();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Failed to load');
  });

  it('should clear the previous content and ignore stale responses when another version is selected', () => {
    const pending = new Subject<DeployContent>();
    networkService.getContent.and.returnValues(pending, of(EMPTY_CONTENT));

    select(VERSION);
    expect(component.isLoading).toBeTrue();
    expect(component.content).toBeNull();

    select(VERSION_2);
    expect(component.content).toEqual(EMPTY_CONTENT);
    expect(component.activeTab).toEqual('git');

    pending.next(FULL_CONTENT); // late response for the superseded selection

    expect(component.selection!.version).toEqual(VERSION_2);
    expect(component.content).toEqual(EMPTY_CONTENT);
    expect(component.isLoading).toBeFalse();
  });
});
