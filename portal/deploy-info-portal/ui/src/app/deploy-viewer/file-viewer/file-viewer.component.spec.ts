import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { provideHighlightOptions } from 'ngx-highlightjs';

import { FileViewerComponent } from './file-viewer.component';
import { DeployContentNetworkService } from '../deploy-content.network.service';
import { DeployViewerService } from '../deploy-viewer.component';
import { DeployContent } from '../content-model';
import { VersionEntry } from '../tree-model';

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
      gitlog: 'commit 1a2b3c ...',
      gitstatus: 'On branch main ...',
      changes: [{ filepath: '/src/main.kt', content: 'diff --git a/src/main.kt ...' }],
    },
    extras: [{ filepath: '/docs/readme.md', content: '# readme' }],
  };
  const EMPTY_CONTENT: DeployContent = { git: null, extras: [] };

  beforeEach(async () => {
    networkService = jasmine.createSpyObj('DeployContentNetworkService', ['getContent']);
    networkService.getContent.and.returnValue(of(FULL_CONTENT));

    await TestBed.configureTestingModule({
      imports: [FileViewerComponent],
      providers: [
        { provide: DeployContentNetworkService, useValue: networkService },
        provideHighlightOptions({
          fullLibraryLoader: () => import('highlight.js'),
          lineNumbersLoader: () => import('ngx-highlightjs/line-numbers'),
        }),
      ],
    })
    .compileComponents();

    fixture = TestBed.createComponent(FileViewerComponent);
    component = fixture.componentInstance;
    deployViewerService = TestBed.inject(DeployViewerService);
    fixture.detectChanges();
  });

  const select = (version: VersionEntry) => {
    deployViewerService.newDeployClickedEvent({ project: 'proj-qa', service: 'web', version });
  };

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show nothing until a version is selected', () => {
    expect(component.selection).toBeNull();
    expect(networkService.getContent).not.toHaveBeenCalled();
  });

  it('should fetch the selected version\'s content and render the header from the tree', () => {
    select(VERSION);

    expect(networkService.getContent).toHaveBeenCalledWith(VERSION.storageFolder);
    expect(component.content).toEqual(FULL_CONTENT);
    expect(component.isLoading).toBeFalse();

    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const header = element.querySelector('.deploy-id')!.textContent!;
    expect(header).toContain('proj-qa');
    expect(header).toContain('web');
    expect(header).toContain('v42');
    expect(element.querySelector('.link')!.textContent).toContain(VERSION.url!);
    expect(element.querySelector('.author')!.textContent).toContain(VERSION.author);
    expect(element.querySelector('.date')!.textContent).toContain(new Date(VERSION.timestamp).toUTCString());
  });

  it('should render the GIT expansion panels (log, status, changes)', () => {
    select(VERSION);
    fixture.detectChanges();

    const panels = (fixture.nativeElement as HTMLElement).querySelectorAll('mat-expansion-panel');
    expect(panels.length).toEqual(3);
    expect(panels[0].textContent).toContain('git log');
    expect(panels[1].textContent).toContain('git status');
    expect(panels[2].textContent).toContain('/src/main.kt');
  });

  it('should show the no-data alert when there is no git data and no extras', () => {
    networkService.getContent.and.returnValue(of(EMPTY_CONTENT));
    select(VERSION);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(component.content).toEqual(EMPTY_CONTENT);
    expect(element.querySelectorAll('.no-data-alert').length).toEqual(1); // the GIT tab is the active one
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

    pending.next(FULL_CONTENT); // late response for the superseded selection

    expect(component.selection!.version).toEqual(VERSION_2);
    expect(component.content).toEqual(EMPTY_CONTENT);
    expect(component.isLoading).toBeFalse();
  });
});