import axios from 'axios';
import keycloak from '../auth/keycloak';

const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8085';
const marketApiUrl = import.meta.env.VITE_MARKET_API_URL || 'http://localhost:8083';

export const financeClient = axios.create({
    baseURL: apiUrl,
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
    headers: { 'Content-Type': 'application/json' },
});

marketClient.interceptors.request.use((config) => {
    if (keycloak.authenticated && keycloak.token) {
        config.headers.Authorization = `Bearer ${keycloak.token}`;
    }
    return config;
});