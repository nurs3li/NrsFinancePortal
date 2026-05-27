import axios from 'axios';
import { applyKeycloakTokens } from '../auth/applyKeycloakTokens';
import keycloak from '../auth/keycloak';
import {
    isPublicAuthRequest,
    notifyAuthExpired,
    redirectToPortalSignIn,
} from '../auth/portalSession';
import { loginResponseToTokens, portalRefreshToken } from '../services/authApi';
import { isPublicMarketReadPath, withApiVersion } from './apiVersion';

const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8085';
const marketApiUrl = import.meta.env.VITE_MARKET_API_URL || 'http://localhost:8083';
const notificationApiUrl = import.meta.env.VITE_NOTIFICATION_API_URL || 'http://localhost:8089';
const httpTimeoutMs = Number(import.meta.env.VITE_HTTP_TIMEOUT_MS) || 45000;
const financeUnwrapEnabled = String(import.meta.env.VITE_FINANCE_UNWRAP_ENABLED ?? 'false') === 'true';

type EnvelopeLike = {
    success?: boolean;
    data?: unknown;
    errors?: unknown;
};

function unwrapEnvelopePayload(payload: unknown): unknown {
    if (!payload || typeof payload !== 'object') return payload;
    const maybe = payload as EnvelopeLike;
    if (maybe.success === true && 'data' in maybe && 'errors' in maybe) {
        return maybe.data;
    }
    return payload;
}

/** responseType: 'arraybuffer' isteklerinde hata gövdesi ArrayBuffer gelebilir. */
export function readFinanceBinaryErrorMessage(err: unknown): string | undefined {
    const data = (err as { response?: { data?: unknown } })?.response?.data;
    if (data == null) return undefined;
    if (typeof data === 'object' && !(data instanceof ArrayBuffer)) {
        const errs = (data as { errors?: { error?: string; message?: string } }).errors;
        if (errs && typeof errs === 'object') {
            if (typeof errs.error === 'string') return errs.error;
            if (typeof errs.message === 'string') return errs.message;
        }
        const m = (data as { message?: string }).message;
        if (typeof m === 'string') return m;
        return undefined;
    }
    if (data instanceof ArrayBuffer) {
        try {
            const text = new TextDecoder().decode(data);
            const j = JSON.parse(text) as { errors?: { error?: string; message?: string }; message?: string };
            return j?.errors?.error ?? j?.errors?.message ?? j?.message;
        } catch {
            return undefined;
        }
    }
    if (typeof data === 'string') {
        try {
            const j = JSON.parse(data) as { errors?: { error?: string; message?: string } };
            return j?.errors?.error ?? j?.errors?.message;
        } catch {
            return data;
        }
    }
    return undefined;
}

function getPreferredAppLang(): string {
    if (typeof window === 'undefined') return 'tr';
    const fromApp = window.localStorage.getItem('app.lang');
    if (fromApp && fromApp.trim()) return fromApp.trim();
    const fromLegacy = window.localStorage.getItem('app.newsLang');
    if (fromLegacy && fromLegacy.trim()) return fromLegacy.trim();
    return 'tr';
}

let authRecoveryInFlight = false;

