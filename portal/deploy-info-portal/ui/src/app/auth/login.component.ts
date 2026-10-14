import { Component, ElementRef, ViewChild, inject, ChangeDetectionStrategy, OnDestroy } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { AuthService, AuthState } from './auth.service';

@Component({
    selector: 'login',
    imports: [
        MatIconModule,
    ],
    templateUrl: './login.component.html',
    styleUrl: './login.component.scss',
    changeDetection: ChangeDetectionStrategy.Eager,
})
export class LoginComponent implements OnDestroy {

    @ViewChild('googleButton')
    googleButton?: ElementRef<HTMLDivElement>;

    // `state` must be declared before `stateSubscription`: class fields initialize in declaration
    // order, and the `BehaviorSubject` behind `authState$` replays its current value synchronously
    // on subscribe. Declaring this field after the subscription would let its initializer overwrite
    // the replayed state back to 'loading', leaving the screen stuck on the loading message forever
    // (this is exactly how it shipped to App Engine once — the bug is invisible to tests that do
    // not construct the component with an already-resolved auth state).
    state: AuthState = { status: 'loading' };

    private authService = inject(AuthService);
    private stateSubscription = this.authService.authState$.subscribe(state => {
        this.state = state;
        // The button container appears/disappears with the state — (re)render once it is there.
        if (state.status === 'signed-out') setTimeout(() => this.renderGoogleButton());
    });

    private themeObserver?: MutationObserver;
    private colorSchemeListener?: () => void;
    private googleInitialized = false;

    /** Template accessors: narrowing the discriminated union inside the template is brittle. */
    get isSignedOut(): boolean {
        return this.state.status === 'signed-out';
    }

    get isNoAccess(): boolean {
        return this.state.status === 'no-access';
    }

    get noAccessEmail(): string {
        return this.state.status === 'no-access' ? this.state.email : '';
    }

    signOut = () => this.authService.logout();

    ngOnDestroy(): void {
        this.stateSubscription.unsubscribe();
        this.themeObserver?.disconnect();
        this.colorSchemeListener && window.matchMedia('(prefers-color-scheme: dark)')
            .removeEventListener('change', this.colorSchemeListener);
    }

    private async renderGoogleButton(): Promise<void> {
        const container = this.googleButton?.nativeElement;
        const clientId = this.authService.config.clientId;
        if (!container || !clientId) return;
        try {
            await this.loadScript();
        } catch {
            return; // The script failed to load; the button stays empty — the error is in the console.
        }
        const google = window.google;
        if (!google) return;
        if (!this.googleInitialized) {
            google.accounts.id.initialize({
                client_id: clientId,
                callback: response => {
                    this.authService.signIn(response.credential);
                },
            });
            this.googleInitialized = true;
        }
        this.watchThemeChanges();
        this.renderButtonInto(container);
    }

    /** Renders the Google button with the page's current light/dark scheme and fixed dimensions. */
    private renderButtonInto(container: HTMLElement): void {
        // Re-rendering replaces the button's iframe: clear the container so old ones do not pile up.
        container.replaceChildren();
        window.google!.accounts.id.renderButton(container, {
            // Follow the page theme: Google's 'outline' is always light, 'filled_black' is dark.
            theme: 'outline',
            type: 'standard',
            shape: 'rectangular',
            text: 'signin_with',
            size: 'large',
            logo_alignment: 'center',
            // Fixed width so the plain button renders at the same size every time. Note that a
            // width of 200px or more also lets Google render the personalized "Sign in as <user>"
            // button for returning users; that variant has its own dimensions and a light frame,
            // which the `.google-button` CSS neutralizes with `color-scheme: light`.
            width: 240,
        });
    }

    /** Re-renders the Google button when the page's light/dark scheme has changed. */
    private rerenderGoogleButton(): void {
        const container = this.googleButton?.nativeElement;
        if (container && this.isSignedOut && window.google) this.renderButtonInto(container);
    }

    /**
     * Listens for light/dark scheme changes: the top-bar toggle swaps `.light-theme`/`.dark-theme`
     * on `<html>`, and the OS preference (which the default `color-scheme: light dark` follows)
     * changes through `matchMedia`. Both are torn down in `ngOnDestroy`.
     */
    private watchThemeChanges(): void {
        if (this.themeObserver) return;
        this.themeObserver = new MutationObserver(() => this.rerenderGoogleButton());
        this.themeObserver.observe(document.documentElement, { attributeFilter: ['class'], attributes: true });
        this.colorSchemeListener = () => this.rerenderGoogleButton();
        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', this.colorSchemeListener);
    }

    /**
     * Whether the page is currently in dark mode. The probe resolves a `light-dark()` color the
     * same way the portal's theme does, so it reflects both the OS preference (the default
     * `color-scheme: light dark`) and the top-bar's `.light-theme`/`.dark-theme` overrides.
     */
    private isDarkTheme(): boolean {
        const probe = document.createElement('span');
        probe.style.color = 'light-dark(rgb(0, 0, 0), rgb(255, 255, 255))';
        document.body.appendChild(probe);
        const dark = getComputedStyle(probe).color === 'rgb(255, 255, 255)';
        probe.remove();
        return dark;
    }

    private scriptLoad?: Promise<void>;

    private loadScript(): Promise<void> {
        if (this.scriptLoad) return this.scriptLoad;
        this.scriptLoad = new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = 'https://accounts.google.com/gsi/client';
            script.async = true;
            script.onload = () => resolve();
            script.onerror = () => reject(new Error('Could not load the Google sign-in script'));
            document.head.appendChild(script);
        });
        return this.scriptLoad;
    }
}
