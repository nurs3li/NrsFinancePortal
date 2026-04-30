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
    if (typeof maybe.success === 'boolean' && 'data' in maybe && 'errors' in maybe) {
        return maybe.data;
    }
    return payload;
}

let loginRedirectInFlight = false;

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
    (err) => handleAuthErrorWithSingleRetry(err)
);

export const marketClient = axios.create({
    baseURL: marketApiUrl,
    timeout: httpTimeoutMs,
    headers: { 'Content-Type': 'application/json' },
});

marketClient.interceptors.request.use((config) => {
    const requestPath = String(config.url ?? '');
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
