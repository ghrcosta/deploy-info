import { Component, inject, Renderer2 } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { HighlightLoader } from 'ngx-highlightjs';

@Component({
    selector: 'top-bar',
    imports: [
        MatButtonModule,
        MatIconModule,
    ],
    templateUrl: './top-bar.component.html',
    styleUrl: './top-bar.component.scss'
})
export class TopBarComponent {
    isDarkMode = false;

    private readonly renderer = inject(Renderer2);
    private readonly highlightjsLoader: HighlightLoader = inject(HighlightLoader);

    toggleDarkMode = () => {
        this.isDarkMode = !this.isDarkMode;

        const body = this.renderer.selectRootElement('html', true);
        if (this.isDarkMode) {
            this.renderer.addClass(body, 'dark-theme');
            this.renderer.removeClass(body, 'light-theme');
            this.highlightjsLoader.setTheme('assets/highlightjs/stackoverflow-dark.min.css');
        } else {
            this.renderer.addClass(body, 'light-theme');
            this.renderer.removeClass(body, 'dark-theme');
            this.highlightjsLoader.setTheme('assets/highlightjs/stackoverflow-light.min.css');
        }
    };
}
