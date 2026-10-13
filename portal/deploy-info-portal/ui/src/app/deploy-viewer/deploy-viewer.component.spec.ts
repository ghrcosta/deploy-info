import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';

import { DeployViewerComponent } from './deploy-viewer.component';

// Minimal stand-ins for the SVG icons the navigator and file viewer render;
// the real ones are registered by AppComponent at runtime, which the specs
// don't include. Without these, MatIconRegistry logs an error for every icon
// the templates fetch via URL.
const STUB_SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M0 0h24v24H0z"/></svg>';
const registerStubIcons = (registry: MatIconRegistry, sanitizer: DomSanitizer) =>
    ['search', 'chevron_right', 'project', 'GAE', 'RUN', 'deployed_code', 'deployed_code_alert', 'no_data', 'link', 'arrow_outward', 'person', 'schedule']
        .forEach(name => registry.addSvgIconLiteral(name, sanitizer.bypassSecurityTrustHtml(STUB_SVG)));

describe('DeployViewerComponent', () => {
  let component: DeployViewerComponent;
  let fixture: ComponentFixture<DeployViewerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeployViewerComponent]
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

  it('should render the mock shell grid: navigator column next to the viewer column', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.shell')).toBeTruthy();
    expect(el.querySelector('aside.navigator#navigator')).toBeTruthy();
    expect(el.querySelector('main.viewer')).toBeTruthy();
    expect(el.querySelector('aside.navigator deploy-navigator')).toBeTruthy();
    expect(el.querySelector('main.viewer file-viewer')).toBeTruthy();
  });
});
