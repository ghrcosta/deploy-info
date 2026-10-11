import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Attaches the Google ID token to every API request (unless it already carries one — the auth
 * service sets the header itself for the session restore), and turns the backend's auth errors
 * into login-state changes: 401 back to the sign-in screen, 403 (an authenticated user without
 * access) to the no-access screen.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
    const authService = inject(AuthService);
    const token = authService.token;
    const withToken =
        authService.isEnabled && token !== null && !request.headers.has('Authorization')
            ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
            : request;
    return next(withToken).pipe(
        tap({
            error: (error: HttpErrorResponse) => {
                if (!authService.isEnabled) return;
                if (error.status === 401) {
                    authService.sessionExpired();
                } else if (error.status === 403 && error.error?.error === 'not-permitted') {
                    authService.accessDenied(error.error?.email ?? '');
                }
            },
        }),
    );
};
