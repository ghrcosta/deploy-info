import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TopBarComponent } from './top-bar.component';
import { HomeService } from '../home.service';

describe('TopBarComponent', () => {
  let component: TopBarComponent;
  let fixture: ComponentFixture<TopBarComponent>;
  let showSettingsValues: boolean[];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TopBarComponent]
    })
    .compileComponents();

    const homeService = TestBed.inject(HomeService);
    showSettingsValues = [];
    homeService.showSettingsEventObservable.subscribe(value => showSettingsValues.push(value));

    fixture = TestBed.createComponent(TopBarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('opens the settings screen when the configuration icon is clicked', () => {
    showSettingsValues = [];

    const buttons = fixture.nativeElement.querySelectorAll('button');
    buttons[1].click(); // configuration icon (after the dark-mode toggle, before logout)

    expect(showSettingsValues).toEqual([true]);
  });

  it('returns to the deploy viewer when the app name is clicked', () => {
    showSettingsValues = [];

    fixture.nativeElement.querySelector('.app-name').click();

    expect(showSettingsValues).toEqual([false]);
  });
});
