import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { TopBarComponent } from "./top-bar/top-bar.component";
import { DeployViewerComponent } from '../deploy-viewer/deploy-viewer.component';
import { SettingsComponent } from '../settings/settings.component'
import { HomeService } from './home.service';
import { AuthService } from '../auth/auth.service';
import { LoginComponent } from '../auth/login.component';

@Component({
    selector: 'app-home',
    templateUrl: './home.component.html',
    styleUrl: './home.component.scss',
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [
        TopBarComponent,
        DeployViewerComponent,
        SettingsComponent,
        LoginComponent,
    ],
})
export class HomeComponent {
    constructor(
        private homeService: HomeService
    ) {}

    private authService = inject(AuthService);

    showSettings = false;

    /** True when authentication is disabled (local dev) or the user is signed in. */
    signedIn = false;

    ngOnInit() {
        this.homeService.showSettingsEventObservable.subscribe(showSettings => {
            this.showSettings = showSettings;
        })
        this.authService.authState$.subscribe(state => {
            this.signedIn = state.status === 'signed-in';
        })
    }
}