async function handleAuthErrorWithSingleRetry(err: unknown): Promise<never> {
    const ax = err as {
        response?: { status?: number };
        config?: Record<string, unknown> & { headers?: Record<string, string>; url?: string };
    };
    const status = ax?.response?.status;
    const originalRequest = ax?.config;
    if (status !== 401 || !originalRequest) {
        return Promise.reject(err);
    }

    const requestUrl = String(originalRequest.url ?? '');
    if (isPublicAuthRequest(requestUrl)) {
        return Promise.reject(err);
    }

    if (originalRequest.__retriedAfterRefresh) {
        if (!authRecoveryInFlight) {
            authRecoveryInFlight = true;
            notifyAuthExpired('session');
            redirectToPortalSignIn({ signin: '1' });
            window.setTimeout(() => {
                authRecoveryInFlight = false;
            }, 5000);
        }
        return Promise.reject(err);
    }

    if (keycloak.authenticated && keycloak.refreshToken) {
        try {
            const data = await portalRefreshToken(keycloak.refreshToken);
            const tokens = loginResponseToTokens(data);
            if (tokens?.accessToken) {
                applyKeycloakTokens(tokens);
                originalRequest.__retriedAfterRefresh = true;
                originalRequest.headers = originalRequest.headers ?? {};
                originalRequest.headers.Authorization = `Bearer ${tokens.accessToken}`;
                return axios.request(originalRequest) as never;
            }
        } catch {
            /* refresh başarısız → portal giriş */
        }
    }

    if (!authRecoveryInFlight) {
        authRecoveryInFlight = true;
        notifyAuthExpired('session');
        redirectToPortalSignIn({ signin: '1' });
        window.setTimeout(() => {
            authRecoveryInFlight = false;
        }, 5000);
    }
    return Promise.reject(err);
}

export const financeClient = axios.create({
    baseURL: apiUrl,
    timeout: httpTimeoutMs,
    headers: { 'Content-Type': 'application/json' },
});

financeClient.interceptors.request.use((config) => {
    config.url = withApiVersion(config.url);
    config.headers = config.headers ?? {};
    const lang = getPreferredAppLang();
    if (lang) config.headers['Accept-Language'] = lang;
    if (keycloak.authenticated && keycloak.token) {
        config.headers.Authorization = `Bearer ${keycloak.token}`;
    }
    return config;
});

financeClient.interceptors.response.use(
    (r) => {
        if (financeUnwrapEnabled) {
            r.data = unwrapEnvelopePayload(r.data);
        }
        return r;
    },
    async (err) => {
        const payload = err?.response?.data;
        const errs = payload?.errors as Record<string, unknown> | undefined;
        if (
            err?.response?.status === 403 &&
            errs &&
            typeof errs === 'object' &&
            (errs.code === 'USER_LOGIN_SUSPENDED' || errs.error === 'USER_LOGIN_SUSPENDED')
        ) {
            notifyAuthExpired('suspended');
            redirectToPortalSignIn({ suspended: '1' });
            return Promise.reject(err);
        }
        return handleAuthErrorWithSingleRetry(err);
    }
);

export const marketClient = axios.create({
    baseURL: marketApiUrl,
    timeout: httpTimeoutMs,
    headers: { 'Content-Type': 'application/json' },
});

marketClient.interceptors.request.use((config) => {
    config.url = withApiVersion(config.url);
    const requestPath = String(config.url ?? '');
    config.headers = config.headers ?? {};
    const lang = getPreferredAppLang();
    if (lang) {
        config.headers['Accept-Language'] = lang;
    }
    const isPublicMarketRead =
        config.method?.toLowerCase() === 'get' && isPublicMarketReadPath(requestPath);

    if (!isPublicMarketRead && keycloak.authenticated && keycloak.token) {
        config.headers.Authorization = `Bearer ${keycloak.token}`;
    }
    return config;
});
marketClient.interceptors.response.use(
    (r) => {
        r.data = unwrapEnvelopePayload(r.data);
        return r;
    },
    (err) => handleAuthErrorWithSingleRetry(err)
);
export const notificationClient = axios.create({
    baseURL: notificationApiUrl,
    timeout: httpTimeoutMs,
    headers: { 'Content-Type': 'application/json' },
});

notificationClient.interceptors.request.use((config) => {
    config.url = withApiVersion(config.url);
    config.headers = config.headers ?? {};
    const lang = getPreferredAppLang();
    if (lang) config.headers['Accept-Language'] = lang;
    if (keycloak.authenticated && keycloak.token) {
        config.headers.Authorization = `Bearer ${keycloak.token}`;
    }
    return config;
});

notificationClient.interceptors.response.use(
    (r) => {
        r.data = unwrapEnvelopePayload(r.data);
        return r;
    },
    (err) => handleAuthErrorWithSingleRetry(err)
);
