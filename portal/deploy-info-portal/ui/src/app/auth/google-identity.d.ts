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
