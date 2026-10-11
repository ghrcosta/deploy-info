import { ApplicationConfig, provideAppInitializer, provideZoneChangeDetection, inject } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { provideHighlightOptions } from 'ngx-highlightjs';

import { routes } from './app.routes';
import { authInterceptor } from './auth/auth.interceptor';
import { AuthService } from './auth/auth.service';

export const appConfig: ApplicationConfig = {
    providers: [
        provideZoneChangeDetection({ eventCoalescing: true }),

        // Attaches the Google ID token to API requests (Phase 2.7 authentication).
        provideHttpClient(withInterceptors([authInterceptor]), withXhr()),

        // Resolves the sign-in configuration and restores a stored session before the UI renders.
        provideAppInitializer(() => inject(AuthService).initialize()),

        // Required by app.routes.ts
        provideRouter(routes),

        // Required by ngx-highlightjs
        provideHighlightOptions({
            fullLibraryLoader: () => import('highlight.js'),
            lineNumbersLoader: () => import('ngx-highlightjs/line-numbers'),
            // Theme downloaded from https://cdnjs.com/libraries/highlight.js
            themePath: 'assets/highlightjs/atom-one-light.min.css',
        }),
    ]
};
