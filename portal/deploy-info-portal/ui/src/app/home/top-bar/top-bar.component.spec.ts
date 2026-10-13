import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';

import { TopBarComponent } from './top-bar.component';
import { HomeService, HomeView } from '../home.service';

const THEME_STORAGE_KEY = 'deploy-info-theme';

// Minimal stand-in for the deployed_code icon the brand mark renders; the real
// SVG is registered by AppComponent at runtime, which the specs don't include.
const DEPLOYED_CODE_SVG =
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="M0-960h960V0H0Z"/></svg>';

describe('TopBarComponent', () => {
  let component: TopBarComponent;
  let fixture: ComponentFixture<TopBarComponent>;
  let views: HomeView[];

  beforeEach(async () => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.classList.remove('dark-theme', 'light-theme');
    document.body.classList.remove('nav-open');

    await TestBed.configureTestingModule({
      imports: [TopBarComponent]
    })
    .compileComponents();

    const iconRegistry = TestBed.inject(MatIconRegistry);
    const sanitizer = TestBed.inject(DomSanitizer);
    iconRegistry.addSvgIconLiteral('deployed_code', sanitizer.bypassSecurityTrustHtml(DEPLOYED_CODE_SVG));

    const homeService = TestBed.inject(HomeService);
    views = [];
    homeService.viewObservable.subscribe(view => views.push(view));

    fixture = TestBed.createComponent(TopBarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.classList.remove('dark-theme', 'light-theme');
    document.body.classList.remove('nav-open');
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('opens the projects view when the Projects nav link is clicked', () => {
    views = [];

    const links = fixture.nativeElement.querySelectorAll('.topbar-link') as NodeListOf<HTMLAnchorElement>;
    const link = Array.from(links).find(el => el.textContent!.trim() === 'Projects')!;
    link.click();

    expect(views).toEqual(['projects']);
  });

  it('opens the help view when the Help nav link is clicked', () => {
    views = [];

    const links = fixture.nativeElement.querySelectorAll('.topbar-link') as NodeListOf<HTMLAnchorElement>;
    const link = Array.from(links).find(el => el.textContent!.trim() === 'Help')!;
    link.click();

    expect(views).toEqual(['help']);
  });

  it('returns to the deploys view when the app name is clicked', () => {
    views = [];

    (fixture.nativeElement.querySelector('.brand') as HTMLElement).click();

    expect(views).toEqual(['deploys']);
  });

  it('does not navigate when a nav link is clicked', () => {
    const event = new MouseEvent('click', { cancelable: true });

    fixture.nativeElement.querySelector('.topbar-link').dispatchEvent(event);

    expect(event.defaultPrevented).toBeTrue();
  });

  it('marks the nav link of the current view as active', () => {
    const homeService = TestBed.inject(HomeService);

    homeService.openView('projects');
    fixture.detectChanges();

    const active = fixture.nativeElement.querySelector('.topbar-link.active') as HTMLElement;
    expect(active.textContent).toContain('Projects');
  });

  it('toggles the dark theme and persists the choice', () => {
    const themeButton = fixture.nativeElement.querySelector('button[aria-label="Toggle theme"]') as HTMLButtonElement;

    themeButton.click();
    expect(document.documentElement.classList).toContain('dark-theme');
    expect(document.documentElement.classList).not.toContain('light-theme');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(component.isDarkMode).toBeTrue();

    themeButton.click();
    expect(document.documentElement.classList).toContain('light-theme');
    expect(document.documentElement.classList).not.toContain('dark-theme');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
    expect(component.isDarkMode).toBeFalse();
  });

  it('restores the persisted dark theme on init', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'dark');

    fixture = TestBed.createComponent(TopBarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.isDarkMode).toBeTrue();
    expect(document.documentElement.classList).toContain('dark-theme');
  });

  it('toggles the navigator drawer class on the body', () => {
    const navToggle = fixture.nativeElement.querySelector('.nav-toggle') as HTMLButtonElement;

    navToggle.click();
    expect(document.body.classList).toContain('nav-open');

    navToggle.click();
    expect(document.body.classList).not.toContain('nav-open');
  });
});
