import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { MatIconRegistry } from '@angular/material/icon';

import { AppComponent } from './app.component';

const THEME_STORAGE_KEY = 'deploy-info-theme';
const STUB_SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M0 0h24v24H0z"/></svg>';

/** The GCP icons registered from `public/assets/icons` (name → file, see app.component.ts). */
const URL_ICONS: Record<string, string> = {
  project: 'assets/icons/gcp_project.svg',
  GAE: 'assets/icons/gcp_gae.svg',
  RUN: 'assets/icons/gcp_run.svg',
  no_data: 'assets/icons/data_off.svg',
  group: 'assets/icons/group.svg',
  deployed_code: 'assets/icons/deployed_code.svg',
  deployed_code_alert: 'assets/icons/deployed_code_alert.svg',
  label: 'assets/icons/label.svg',
  service_account: 'assets/icons/key.svg',
  edit: 'assets/icons/edit.svg',
  delete: 'assets/icons/delete.svg',
};

/** The Material-Symbols icons registered inline (the `VIEWER_ICONS` set). */
const LITERAL_ICONS = [
  'search', 'chevron_right', 'copy', 'check', 'link', 'arrow_outward',
  'person', 'schedule', 'add', 'logout', 'menu',
];

describe('AppComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.classList.remove('dark-theme', 'light-theme');

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: {} },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.classList.remove('dark-theme', 'light-theme');
    httpMock.verify();
  });

  const create = () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    return fixture;
  };

  it('renders the router outlet (the app is a routed login → home shell)', () => {
    const fixture = create();
    expect((fixture.nativeElement as HTMLElement).querySelector('router-outlet'))
      .withContext('router-outlet should be rendered')
      .toBeTruthy();
  });

  it('registers every GCP icon from public/assets/icons, served on first use', () => {
    create();
    const registry = TestBed.inject(MatIconRegistry);

    for (const [name, url] of Object.entries(URL_ICONS)) {
      let resolved = false;
      registry.getNamedSvgIcon(name).subscribe(() => (resolved = true));
      httpMock.expectOne(url).flush(STUB_SVG);
      expect(resolved).withContext(`icon '${name}' from ${url}`).toBeTrue();
    }
  });

  it('registers the inline Material-Symbols icons without any request', () => {
    create();
    const registry = TestBed.inject(MatIconRegistry);

    for (const name of LITERAL_ICONS) {
      let resolved = false;
      registry.getNamedSvgIcon(name).subscribe(() => (resolved = true));
      expect(resolved).withContext(`inline icon '${name}'`).toBeTrue();
    }
  });

  it('applies the light theme by default and the persisted dark theme on startup', () => {
    create();
    expect(document.documentElement.classList).toContain('light-theme');

    document.documentElement.classList.remove('light-theme');
    localStorage.setItem(THEME_STORAGE_KEY, 'dark');
    create();
    expect(document.documentElement.classList).toContain('dark-theme');
  });
});

