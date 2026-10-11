import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

/** The sign-in configuration served by the backend's public `GET /auth/config`. */
export interface AuthConfig {
    enabled: boolean;
    clientId: string;
}

/** The possible login states the UI renders. */
export type AuthState =
    | { status: 'loading' }
    | { status: 'signed-out' }
    | { status: 'no-access'; email: string }
    | { status: 'signed-in'; email: string | null };

const TOKEN_STORAGE_KEY = 'deploy-info.id-token';

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private http = inject(HttpClient);

    // BehaviorSubject so subscribers always receive the current state immediately,
    // instead of only reacting to transitions emitted after they subscribed.
    private stateSubject = new BehaviorSubject<AuthState>({ status: 'loading' });
    readonly authState$ = this.stateSubject.asObservable();

    private authConfig: AuthConfig = { enabled: false, clientId: '' };

    get config(): AuthConfig {
        return this.authConfig;
    }

    get isEnabled(): boolean {
        return this.authConfig.enabled;
    }

    /** The Google ID token obtained at sign-in, if any. */
    get token(): string | null {
        return sessionStorage.getItem(TOKEN_STORAGE_KEY);
    }

    /** Runs at app startup (see app.config.ts): resolves the config, restores the previous session. */
    async initialize(): Promise<void> {
        try {
            this.authConfig = await firstValueFrom(this.http.get<AuthConfig>(`${environment.url}/auth/config`));
        } catch {
            // A backend without the auth endpoint (or unreachable): behave like auth-disabled, the
            // way local development runs anyway.
            this.authConfig = { enabled: false, clientId: '' };
        }
        if (!this.authConfig.enabled) {
            this.stateSubject.next({ status: 'signed-in', email: null });
            return;
        }
        const stored = this.token;
        if (stored === null) {
            this.stateSubject.next({ status: 'signed-out' });
            return;
        }
        // The interceptor is deliberately not used here: the session is restored before the UI
        // renders, without the interceptor's session-expiry side effects kicking in.
        await this.restoreOrDrop(stored);
    }

    /** Called by the login component when the Google sign-in produced a credential (an ID token). */
    async signIn(credential: string): Promise<void> {
        sessionStorage.setItem(TOKEN_STORAGE_KEY, credential);
        await this.restoreOrDrop(credential);
    }

    /**
     * The backend reported 401 for a request the interceptor attached the token to: the stored
     * token is no longer valid (expired or revoked).
     */
    sessionExpired(): void {
        if (this.stateSubject.value.status !== 'signed-in') return;
        sessionStorage.removeItem(TOKEN_STORAGE_KEY);
        this.stateSubject.next({ status: 'signed-out' });
    }

    /** The backend reported 403 for an authenticated user: the account has no IAM access. */
    accessDenied(email: string): void {
        if (this.stateSubject.value.status !== 'signed-in') return;
        this.stateSubject.next({ status: 'no-access', email });
    }

    /** The top-bar logout button. A no-op while authentication is disabled (local dev). */
    logout(): void {
        if (!this.authConfig.enabled) return;
        const current = this.stateSubject.value;
        const google = window.google;
        if (current.status === 'signed-in' && current.email) {
            try {
                google?.accounts.id.revoke(current.email, () => {});
            } catch {
                // Revocation is best-effort: the local session is cleared regardless.
            }
        }
        try {
            google?.accounts.id.disableAutoSelect();
        } catch {
            // Ignore — see above.
        }
        sessionStorage.removeItem(TOKEN_STORAGE_KEY);
        this.stateSubject.next({ status: 'signed-out' });
    }

    private async restoreOrDrop(token: string): Promise<void> {
        try {
            const user = await firstValueFrom(this.http.get<{ email: string }>(
                `${environment.url}/auth/user`,
                { headers: { Authorization: `Bearer ${token}` } },
            ));
            this.stateSubject.next({ status: 'signed-in', email: user.email });
        } catch (error: unknown) {
            const httpError = error as { status?: number; error?: { email?: string } };
            if (httpError.status === 403) {
                // The account is signed in, but is not present in the portal project's IAM.
                this.stateSubject.next({ status: 'no-access', email: httpError.error?.email ?? '' });
                return;
            }
            // 401 (token no longer valid) or the backend being unreachable: back to sign-in.
            sessionStorage.removeItem(TOKEN_STORAGE_KEY);
            this.stateSubject.next({ status: 'signed-out' });
        }
    }
}
