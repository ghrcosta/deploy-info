import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProjectDataDialogComponent } from './project-data-dialog.component';

describe('ProjectDataDialogComponent', () => {
  let component: ProjectDataDialogComponent;
  let fixture: ComponentFixture<ProjectDataDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProjectDataDialogComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ProjectDataDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
