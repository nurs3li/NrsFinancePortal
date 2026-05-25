import keycloak from './keycloak';
import { isPublicAuthPath } from '../api/apiVersion';
import type { PortalTokens } from './applyKeycloakTokens';

export const PORTAL_AUTH_EXPIRED_EVENT = 'portal:auth-expired';
const PORTAL_SESSION_KEY = 'nrs.portal.session';

type PortalSessionScope = 'local' | 'session';

type StoredPortalSession = PortalTokens & {
    scope: PortalSessionScope;
};

function readStoredSession(storage: Storage): StoredPortalSession | null {
    try {
        const raw = storage.getItem(PORTAL_SESSION_KEY);
        if (!raw) return null;
        const parsed = JSON.parse(raw) as Partial<StoredPortalSession>;
        if (!parsed || typeof parsed.accessToken !== 'string' || !parsed.accessToken.trim()) {
            storage.removeItem(PORTAL_SESSION_KEY);
            return null;
        }
        return {
            accessToken: parsed.accessToken,
            refreshToken: parsed.refreshToken ?? undefined,
            expiresIn: parsed.expiresIn ?? undefined,
            scope: parsed.scope === 'local' ? 'local' : 'session',
        };
    } catch {
        storage.removeItem(PORTAL_SESSION_KEY);
        return null;
    }
}

function readPortalSessionRecord(): StoredPortalSession | null {
    if (typeof window === 'undefined') return null;
    return readStoredSession(window.localStorage) ?? readStoredSession(window.sessionStorage);
}

function storageByScope(scope: PortalSessionScope): Storage | null {
    if (typeof window === 'undefined') return null;
    return scope === 'local' ? window.localStorage : window.sessionStorage;
}

export function isPublicAuthRequest(url: string | undefined): boolean {
    return isPublicAuthPath(String(url ?? ''));
}

export function persistPortalSession(tokens: PortalTokens, rememberMe?: boolean): void {
    const existing = readPortalSessionRecord();
    const scope: PortalSessionScope = rememberMe == null ? existing?.scope ?? 'session' : rememberMe ? 'local' : 'session';
    const target = storageByScope(scope);
    if (!target) return;
    const payload: StoredPortalSession = {
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken ?? undefined,
        expiresIn: tokens.expiresIn ?? undefined,
        scope,
    };
    try {
        target.setItem(PORTAL_SESSION_KEY, JSON.stringify(payload));
        const other = scope === 'local' ? storageByScope('session') : storageByScope('local');
        other?.removeItem(PORTAL_SESSION_KEY);
    } catch {
        /* storage unavailable */
    }
}

export function restorePortalSession(): PortalTokens | null {
    const stored = readPortalSessionRecord();
    if (!stored) return null;
    return {
        accessToken: stored.accessToken,
        refreshToken: stored.refreshToken ?? undefined,
        expiresIn: stored.expiresIn ?? undefined,
    };
}

/** Keycloak redirect olmadan oturumu temizler. */
export function clearPortalSession(): void {
    keycloak.authenticated = false;
    keycloak.token = undefined;
    keycloak.refreshToken = undefined;
    keycloak.idToken = undefined;
    keycloak.tokenParsed = undefined;
    keycloak.refreshTokenParsed = undefined;
    if (typeof window !== 'undefined') {
        try {
            window.localStorage.removeItem(PORTAL_SESSION_KEY);
            window.sessionStorage.removeItem(PORTAL_SESSION_KEY);
        } catch {
            /* storage unavailable */
        }
    }
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
