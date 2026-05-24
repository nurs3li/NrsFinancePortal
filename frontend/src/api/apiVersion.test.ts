import { describe, expect, it } from 'vitest';
import {
    API_VERSION,
    isPublicAuthPath,
    isPublicMarketReadPath,
    toLegacyPublicApiPath,
    withApiVersion,
} from './apiVersion';

describe('apiVersion', () => {
    it('prefixes unversioned public API paths', () => {
        expect(withApiVersion('/api/dashboard/summary')).toBe('/api/v1/dashboard/summary');
    });

    it('leaves internal paths unchanged', () => {
        expect(withApiVersion('/internal/users/by-sub/abc')).toBe('/internal/users/by-sub/abc');
    });

    it('does not double-version already versioned paths', () => {
        expect(withApiVersion('/api/v1/portfolio/me/unified')).toBe('/api/v1/portfolio/me/unified');
    });

    it('normalizes versioned paths to legacy form', () => {
        expect(toLegacyPublicApiPath('/api/v1/market/overview')).toBe('/api/market/overview');
    });

    it('detects public auth paths after versioning', () => {
        expect(isPublicAuthPath('/api/public/login')).toBe(true);
        expect(isPublicAuthPath('/api/v1/public/login')).toBe(true);
    });

    it('detects public market read paths after versioning', () => {
        expect(isPublicMarketReadPath('/api/market/bank-rates/board')).toBe(true);
        expect(isPublicMarketReadPath('/api/news/latest')).toBe(true);
    });

    it('defaults API version to v1', () => {
        expect(API_VERSION).toBe('v1');
    });
});
