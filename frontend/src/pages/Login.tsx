import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';

const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8081';
const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'nrs-finance';
const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'nrs-frontend';

export function Login() {
    const { isAuthenticated, login, ready, role } = useAuth();
    const { tokens } = useTheme();
    const navigate = useNavigate();
    const location = useLocation();
    const from = (location.state as { from?: { pathname: string } })?.from?.pathname;

    const destination = (() => {
        if (from && from !== '/' && from !== '/dashboard') return from;
        if (role === 'ADMIN') return '/admin/metrics';
        if (role === 'FINANCE_MANAGER') return '/fm/tasks';
        return '/dashboard';
    })();

    useEffect(() => {
        if (!ready) return;
        if (isAuthenticated) navigate(destination, { replace: true });
    }, [ready, isAuthenticated, navigate, destination]);

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