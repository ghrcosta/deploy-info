import { Component, OnInit, inject, Renderer2, ChangeDetectionStrategy } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { HomeService, HomeView } from '../home.service'
import { AuthService } from '../../auth/auth.service'

/** localStorage key under which the theme choice persists across reloads. */
const THEME_STORAGE_KEY = 'deploy-info-theme';

@Component({
    selector: 'top-bar',
    imports: [
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

    /** The view whose nav link is shown as active. */
    view: HomeView = 'deployments';

    isDarkMode = false;

    private readonly renderer = inject(Renderer2);

    ngOnInit() {
        this.homeService.viewObservable.subscribe(view => {
            this.view = view;
        })
        // Restore the persisted theme choice (see app.component.ts for the
        // startup application of the same key).
        this.isDarkMode = localStorage.getItem(THEME_STORAGE_KEY) === 'dark';
        this.applyThemeClasses();
    }

    /** Mobile menu toggle: shows/hides the navigator drawer. */
    toggleNavigator = () => {
        this.renderer.selectRootElement('body', true).classList.toggle('nav-open');
    }

    openView = (view: HomeView, event?: Event) => {
        event?.preventDefault();
        this.homeService.openView(view);
    }

    toggleDarkMode = () => {
        this.isDarkMode = !this.isDarkMode;
        this.applyThemeClasses();
        localStorage.setItem(THEME_STORAGE_KEY, this.isDarkMode ? 'dark' : 'light');
    }

    logout = () => {
        this.authService.logout();
    }

    private applyThemeClasses() {
        const html = this.renderer.selectRootElement('html', true);
        if (this.isDarkMode) {
            this.renderer.addClass(html, 'dark-theme');
            this.renderer.removeClass(html, 'light-theme');
        } else {
            this.renderer.addClass(html, 'light-theme');
            this.renderer.removeClass(html, 'dark-theme');
        }
    }
}
