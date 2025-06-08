import { Component, Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { TopBarComponent } from "./top-bar/top-bar.component";
import { DeployViewerComponent } from '../deploy-viewer/deploy-viewer.component';
import { SettingsComponent } from '../settings/settings.component'

@Component({
    selector: 'app-home',
    templateUrl: './home.component.html',
    styleUrl: './home.component.scss',
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

@Injectable({
    providedIn: 'root'
})
export class HomeService {
    private _showSettingsSubject = new Subject<boolean>();
    showSettingsEventObservable = this._showSettingsSubject.asObservable();

    showSettingsClickedEvent(showSettings: boolean) {
        this._showSettingsSubject.next(showSettings);
    }
}