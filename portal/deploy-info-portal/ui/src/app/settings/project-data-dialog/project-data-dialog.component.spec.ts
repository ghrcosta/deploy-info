import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { DialogData, ProjectDataDialogAction, ProjectDataDialogComponent } from './project-data-dialog.component';

describe('ProjectDataDialogComponent', () => {
  let component: ProjectDataDialogComponent;
  let fixture: ComponentFixture<ProjectDataDialogComponent>;

  const dialogData: DialogData = {
    action: ProjectDataDialogAction.ADD,
    project: { name: 'test-project', group: 'test-group', serviceAccount: 'test@test.iam.gserviceaccount.com' }
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProjectDataDialogComponent],
      providers: [
        { provide: MatDialogRef, useValue: { close: () => {} } },
        { provide: MAT_DIALOG_DATA, useValue: dialogData }
      ]
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
