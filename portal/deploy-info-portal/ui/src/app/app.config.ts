import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHighlightOptions } from 'ngx-highlightjs';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
    providers: [
        provideZoneChangeDetection({ eventCoalescing: true }),

        // Required by MatIconRegistry
        provideHttpClient(),

        // Required by app.routes.ts
        provideRouter(routes),

        // Required by ngx-highlightjs
        provideHighlightOptions({
            fullLibraryLoader: () => import('highlight.js'),
            lineNumbersLoader: () => import('ngx-highlightjs/line-numbers'),
            themePath: 'assets/highlightjs/stackoverflow-light.min.css',
        }),
    ]
};
