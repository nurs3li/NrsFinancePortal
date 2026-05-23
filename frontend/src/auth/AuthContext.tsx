import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import keycloak from './keycloak';
import { applyKeycloakTokens } from './applyKeycloakTokens';
import { clearPortalSession, PORTAL_AUTH_EXPIRED_EVENT } from './portalSession';
import { financeClient } from '../api/client';
import { effectiveRoleFromRealmRoles, readRealmRolesFromTokenParsed } from './jwtRoleUtils';
import { loginResponseToTokens, portalLogin, portalRefreshToken, type LoginRequest } from '../services/authApi';
import { useTheme } from '../theme/ThemeContext';

export type UserRole = 'USER' | 'ADMIN';

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
    loginWithCredentials: (body: LoginRequest) => Promise<{ otpRequired: boolean; message?: string | null }>;
    logout: () => void;
    ready: boolean;
    refetchUser: () => Promise<void>;
    permissionFlash: string | null;
    dismissPermissionFlash: () => void;
};

const AuthContext = createContext<AuthContextType | null>(null);

function parseRole(r: string | undefined): UserRole | null {
    if (r === 'ADMIN' || r === 'USER') return r;
    return null;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const { tokens, theme } = useTheme();
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

    const loginWithCredentials = useCallback(
        async (body: LoginRequest) => {
            const data = await portalLogin(body);
            if (data.otpRequired) {
                return { otpRequired: true, message: data.message ?? null };
            }
            const tokens = loginResponseToTokens(data);
            if (!tokens?.accessToken) {
                throw new Error('Giriş yanıtında token yok');
            }
            applyKeycloakTokens(tokens);
            setIsAuthenticated(true);
            setToken(tokens.accessToken);
            syncRolesFromKeycloak();
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
                }
            } catch {
                if (data.userId != null && data.role) {
                    setUser({
                        id: data.userId,
                        username: data.username ?? '',
                        email: data.email ?? '',
                        role: parseRole(data.role) ?? 'USER',
                    });
                }
            }
            return { otpRequired: false };
        },
        [syncRolesFromKeycloak]
    );

    const logout = useCallback(() => {
        clearPortalSession();
        setIsAuthenticated(false);
        setToken(null);
        setUser(null);
        setJwtRealmRoles([]);
        window.location.replace('/');
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
        const onExpired = () => {
            setIsAuthenticated(false);
            setToken(null);
            setUser(null);
            setJwtRealmRoles([]);
        };
        window.addEventListener(PORTAL_AUTH_EXPIRED_EVENT, onExpired);
        return () => window.removeEventListener(PORTAL_AUTH_EXPIRED_EVENT, onExpired);
    }, []);

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
        if (!keycloak.authenticated || !keycloak.refreshToken) return;
        const updateToken = async () => {
            try {
                const data = await portalRefreshToken(keycloak.refreshToken!);
                const tokens = loginResponseToTokens(data);
                if (!tokens?.accessToken) {
                    throw new Error('refresh failed');
                }
                applyKeycloakTokens(tokens);
                syncRolesFromKeycloak();
            } catch {
                clearPortalSession();
                setIsAuthenticated(false);
                setToken(null);
                setUser(null);
                setJwtRealmRoles([]);
            }
        };
        const interval = setInterval(() => void updateToken(), 60000);
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
                loginWithCredentials,
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
                        background: theme === 'light' ? 'rgba(255, 255, 255, 0.97)' : 'rgba(15, 23, 42, 0.92)',
                        color: tokens.text,
                        border: theme === 'light' ? `1px solid ${tokens.border}` : 'none',
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
                            color: tokens.textMuted,
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
