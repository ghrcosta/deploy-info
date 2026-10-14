/**
 * Minimal typing of the Google Identity Services API (`https://accounts.google.com/gsi/client`),
 * which the login component loads at runtime — no npm package is used.
 */
declare global {
    interface GoogleIdentityApi {
        accounts: {
            id: {
                initialize(config: {
                    client_id: string;
                    callback: (response: { credential: string }) => void;
                    /** Switches the button to the FedCM flow on Chrome (desktop M125+, Android M128+); the legacy popup flow has no auto-select. */
                    use_fedcm_for_button?: boolean;
                    /** FedCM auto-select: returning users with an active Google session are signed in directly, without the Account Chooser. */
                    button_auto_select?: boolean;
                }): void;
                renderButton(parent: HTMLElement, options: Record<string, unknown>): void;
                disableAutoSelect(): void;
                revoke(userId: string, done: () => void): void;
            };
        };
    }

    interface Window {
        google?: GoogleIdentityApi;
    }
}

export {};
