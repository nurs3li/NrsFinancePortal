import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import keycloak from './keycloak';
import { financeClient } from '../api/client';

export type UserRole = 'USER' | 'ADMIN' | 'FINANCE_MANAGER';

export type CurrentUser = {
    id: number;
    username: string;
    email: string;
    role: UserRole;
};

type AuthContextType = {
    isAuthenticated: boolean;
    token: string | null;
    user: CurrentUser | null;
    role: UserRole | null;
    login: () => void;
    logout: () => void;
    ready: boolean;
    refetchUser: () => Promise<void>;
};

const AuthContext = createContext<AuthContextType | null>(null);

function parseRole(r: string | undefined): UserRole | null {
    if (r === 'ADMIN' || r === 'FINANCE_MANAGER' || r === 'USER') return r;
    return null;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [token, setToken] = useState<string | null>(null);
    const [user, setUser] = useState<CurrentUser | null>(null);
    const [ready, setReady] = useState(false);

    const login = useCallback(() => {
        keycloak.login();
    }, []);

    const logout = useCallback(() => {
        keycloak.logout();
        setUser(null);
    }, []);

    const refetchUser = useCallback(async () => {
        if (!keycloak.authenticated || !keycloak.token) return;
        try {
            const res = await financeClient.get('/api/users/me');
            const raw = res.data?.data ?? res.data;
            if (raw?.id != null && raw?.role) {
                const role = parseRole(raw.role);
                setUser({
                    id: raw.id,
                    username: raw.username ?? '',
                    email: raw.email ?? '',
                    role: role ?? 'USER',
                });
            }
        } catch {
            setUser(null);
        }
    }, []);

    useEffect(() => {
        keycloak
            .init({
                onLoad: 'check-sso',
                // Chrome üçüncü taraf çerez kısıtları / iframe cache sorunlarında
                // login-status-iframe takılı kalıp uygulamayı kilitlemesin diye.
                checkLoginIframe: false,
            })
            .then((auth) => {
                setIsAuthenticated(auth);
                if (auth && keycloak.token) {
                    setToken(keycloak.token);
                }
                setReady(true);
            })
            .catch(() => setReady(true));
    }, []);

    useEffect(() => {
        if (!keycloak.authenticated) return;
        const updateToken = () => {
            keycloak.updateToken(70).then((refreshed) => {
                if (refreshed && keycloak.token) setToken(keycloak.token);
            }).catch(() => keycloak.login());
        };
        const interval = setInterval(updateToken, 60000);
        return () => clearInterval(interval);
    }, [isAuthenticated]);

    useEffect(() => {
        if (keycloak.authenticated && keycloak.token) setToken(keycloak.token);
    }, [isAuthenticated]);

    useEffect(() => {
        if (isAuthenticated) {
            refetchUser();
        } else {
            setUser(null);
        }
    }, [isAuthenticated, refetchUser]);

    return (
        <AuthContext.Provider value={{ isAuthenticated, token, user, role: user?.role ?? null, login, logout, ready, refetchUser }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error('useAuth must be used within AuthProvider');
    return ctx;
}