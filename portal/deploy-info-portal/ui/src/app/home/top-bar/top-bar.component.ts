import { Component, OnInit, inject, Renderer2, ChangeDetectionStrategy } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { HighlightLoader } from 'ngx-highlightjs';
import { HomeService } from '../home.service'
import { AuthService } from '../../auth/auth.service'

@Component({
    selector: 'top-bar',
    imports: [
        MatButtonModule,
        MatIconModule,
    ],
    templateUrl: './top-bar.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './top-bar.component.scss'
})
export class TopBarComponent implements OnInit {
    constructor(
        private homeService: HomeService,
        private authService: AuthService,
    ) {}

    isDarkMode = false;

    /** The signed-in user's email; empty while authentication is disabled (local dev). */
    userEmail = '';

    ngOnInit() {
        this.authService.authState$.subscribe(state => {
            this.userEmail = state.status === 'signed-in' ? state.email ?? '' : '';
        })
    }

    private readonly renderer = inject(Renderer2);
    private readonly highlightjsLoader: HighlightLoader = inject(HighlightLoader);

    toggleDarkMode = () => {
        this.isDarkMode = !this.isDarkMode;

        const body = this.renderer.selectRootElement('html', true);
        if (this.isDarkMode) {
            this.renderer.addClass(body, 'dark-theme');
            this.renderer.removeClass(body, 'light-theme');
            this.highlightjsLoader.setTheme('assets/highlightjs/atom-one-dark.min.css');
        } else {
            this.renderer.addClass(body, 'light-theme');
            this.renderer.removeClass(body, 'dark-theme');
            this.highlightjsLoader.setTheme('assets/highlightjs/atom-one-light.min.css');
        }
    }

    openDeployViewer = () => {
        this.homeService.showSettingsClickedEvent(false);
    }

    openSettings = () => {
        this.homeService.showSettingsClickedEvent(true);
    }

    logout = () => {
        this.authService.logout();
    }
}
