import axios from 'axios';
import keycloak from '../auth/keycloak';

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
    // Only unwrap successful envelopes. On failures, preserve the full envelope
    // so callers can read errors instead of receiving null data.
    if (maybe.success === true && 'data' in maybe && 'errors' in maybe) {
        return maybe.data;
    }
    return payload;
}

let loginRedirectInFlight = false;

function getPreferredAppLang(): string {
    if (typeof window === 'undefined') return 'tr';
    const fromApp = window.localStorage.getItem('app.lang');
    if (fromApp && fromApp.trim()) return fromApp.trim();
    const fromLegacy = window.localStorage.getItem('app.newsLang');
    if (fromLegacy && fromLegacy.trim()) return fromLegacy.trim();
    return 'tr';
}

async function handleAuthErrorWithSingleRetry(err: any): Promise<any> {
    const status = err?.response?.status;
    const originalRequest = err?.config as (Record<string, any> & { headers?: Record<string, any> }) | undefined;
    if (status !== 401 || !originalRequest) {
        return Promise.reject(err);
    }

    if (originalRequest.__retriedAfterRefresh) {
        if (!loginRedirectInFlight && keycloak.authenticated) {
            loginRedirectInFlight = true;
            setTimeout(() => {
                loginRedirectInFlight = false;
            }, 10_000);
            keycloak.login();
        }
        return Promise.reject(err);
    }

    if (keycloak.authenticated) {
        try {
            await keycloak.updateToken(30);
            if (keycloak.token) {
                originalRequest.__retriedAfterRefresh = true;
                originalRequest.headers = originalRequest.headers ?? {};
                originalRequest.headers.Authorization = `Bearer ${keycloak.token}`;
                return axios.request(originalRequest);
            }
        } catch {
            // Token yenileme başarısızsa aşağıdaki tek-seferlik login yönlendirmesine düş.
        }
    }

    if (!loginRedirectInFlight) {
        loginRedirectInFlight = true;
        setTimeout(() => {
            loginRedirectInFlight = false;
        }, 10_000);
        keycloak.login();
    }
    return Promise.reject(err);
}

export const financeClient = axios.create({
    baseURL: apiUrl,
    timeout: httpTimeoutMs,
    headers: { 'Content-Type': 'application/json' },
});

financeClient.interceptors.request.use((config) => {
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
            errs.error === 'USER_LOGIN_SUSPENDED'
        ) {
            await keycloak.logout({ redirectUri: `${window.location.origin}/login?suspended=1` });
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
    const requestPath = String(config.url ?? '');
    config.headers = config.headers ?? {};
    const lang = getPreferredAppLang();
    if (lang) {
        config.headers['Accept-Language'] = lang;
    }
    const isPublicMarketRead =
        config.method?.toLowerCase() === 'get' &&
        (requestPath.startsWith('/api/news') || requestPath.startsWith('/api/market/'));

    // Public market/news read endpoint'lerinde Bearer göndermeyelim:
    // geçersiz/expired token bazı ortamlarda permitAll endpoint'i bile 401'e düşürebiliyor.
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
const metricsApiUrl = import.meta.env.VITE_METRICS_URL || 'http://localhost:8088';

export const metricsClient = axios.create({
    baseURL: metricsApiUrl,
    timeout: httpTimeoutMs,
    headers: { 'Content-Type': 'application/json' },
});

metricsClient.interceptors.request.use((config) => {
    config.headers = config.headers ?? {};
    const lang = getPreferredAppLang();
    if (lang) config.headers['Accept-Language'] = lang;
    if (keycloak.authenticated && keycloak.token) {
        config.headers.Authorization = `Bearer ${keycloak.token}`;
    }
    return config;
});

metricsClient.interceptors.response.use(
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
