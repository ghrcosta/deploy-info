import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { LoginComponent } from './login.component';
import { AuthService } from './auth.service';

describe('LoginComponent', () => {
    let httpMock: HttpTestingController;

    const flushAuthConfig = () => {
        httpMock.expectOne(`${environment.url}/auth/config`)
            .flush({ enabled: true, clientId: 'test-client-id' });
    };

    beforeEach(async () => {
        sessionStorage.clear();
        await TestBed.configureTestingModule({
            imports: [LoginComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        sessionStorage.clear();
        httpMock.verify();
    });

    /**
     * Regression: the component's `state` field initializer used to run after the
     * `authState$` subscription field, overwriting the synchronously replayed state back
     * to 'loading' — the deployed sign-in screen was stuck on the loading message forever.
     */
    it('renders the sign-in screen with the Google button container when the auth state is signed-out', async () => {
        const authService = TestBed.inject(AuthService);
        const initialized = authService.initialize();
        flushAuthConfig(); // enabled, no stored token: initialize() ends in signed-out
        await initialized;

        const fixture = TestBed.createComponent(LoginComponent);
        fixture.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        expect(compiled.textContent).toContain('Sign in with your Google account');
        expect(compiled.querySelector('.google-button'))
            .withContext('the container the Google Identity Services button is rendered into')
            .toBeTruthy();
        expect(compiled.textContent).not.toContain('Loading');
    });

    /**
     * Regression: the button used to be rendered with the hardcoded light 'outline' theme, and
     * content-sized (no `width`), so the personalized "Sign in as <user>" button had different
     * dimensions than the initial one, with the iframe off-center inside the container.
     */
    it('renders the Google button with the page scheme and fixed dimensions, re-rendering on theme change', async () => {
        const renderButton = jasmine.createSpy('renderButton');
        (window as { google?: unknown }).google = {
            accounts: { id: { initialize: jasmine.createSpy('initialize'), renderButton } },
        };
        // "Loads" the Google Identity Services script without the network: resolves synchronously.
        spyOn(document.head, 'appendChild').and.callFake(
            ((script: HTMLScriptElement) => {
                script.onload?.(new Event('load'));
                return script;
            }) as unknown as typeof document.head.appendChild,
        );

        const authService = TestBed.inject(AuthService);
        const initialized = authService.initialize();
        flushAuthConfig();
        await initialized;

        const fixture = TestBed.createComponent(LoginComponent);
        fixture.detectChanges();
        await fixture.whenStable(); // let the deferred Google button render run
        fixture.detectChanges();
        await new Promise(resolve => setTimeout(resolve)); // let the async render finish
        expect(renderButton).toHaveBeenCalledTimes(1);
        expect(renderButton.calls.first().args[1]).toEqual(jasmine.objectContaining({
            type: 'standard',
            shape: 'rectangular',
            text: 'signin_with',
            size: 'large',
            logo_alignment: 'center',
            width: 300, // fixed width: the plain button renders at the same size every time
        }));

        const html = document.documentElement;
        html.classList.add('dark-theme');
        html.style.colorScheme = 'dark'; // deterministic even if global styles are absent in tests
        await new Promise(resolve => setTimeout(resolve)); // the MutationObserver fires on a microtask
        fixture.detectChanges();
        expect(renderButton).toHaveBeenCalledTimes(2);
        expect(renderButton.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({
            theme: 'filled_black', // the dark page gets the dark button variant
            width: 300,
        }));

        html.classList.remove('dark-theme');
        html.style.colorScheme = 'light';
        await new Promise(resolve => setTimeout(resolve));
        fixture.detectChanges();
        expect(renderButton).toHaveBeenCalledTimes(3);
        expect(renderButton.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({
            theme: 'outline',
            width: 300,
        }));

        html.style.colorScheme = '';
        delete (window as { google?: unknown }).google;
    });

    it('renders the loading message while the auth state is still loading', () => {
        const authService = TestBed.inject(AuthService);
        authService.initialize(); // config request left pending: the state stays 'loading'

        const fixture = TestBed.createComponent(LoginComponent);
        fixture.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        expect(compiled.textContent).toContain('Loading');
        expect(compiled.textContent).not.toContain('Sign in with');

        flushAuthConfig(); // no pending requests left for verify()
    });

    it('renders the no-access screen with the account email when the backend rejects the user', async () => {
        const authService = TestBed.inject(AuthService);
        const initialized = authService.initialize();
        flushAuthConfig();
        await initialized;

        const signedIn = authService.signIn('stored-id-token');
        httpMock.expectOne(`${environment.url}/auth/user`).flush(
            { error: 'not-permitted', email: 'user@example.com' },
            { status: 403, statusText: 'Forbidden' },
        );
        await signedIn;

        const fixture = TestBed.createComponent(LoginComponent);
        fixture.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        expect(compiled.textContent).toContain('no access to this portal');
        expect(compiled.textContent).toContain('user@example.com');
        expect(compiled.textContent).not.toContain('Loading');
    });
});
