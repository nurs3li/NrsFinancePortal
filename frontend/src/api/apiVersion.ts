const DEFAULT_API_VERSION = 'v1';

/** Active public REST API version segment (e.g. {@code v1} → {@code /api/v1/...}). */
export const API_VERSION = String(import.meta.env.VITE_API_VERSION ?? DEFAULT_API_VERSION).trim() || DEFAULT_API_VERSION;

/**
 * Prefixes unversioned public API paths with the configured version segment.
 * Internal and already-versioned paths are left unchanged.
 */
export function withApiVersion(url: string | undefined): string {
    if (!url) return url ?? '';
    if (url.startsWith('/internal/')) return url;
    if (/^\/api\/v\d+\//.test(url)) return url;
    if (url.startsWith('/api/')) {
        return url.replace(/^\/api\//, `/api/${API_VERSION}/`);
    }
    return url;
}

/** Maps {@code /api/v1/...} back to legacy {@code /api/...} for shared path checks. */
export function toLegacyPublicApiPath(path: string): string {
    if (path.startsWith('/api/v1/')) {
        return `/api/${path.slice('/api/v1/'.length)}`;
    }
    if (path === '/api/v1') {
        return '/api';
    }
    return path;
}

export function isPublicAuthPath(path: string): boolean {
    const legacy = toLegacyPublicApiPath(withApiVersion(path));
    return (
        legacy.includes('/api/public/login') ||
        legacy.includes('/api/public/register') ||
        legacy.includes('/api/public/token/refresh')
    );
}

export function isPublicMarketReadPath(path: string): boolean {
    const legacy = toLegacyPublicApiPath(withApiVersion(path));
    return legacy.startsWith('/api/news') || legacy.startsWith('/api/market/');
}
