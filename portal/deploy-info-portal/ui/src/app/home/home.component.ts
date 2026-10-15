import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { TopBarComponent } from "./top-bar/top-bar.component";
import { DeploymentViewerComponent } from '../deployment-viewer/deployment-viewer.component';
import { ProjectsConfigComponent } from '../projects-config/projects-config.component';
import { HelpComponent } from '../help/help.component';
import { HomeService, HomeView } from './home.service';
import { AuthService } from '../auth/auth.service';
import { LoginComponent } from '../auth/login.component';

@Component({
    selector: 'app-home',
    templateUrl: './home.component.html',
    styleUrl: './home.component.scss',
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [
        TopBarComponent,
        DeploymentViewerComponent,
        ProjectsConfigComponent,
        HelpComponent,
        LoginComponent,
    ],
})
export class HomeComponent {
    constructor(
        private homeService: HomeService
    ) {}

    private authService = inject(AuthService);

    /** The view currently shown in the content area. */
    view: HomeView = 'deployments';

    /** True when authentication is disabled (local dev) or the user is signed in. */
    signedIn = false;

    ngOnInit() {
        this.homeService.viewObservable.subscribe(view => {
            this.view = view;
        })
        this.authService.authState$.subscribe(state => {
            this.signedIn = state.status === 'signed-in';
        })
    }
}
