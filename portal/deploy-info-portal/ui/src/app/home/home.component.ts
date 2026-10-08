import { Component, ChangeDetectionStrategy } from '@angular/core';
import { TopBarComponent } from "./top-bar/top-bar.component";
import { DeployViewerComponent } from '../deploy-viewer/deploy-viewer.component';
import { SettingsComponent } from '../settings/settings.component'
import { HomeService } from './home.service';

@Component({
    selector: 'app-home',
    templateUrl: './home.component.html',
    styleUrl: './home.component.scss',
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [
        TopBarComponent,
        DeployViewerComponent,
        SettingsComponent,
    ],
})
export class HomeComponent {
    constructor(
        private homeService: HomeService
    ) {}

    showSettings = false;

    ngOnInit() {
        this.homeService.showSettingsEventObservable.subscribe(showSettings => {
            this.showSettings = showSettings;
        })
    }
}