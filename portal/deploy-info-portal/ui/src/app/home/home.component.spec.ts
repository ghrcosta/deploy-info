import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';

import { HomeComponent } from './home.component';

// Minimal stand-ins for the SVG icons the login card renders; the real ones are
// registered by AppComponent at runtime, which the specs don't include.
const STUB_SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="M0-960h960V0Z"/></svg>';

describe('HomeComponent', () => {
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomeComponent]
    })
    .compileComponents();

    const iconRegistry = TestBed.inject(MatIconRegistry);
    const sanitizer = TestBed.inject(DomSanitizer);
    ['deployed_code', 'deployed_code_alert']
      .forEach(name => iconRegistry.addSvgIconLiteral(name, sanitizer.bypassSecurityTrustHtml(STUB_SVG)));

    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
