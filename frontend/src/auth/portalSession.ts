import keycloak from './keycloak';

export const PORTAL_AUTH_EXPIRED_EVENT = 'portal:auth-expired';

export function isPublicAuthRequest(url: string | undefined): boolean {
    const path = String(url ?? '');
    return (
        path.includes('/api/public/login') ||
        path.includes('/api/public/register') ||
        path.includes('/api/public/token/refresh')
    );
}

/** Keycloak redirect olmadan oturumu temizler. */
export function clearPortalSession(): void {
    keycloak.authenticated = false;
    keycloak.token = undefined;
    keycloak.refreshToken = undefined;
    keycloak.idToken = undefined;
    keycloak.tokenParsed = undefined;
    keycloak.refreshTokenParsed = undefined;
}

export function notifyAuthExpired(reason?: 'session' | 'suspended'): void {
    clearPortalSession();
    if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent(PORTAL_AUTH_EXPIRED_EVENT, { detail: { reason } }));
    }
}

export function redirectToPortalSignIn(query?: Record<string, string>): void {
    if (typeof window === 'undefined') return;
    const params = new URLSearchParams(query ?? { signin: '1' });
    const base = window.location.pathname === '/' ? '/' : '/';
    window.location.replace(`${base}?${params.toString()}`);
}
