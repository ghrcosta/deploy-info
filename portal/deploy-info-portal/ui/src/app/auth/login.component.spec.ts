import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { environment } from '../../environments/environment';
import { LoginComponent } from './login.component';
import { AuthService } from './auth.service';

// Minimal icon stand-ins: the real ones are registered by AppComponent at
// runtime, which the specs do not include (why these are needed at all: deploy-viewer.component.spec.ts).
const STUB_SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="M0-960h960V0Z"/></svg>';

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
        const iconRegistry = TestBed.inject(MatIconRegistry);
        const sanitizer = TestBed.inject(DomSanitizer);
        ['deployed_code', 'deployed_code_alert']
            .forEach(name => iconRegistry.addSvgIconLiteral(name, sanitizer.bypassSecurityTrustHtml(STUB_SVG)));
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        sessionStorage.clear();
        httpMock.verify();
    });

    it('renders the sign-in screen with the branded card and the Google button container when the auth state is signed-out', async () => {
        const authService = TestBed.inject(AuthService);
        const initialized = authService.initialize();
        flushAuthConfig(); // enabled, no stored token: initialize() ends in signed-out
        await initialized;

        const fixture = TestBed.createComponent(LoginComponent);
        fixture.detectChanges();

        const compiled = fixture.nativeElement as HTMLElement;
        expect(compiled.querySelector('.login-card')).withContext('the branded login card').toBeTruthy();
        expect(compiled.querySelector('.login-brand')!.textContent).toContain('DeployInfo');
        expect(compiled.querySelector('.login-state')!.textContent).toContain('Access requires IAM role');
        expect(compiled.querySelector('.google-button'))
            .withContext('the container the Google Identity Services button is rendered into')
            .toBeTruthy();
    });

    it('renders the Google button with the page scheme and fixed dimensions, re-rendering on theme change', async () => {
        const renderButton = jasmine.createSpy('renderButton');
        const initialize = jasmine.createSpy('initialize');
        (window as { google?: unknown }).google = {
            accounts: { id: { initialize, renderButton } },
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
            width: 240, // fixed width: the plain button renders at the same size every time
        }));

        const html = document.documentElement;
        html.classList.add('dark-theme');
        html.style.colorScheme = 'dark'; // deterministic even if global styles are absent in tests
        await new Promise(resolve => setTimeout(resolve)); // the MutationObserver fires on a microtask
        fixture.detectChanges();
        expect(renderButton).toHaveBeenCalledTimes(2);
        expect(renderButton.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({
            theme: 'outline',
            width: 240,
        }));

        html.classList.remove('dark-theme');
        html.style.colorScheme = 'light';
        await new Promise(resolve => setTimeout(resolve));
        fixture.detectChanges();
        expect(renderButton).toHaveBeenCalledTimes(3);
        expect(renderButton.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({
            theme: 'outline',
            width: 240,
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
        expect(compiled.querySelector('.spinner')).toBeTruthy();
        expect(compiled.textContent).toContain('Checking your access');
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
        expect(compiled.querySelector('.login-state-warning')).toBeTruthy();
        expect(compiled.textContent).toContain('No access');
        expect(compiled.textContent).toContain('no access to this portal');
        expect(compiled.textContent).toContain('user@example.com');
        expect(compiled.querySelector('.btn.outlined')!.textContent).toContain('Sign out / switch account');
    });
});
