import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./keycloak', () => ({
    default: {
        authenticated: false,
        token: undefined,
        refreshToken: undefined,
        idToken: undefined,
        tokenParsed: undefined,
        refreshTokenParsed: undefined,
    },
}));

import { clearPortalSession, persistPortalSession, restorePortalSession } from './portalSession';

function createMemoryStorage(): Storage {
    const store = new Map<string, string>();
    return {
        get length() {
            return store.size;
        },
        clear() {
            store.clear();
        },
        getItem(key: string) {
            return store.has(key) ? store.get(key)! : null;
        },
        key(index: number) {
            return Array.from(store.keys())[index] ?? null;
        },
        removeItem(key: string) {
            store.delete(key);
        },
        setItem(key: string, value: string) {
            store.set(key, value);
        },
    };
}

describe('portalSession', () => {
    beforeEach(() => {
        const localStorage = createMemoryStorage();
        const sessionStorage = createMemoryStorage();
        Object.defineProperty(globalThis, 'window', {
            value: {
                localStorage,
                sessionStorage,
                dispatchEvent: vi.fn(),
                location: {
                    pathname: '/',
                    replace: vi.fn(),
                },
            },
            configurable: true,
        });
        window.localStorage.clear();
        window.sessionStorage.clear();
        clearPortalSession();
    });

    it('restores session-scoped tokens after refresh', () => {
        persistPortalSession(
            {
                accessToken: 'access-session',
                refreshToken: 'refresh-session',
                expiresIn: 300,
            },
            false,
        );

        expect(JSON.parse(window.sessionStorage.getItem('nrs.portal.session') ?? '{}').scope).toBe('session');
        expect(restorePortalSession()).toEqual({
            accessToken: 'access-session',
            refreshToken: 'refresh-session',
            expiresIn: 300,
        });
    });

    it('stores remembered sessions in localStorage', () => {
        persistPortalSession(
            {
                accessToken: 'access-local',
                refreshToken: 'refresh-local',
            },
            true,
        );

        expect(JSON.parse(window.localStorage.getItem('nrs.portal.session') ?? '{}').scope).toBe('local');
        expect(window.sessionStorage.getItem('nrs.portal.session')).toBeNull();
    });

    it('keeps existing storage scope on refresh token rotation', () => {
        persistPortalSession(
            {
                accessToken: 'access-local',
                refreshToken: 'refresh-local',
            },
            true,
        );

        persistPortalSession({
            accessToken: 'access-local-2',
            refreshToken: 'refresh-local-2',
            expiresIn: 600,
        });

        expect(restorePortalSession()).toEqual({
            accessToken: 'access-local-2',
            refreshToken: 'refresh-local-2',
            expiresIn: 600,
        });
        expect(JSON.parse(window.localStorage.getItem('nrs.portal.session') ?? '{}').scope).toBe('local');
    });
});
