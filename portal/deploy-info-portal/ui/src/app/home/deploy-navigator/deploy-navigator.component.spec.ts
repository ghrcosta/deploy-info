import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DeployNavigatorComponent } from './deploy-navigator.component';

describe('DeployNavigatorComponent', () => {
  let component: DeployNavigatorComponent;
  let fixture: ComponentFixture<DeployNavigatorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeployNavigatorComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DeployNavigatorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
