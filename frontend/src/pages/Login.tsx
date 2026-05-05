import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';

function decodeOAuthHint(raw: string | null): string | null {
    if (!raw) return null;
    try {
        return decodeURIComponent(raw.replace(/\+/g, ' '));
    } catch {
        return raw;
    }
}

function mapLoginOAuthError(error: string | null, description: string | null): string | null {
    const desc = decodeOAuthHint(description) ?? '';
    const err = decodeOAuthHint(error) ?? '';
    const blob = `${desc} ${err}`.toLowerCase();
    if (blob.includes('disabled') || blob.includes('account_disabled') || blob.includes('inactive')) {
        return 'Hesabınız askıya alındı. Erişim için destek ile iletişime geçin.';
    }
    if (blob.includes('temporarily_disabled')) {
        return 'Hesabınız geçici olarak devre dışı. Lütfen daha sonra deneyin veya destek ile iletişime geçin.';
    }
    if (err || desc) {
        return desc || err || null;
    }
    return null;
}

function parseOAuthErrorsFromLocation(): string | null {
    const qs = new URLSearchParams(window.location.search);
    const mapped = mapLoginOAuthError(qs.get('error'), qs.get('error_description'));
    if (mapped) return mapped;

    const hash = window.location.hash?.replace(/^#/, '') ?? '';
    if (!hash) return null;
    const hp = new URLSearchParams(hash);
    return mapLoginOAuthError(hp.get('error'), hp.get('error_description'));
}

const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8081';
const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'nrs-finance';
const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'nrs-frontend';

export function Login() {
    const { isAuthenticated, login, ready, role } = useAuth();
    const { tokens } = useTheme();
    const navigate = useNavigate();
    const location = useLocation();
    const [searchParams, setSearchParams] = useSearchParams();
    const [loginError, setLoginError] = useState<string | null>(null);
    const from = (location.state as { from?: { pathname: string } })?.from?.pathname;

    const suspendedBanner = useMemo(() => searchParams.get('suspended'), [searchParams]);

    const destination = (() => {
        if (from && from !== '/' && from !== '/dashboard') return from;
        if (role === 'ADMIN') return '/admin';
        if (role === 'FINANCE_MANAGER') return '/fm/tasks';
        if (role === 'USER') return '/dashboard';
        return '/dashboard';
    })();

    useEffect(() => {
        if (!ready) return;
        if (isAuthenticated) navigate(destination, { replace: true });
    }, [ready, isAuthenticated, navigate, destination]);

    useEffect(() => {
        if (!ready) return;
        const fromOAuth = parseOAuthErrorsFromLocation();
        if (fromOAuth) {
            setLoginError(fromOAuth);
        } else if (suspendedBanner === '1') {
            setLoginError('Hesabınız askıya alındı. Erişim için destek ile iletişime geçin.');
        }
        if (fromOAuth || suspendedBanner === '1') {
            setSearchParams({}, { replace: true });
            if (window.location.hash.includes('state=')) {
                window.history.replaceState(null, '', window.location.pathname + window.location.search);
            }
        }
    }, [ready, suspendedBanner, setSearchParams]);

    const goToRegister = () => {
        const redirectUri = encodeURIComponent(window.location.origin + '/');
        const url = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/registrations?client_id=${KEYCLOAK_CLIENT_ID}&redirect_uri=${redirectUri}&response_type=code&scope=openid`;
        window.location.href = url;
    };

    if (!ready) {
        return (
            <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: tokens.bg, color: tokens.text }}>
                <span style={{ fontSize: '0.9375rem', color: tokens.textMuted }}>Yükleniyor...</span>
            </div>
        );
    }
    if (isAuthenticated) return null;

    return (
        <div
            style={{
                maxWidth: 400,
                margin: '80px auto',
                textAlign: 'center',
                padding: 24,
                background: tokens.bgCard,
                border: `1px solid ${tokens.border}`,
                borderRadius: 12,
                color: tokens.text,
            }}
        >
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: 8 }}>Giriş</h1>
            <p style={{ fontSize: '0.9375rem', color: tokens.textMuted, marginBottom: 24 }}>
                Finans portalına erişmek için Keycloak ile giriş yapın veya yeni hesap oluşturun.
            </p>
            {loginError && (
                <div
                    role="alert"
                    style={{
                        marginBottom: 16,
                        padding: '10px 12px',
                        borderRadius: 8,
                        background: 'rgba(239,68,68,0.12)',
                        border: `1px solid rgba(239,68,68,0.45)`,
                        color: tokens.error ?? '#fca5a5',
                        fontSize: '0.875rem',
                        textAlign: 'left',
                    }}
                >
                    {loginError}
                </div>
            )}
            <button
                type="button"
                onClick={login}
                style={{
                    width: '100%',
                    padding: '12px 24px',
                    fontSize: '0.9375rem',
                    fontWeight: 600,
                    background: tokens.accentGradient,
                    color: '#fff',
                    border: 'none',
                    borderRadius: 8,
                    cursor: 'pointer',
                }}
            >
                Keycloak ile Giriş
            </button>
            <p style={{ fontSize: '0.875rem', color: tokens.textMuted, marginTop: 16 }}>
                Hesabınız yok mu?
            </p>
            <button
                type="button"
                onClick={goToRegister}
                style={{
                    width: '100%',
                    padding: '12px 24px',
                    fontSize: '0.9375rem',
                    fontWeight: 600,
                    background: 'transparent',
                    color: tokens.accent,
                    border: `2px solid ${tokens.accent}`,
                    borderRadius: 8,
                    cursor: 'pointer',
                }}
            >
                Kayıt ol
            </button>
        </div>
    );
}