import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { of } from 'rxjs';

import { DeployNavigatorComponent } from './deploy-navigator.component';
import { DeployTreeNetworkService } from '../deploy-tree.network.service';
import { DeployViewerService } from '../deploy-viewer.service';
import { GroupEntry } from '../tree-model';
import { HomeService } from '../../home/home.service';

// Minimal stand-ins for the SVG icons the navigator renders; the real ones are
// registered by AppComponent at runtime, which the specs don't include.
const STUB_SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M0 0h24v24H0z"/></svg>';
const registerStubIcons = (registry: MatIconRegistry, sanitizer: DomSanitizer) =>
    ['search', 'chevron_right', 'project', 'GAE', 'RUN', 'deployed_code', 'deployed_code_alert']
        .forEach(name => registry.addSvgIconLiteral(name, sanitizer.bypassSecurityTrustHtml(STUB_SVG)));

describe('DeployNavigatorComponent', () => {
  let component: DeployNavigatorComponent;
  let fixture: ComponentFixture<DeployNavigatorComponent>;
  let networkService: jasmine.SpyObj<DeployTreeNetworkService>;
  let deployViewerService: DeployViewerService;

  const TEST_TREE: GroupEntry[] = [
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
                { id: 'proj-qa_GAE_-_web_v42', name: 'v42', type: 'GAE', location: null, url: null, author: 'a@b.c', timestamp: 123, storageFolder: 'folder' },
                { id: 'proj-qa_GAE_-_web_v7', name: 'v7', type: 'GAE', location: null, url: null, author: 'a@b.c', timestamp: 456, storageFolder: 'folder2' },
              ]
            },
          ]
        },
      ]
    },
    {
      name: '',
      projects: [
        {
          projectId: 'Project D',
          services: [
            {
              name: 'worker',
              type: 'RUN',
              versions: [
                { id: 'proj-qa_RUN_eu_worker_v1', name: 'v1', type: 'RUN', location: 'eu', url: null, author: 'a@b.c', timestamp: 789, storageFolder: 'folder3' },
              ]
            },
          ]
        },
      ]
    },
  ];

  beforeEach(async () => {
    document.body.classList.remove('nav-open');

    networkService = jasmine.createSpyObj('DeployTreeNetworkService', ['getTree']);
    networkService.getTree.and.returnValue(of(TEST_TREE));

    await TestBed.configureTestingModule({
      imports: [DeployNavigatorComponent],
      providers: [{ provide: DeployTreeNetworkService, useValue: networkService }],
    })
    .compileComponents();

    registerStubIcons(TestBed.inject(MatIconRegistry), TestBed.inject(DomSanitizer));

    deployViewerService = TestBed.inject(DeployViewerService);
    fixture = TestBed.createComponent(DeployNavigatorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    document.body.classList.remove('nav-open');
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const versionButton = (name: string) =>
    Array.from(element().querySelectorAll('.nav-version'))
      .find(btn => btn.textContent!.includes(name)) as HTMLButtonElement;
  // Note: project <details> also contain their services' text, so the helpers
  // must search the specific node kind.
  const serviceOf = (name: string) =>
    Array.from(element().querySelectorAll('.nav-service'))
      .find(el => el.textContent!.includes(name)) as HTMLDetailsElement;
  const projectOf = (name: string) =>
    Array.from(element().querySelectorAll('.nav-project'))
      .find(el => el.textContent!.includes(name)) as HTMLDetailsElement;

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should fetch the tree and convert it to nodes', () => {
    expect(networkService.getTree).toHaveBeenCalled();
    expect(component.groupList.length).toEqual(2);
    expect(component.groupList[0].name).toEqual('PROD');
    expect(component.groupList[0].projects[0].services[0].versions.length).toEqual(2);
    expect(component.groupList[1].name).toEqual('');
  });

  it('should carry the file-viewer selection (project, service, version) on the version nodes', () => {
    const service = component.groupList[0].projects[0].services[0];
    expect(service.versions[0].selection).toEqual({
      project: 'Project C',
      service: 'web',
      version: TEST_TREE[0].projects[0].services[0].versions[0],
    });
    expect(service.versions[1].selection!.version).toEqual(TEST_TREE[0].projects[0].services[0].versions[1]);
    expect(component.groupList[1].projects[0].services[0].versions[0].selection).toEqual({
      project: 'Project D',
      service: 'worker',
      version: TEST_TREE[1].projects[0].services[0].versions[0],
    });
  });

  it('should render the details tree with group, project, service and version nodes', () => {
    const el = element();
    expect(el.querySelectorAll('.nav-group').length).toEqual(2);
    expect(el.querySelectorAll('.nav-project').length).toEqual(2);
    expect(el.querySelectorAll('.nav-service').length).toEqual(2);
    expect(el.querySelectorAll('.nav-version').length).toEqual(3);

    const serviceSummary = serviceOf('web')!.querySelector('summary')!;
    expect(serviceSummary.textContent).toContain('web');
    expect(serviceSummary.querySelector('.nav-count')!.textContent!.trim()).toEqual('2');

    const version = versionButton('v42');
    expect(version.querySelector('.nav-version-name')!.textContent).toContain('v42');
    expect(version.querySelector('.nav-time')!.textContent).toContain('ago');
    expect(projectOf('Project C').tagName).toEqual('DETAILS');
    expect(el.querySelector('.nav-group-none')!.textContent).toContain('No group');
  });

  it('should emit the selection and mark the clicked version as selected', () => {
    const selections: unknown[] = [];
    deployViewerService.deployClickedEventObservable.subscribe(selection => selections.push(selection));

    const first = versionButton('v42');
    first.click();
    fixture.detectChanges();

    expect(selections).toEqual([component.groupList[0].projects[0].services[0].versions[0].selection]);
    expect(first.classList.contains('selected')).toBeTrue();
    expect(first.getAttribute('aria-current')).toEqual('true');

    const second = versionButton('v1');
    second.click();
    fixture.detectChanges();
    expect(second.classList.contains('selected')).toBeTrue();
    expect(second.getAttribute('aria-current')).toEqual('true');
    expect(first.classList.contains('selected')).toBeFalse();
  });

  it('should close the mobile navigator drawer when a version is clicked', () => {
    document.body.classList.add('nav-open');

    versionButton('v42').click();

    expect(document.body.classList).not.toContain('nav-open');
  });

  it('should filter versions, auto-open survivors and hide the rest', () => {
    const search = element().querySelector('.nav-search input') as HTMLInputElement;
    search.value = 'v42';
    search.dispatchEvent(new Event('input'));

    expect(versionButton('v42').hasAttribute('data-filtered-out')).toBeFalse();
    expect(versionButton('v7').hasAttribute('data-filtered-out')).toBeTrue();
    expect(versionButton('v1').hasAttribute('data-filtered-out')).toBeTrue();

    const webService = serviceOf('web');
    expect(webService.hasAttribute('data-filtered-out')).toBeFalse();
    expect(webService.open).toBeTrue();
    // 'Project D' keeps no visible version and does not self-match
    expect(projectOf('Project D').hasAttribute('data-filtered-out')).toBeTrue();
    expect(Array.from(element().querySelectorAll('.nav-group'))[1].hasAttribute('data-filtered-out')).toBeTrue();

    search.value = '';
    search.dispatchEvent(new Event('input'));
    expect(versionButton('v7').hasAttribute('data-filtered-out')).toBeFalse();
    expect(versionButton('v1').hasAttribute('data-filtered-out')).toBeFalse();
    expect(webService.open).toBeTrue();
  });

  it('should keep a service visible on a self-match even when all its versions are filtered out', () => {
    const search = element().querySelector('.nav-search input') as HTMLInputElement;
    search.value = 'worker';
    search.dispatchEvent(new Event('input'));

    const workerService = serviceOf('worker');
    expect(workerService.hasAttribute('data-filtered-out')).toBeFalse();
    expect(workerService.open).toBeTrue();
    expect(versionButton('v1').hasAttribute('data-filtered-out')).toBeTrue();
    expect(versionButton('v42').hasAttribute('data-filtered-out')).toBeTrue();
  });

  it('should show the empty state with a "Manage projects" button when there is no data', () => {
    networkService.getTree.and.returnValue(of([]));
    const emptyFixture = TestBed.createComponent(DeployNavigatorComponent);
    emptyFixture.detectChanges();
    const el = emptyFixture.nativeElement as HTMLElement;

    expect(el.querySelector('.empty-state.nav-empty')).toBeTruthy();
    expect(el.textContent).toContain('No deploys found');
    expect(el.querySelector('.nav-search')).toBeNull();

    const views: unknown[] = [];
    TestBed.inject(HomeService).viewObservable.subscribe(view => views.push(view));
    views.length = 0; // drop the BehaviorSubject's current value
    (el.querySelector('.nav-empty .btn') as HTMLButtonElement).click();

    expect(views).toEqual(['projects']);
  });
});
