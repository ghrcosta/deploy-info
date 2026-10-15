import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { of } from 'rxjs';

import { HomeComponent } from './home.component';
import { HomeService } from './home.service';
import { AuthState, AuthService } from '../auth/auth.service';
import { DeploymentTreeNetworkService } from '../deployment-viewer/deployment-tree.network.service';
import { ProjectsConfigNetworkService } from '../projects-config/projects-config.network.service';

const THEME_STORAGE_KEY = 'deploy-info-theme';

// Minimal icon stand-ins: the real ones are registered by AppComponent at
// runtime, which the specs do not include (why these are needed at all: deployment-viewer.component.spec.ts).
const STUB_SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="M0-960h960V0Z"/></svg>';
const ICON_NAMES = [
  'project', 'GAE', 'RUN', 'no_data', 'group', 'deployed_code', 'deployed_code_alert', 'label',
  'service_account', 'edit', 'delete', 'search', 'chevron_right', 'copy', 'check', 'link',
  'arrow_outward', 'person', 'schedule', 'add', 'logout', 'menu',
];

/** Auth state the specs control directly (the real service only changes state over HTTP). */
class AuthServiceStub {
  private subject = new BehaviorSubject<AuthState>({ status: 'signed-in', email: 'user@example.com' });
  readonly authState$ = this.subject.asObservable();

  become(state: AuthState) {
    this.subject.next(state);
  }
}

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  let authService: AuthServiceStub;

  beforeEach(async () => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.classList.remove('dark-theme', 'light-theme');

    const treeNetwork = jasmine.createSpyObj<DeploymentTreeNetworkService>('DeploymentTreeNetworkService', ['getTree']);
    treeNetwork.getTree.and.returnValue(of([]));
    const projectsNetwork = jasmine.createSpyObj<ProjectsConfigNetworkService>('ProjectsConfigNetworkService', ['getProjects']);
    projectsNetwork.getProjects.and.returnValue(of([]));
    authService = new AuthServiceStub();

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: DeploymentTreeNetworkService, useValue: treeNetwork },
        { provide: ProjectsConfigNetworkService, useValue: projectsNetwork },
      ],
    }).compileComponents();

    const iconRegistry = TestBed.inject(MatIconRegistry);
    const sanitizer = TestBed.inject(DomSanitizer);
    ICON_NAMES.forEach(name => iconRegistry.addSvgIconLiteral(name, sanitizer.bypassSecurityTrustHtml(STUB_SVG)));
  });

  afterEach(() => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.classList.remove('dark-theme', 'light-theme');
  });

  const render = () => {
    fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    return fixture;
  };
  const element = () => fixture.nativeElement as HTMLElement;

  it('shows the login screen while the auth state is loading', () => {
    authService.become({ status: 'loading' });
    render();

    expect(element().querySelector('login')).withContext('the sign-in screen').toBeTruthy();
    expect(element().querySelector('top-bar')).toBeNull();
  });

  it('shows the login screen while signed out', () => {
    authService.become({ status: 'signed-out' });
    render();

    expect(element().querySelector('login')).toBeTruthy();
    expect(element().querySelector('top-bar')).toBeNull();
  });

  it('shows the top bar and the deployment viewer once signed in', () => {
    render();

    expect(element().querySelector('top-bar')).toBeTruthy();
    expect(element().querySelector('deployment-viewer')).withContext('the default view is deployments').toBeTruthy();
  });

  it('switches the content view with the HomeService view state', () => {
    render();
    const homeService = TestBed.inject(HomeService);

    homeService.openView('projects');
    fixture.detectChanges();
    expect(element().querySelector('projects-config')).toBeTruthy();
    expect(element().querySelector('deployment-viewer')).toBeNull();

    homeService.openView('help');
    fixture.detectChanges();
    expect(element().querySelector('help')).toBeTruthy();
    expect(element().querySelector('projects-config')).toBeNull();

    homeService.openView('deployments');
    fixture.detectChanges();
    expect(element().querySelector('deployment-viewer')).toBeTruthy();
  });
});

