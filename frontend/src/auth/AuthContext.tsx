import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import keycloak from './keycloak';
import { financeClient } from '../api/client';
import { effectiveRoleFromRealmRoles, readRealmRolesFromTokenParsed } from './jwtRoleUtils';

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
    /** JWT realm_access önceliği; uygulama rolleri yoksa /api/users/me */
    role: UserRole | null;
    jwtRealmRoles: string[];
    login: () => void;
    logout: () => void;
    ready: boolean;
    refetchUser: () => Promise<void>;
    permissionFlash: string | null;
    dismissPermissionFlash: () => void;
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
    const [jwtRealmRoles, setJwtRealmRoles] = useState<string[]>([]);
    const [ready, setReady] = useState(false);
    const [permissionFlash, setPermissionFlash] = useState<string | null>(null);

    const syncRolesFromKeycloak = useCallback(() => {
        setJwtRealmRoles(readRealmRolesFromTokenParsed(keycloak.tokenParsed));
        if (keycloak.token) setToken(keycloak.token);
    }, []);

    const login = useCallback(() => {
        keycloak.login({ locale: 'tr' });
    }, []);

    const logout = useCallback(() => {
        keycloak.logout();
        setUser(null);
        setJwtRealmRoles([]);
    }, []);

    const dismissPermissionFlash = useCallback(() => setPermissionFlash(null), []);

    const refetchUser = useCallback(async () => {
        if (!keycloak.authenticated || !keycloak.token) return;
        try {
            const res = await financeClient.get('/api/users/me');
            const raw = res.data?.data ?? res.data;
            if (raw?.id != null && raw?.role) {
                const apiRole = parseRole(raw.role);
                setUser({
                    id: raw.id,
                    username: raw.username ?? '',
                    email: raw.email ?? '',
                    role: apiRole ?? 'USER',
                });

                const jwtR = effectiveRoleFromRealmRoles(readRealmRolesFromTokenParsed(keycloak.tokenParsed));
                if (apiRole && jwtR && apiRole !== jwtR) {
                    setPermissionFlash('Yetkileriniz güncellendi, arayüz yenileniyor…');
                    try {
                        await keycloak.updateToken(-1);
                        syncRolesFromKeycloak();
                    } catch {
                        /* token yenilenemezse kullanıcı bir sonraki girişte alır */
                    }
                    window.setTimeout(() => setPermissionFlash(null), 4500);
                }
            }
        } catch {
            setUser(null);
        }
    }, [syncRolesFromKeycloak]);

    const role = useMemo(() => {
        const fromJwt = effectiveRoleFromRealmRoles(jwtRealmRoles);
        if (fromJwt) return fromJwt;
        return user?.role ?? null;
    }, [jwtRealmRoles, user]);

    useEffect(() => {
        keycloak
            .init({
                onLoad: 'check-sso',
                checkLoginIframe: false,
            })
            .then((auth) => {
                setIsAuthenticated(auth);
                if (auth && keycloak.token) {
                    setToken(keycloak.token);
                    syncRolesFromKeycloak();
                }
                setReady(true);
            })
            .catch(() => setReady(true));
    }, [syncRolesFromKeycloak]);

    useEffect(() => {
        const onTok = () => syncRolesFromKeycloak();
        keycloak.onAuthRefreshSuccess = onTok;
        keycloak.onAuthSuccess = onTok;
        return () => {
            keycloak.onAuthRefreshSuccess = undefined;
            keycloak.onAuthSuccess = undefined;
        };
    }, [syncRolesFromKeycloak]);

    useEffect(() => {
        if (!keycloak.authenticated) return;
        const updateToken = () => {
            keycloak.updateToken(70).then((refreshed) => {
                if (refreshed) syncRolesFromKeycloak();
            }).catch(() => keycloak.login());
        };
        const interval = setInterval(updateToken, 60000);
        return () => clearInterval(interval);
    }, [isAuthenticated, syncRolesFromKeycloak]);

    useEffect(() => {
        if (keycloak.authenticated && keycloak.token) setToken(keycloak.token);
    }, [isAuthenticated]);

    useEffect(() => {
        if (isAuthenticated) {
            void refetchUser();
        } else {
            setUser(null);
            setJwtRealmRoles([]);
        }
    }, [isAuthenticated, refetchUser]);

    return (
        <AuthContext.Provider
            value={{
                isAuthenticated,
                token,
                user,
                role,
                jwtRealmRoles,
                login,
                logout,
                ready,
                refetchUser,
                permissionFlash,
                dismissPermissionFlash,
            }}
        >
            {permissionFlash && (
                <div
                    role="status"
                    style={{
                        position: 'fixed',
                        top: 72,
                        left: '50%',
                        transform: 'translateX(-50%)',
                        zIndex: 9999,
                        padding: '10px 18px',
                        borderRadius: 10,
                        background: 'rgba(15, 23, 42, 0.92)',
                        color: '#e2e8f0',
                        fontSize: '0.875rem',
                        boxShadow: '0 8px 24px rgba(0,0,0,0.35)',
                        maxWidth: 'min(420px, 92vw)',
                        textAlign: 'center',
                    }}
                >
                    {permissionFlash}
                    <button
                        type="button"
                        onClick={dismissPermissionFlash}
                        style={{
                            marginLeft: 12,
                            background: 'transparent',
                            border: 'none',
                            color: '#94a3b8',
                            cursor: 'pointer',
                            fontSize: '0.8rem',
                        }}
                    >
                        Tamam
                    </button>
                </div>
            )}
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error('useAuth must be used within AuthProvider');
    return ctx;
}
