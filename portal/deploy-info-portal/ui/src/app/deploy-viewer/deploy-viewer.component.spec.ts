import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DeployViewerComponent } from './deploy-viewer.component';

describe('DeployViewerComponent', () => {
  let component: DeployViewerComponent;
  let fixture: ComponentFixture<DeployViewerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeployViewerComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DeployViewerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
