import axios from 'axios';
import keycloak from '../auth/keycloak';

const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8085';
const marketApiUrl = import.meta.env.VITE_MARKET_API_URL || 'http://localhost:8083';
const notificationApiUrl = import.meta.env.VITE_NOTIFICATION_API_URL || 'http://localhost:8089';
const httpTimeoutMs = Number(import.meta.env.VITE_HTTP_TIMEOUT_MS) || 45000;

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
    (r) => r,
    (err) => {
        if (err.response?.status === 401) keycloak.login();
        return Promise.reject(err);
    }
);

export const marketClient = axios.create({
    baseURL: marketApiUrl,
    timeout: httpTimeoutMs,
    headers: { 'Content-Type': 'application/json' },
});

marketClient.interceptors.request.use((config) => {
    if (keycloak.authenticated && keycloak.token) {
        config.headers.Authorization = `Bearer ${keycloak.token}`;
    }
    return config;
});
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
    (r) => r,
    (err) => {
        if (err.response?.status === 401) keycloak.login();
        return Promise.reject(err);
    }
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
    (r) => r,
    (err) => {
        // 401'de login'e yönlendirme - notification servisi down/yanlış JWT olsa bile sayfa döngüye girmesin
        // if (err.response?.status === 401) keycloak.login();
        return Promise.reject(err);
    }
);
