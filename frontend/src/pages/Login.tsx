import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import { financeClient } from '../api/client';

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
        return 'Your account is suspended. Please contact support for access.';
    }
    if (blob.includes('temporarily_disabled')) {
        return 'Your account is temporarily disabled. Please try later or contact support.';
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

export function Login() {
    const { isAuthenticated, login, ready, role } = useAuth();
    const { tokens } = useTheme();
    const { t } = useLanguage();
    const navigate = useNavigate();
    const location = useLocation();
    const [searchParams, setSearchParams] = useSearchParams();
    const [loginError, setLoginError] = useState<string | null>(null);
    const [registerMode, setRegisterMode] = useState(false);
    const [registerBusy, setRegisterBusy] = useState(false);
    const [registerMessage, setRegisterMessage] = useState<string | null>(null);
    const [registerError, setRegisterError] = useState<string | null>(null);
    const [email, setEmail] = useState('');
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [code, setCode] = useState('');
    const from = (location.state as { from?: { pathname: string } })?.from?.pathname;

    const suspendedBanner = useMemo(() => searchParams.get('suspended'), [searchParams]);

    const destination = (() => {
        if (from && from !== '/' && from !== '/dashboard') return from;
        if (role === 'ADMIN') return '/admin';
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
            setLoginError(t('login.suspended', 'Hesabınız askıya alındı. Erişim için destek ile iletişime geçin.'));
        }
        if (fromOAuth || suspendedBanner === '1') {
            setSearchParams({}, { replace: true });
            if (window.location.hash.includes('state=')) {
                window.history.replaceState(null, '', window.location.pathname + window.location.search);
            }
        }
    }, [ready, suspendedBanner, setSearchParams, t]);

    const requestRegisterCode = async () => {
        setRegisterError(null);
        setRegisterMessage(null);
        if (!email.trim()) {
            setRegisterError('Lütfen e-posta girin.');
            return;
        }
        setRegisterBusy(true);
        try {
            const res = await financeClient.post('/api/public/register/request-code', { email });
            const msg = res.data?.data ?? 'Doğrulama kodu e-postanıza gönderildi.';
            setRegisterMessage(msg);
        } catch (err: any) {
            setRegisterError(err?.response?.data?.errors?.message ?? err?.response?.data?.message ?? 'Kod gönderilemedi.');
        } finally {
            setRegisterBusy(false);
        }
    };

    const completeRegistration = async () => {
        setRegisterError(null);
        setRegisterMessage(null);
        if (!email.trim() || !username.trim() || !password || !code.trim()) {
            setRegisterError('Lütfen tüm alanları doldurun.');
            return;
        }
        if (password !== confirmPassword) {
            setRegisterError('Şifreler eşleşmiyor.');
            return;
        }
        setRegisterBusy(true);
        try {
            const res = await financeClient.post('/api/public/register/complete', {
                email,
                username,
                password,
                code,
            });
            const msg = res.data?.data ?? 'Kayıt tamamlandı.';
            setRegisterMessage(msg);
            setCode('');
        } catch (err: any) {
            setRegisterError(err?.response?.data?.errors?.message ?? err?.response?.data?.message ?? 'Kayıt tamamlanamadı.');
        } finally {
            setRegisterBusy(false);
        }
    };

    if (!ready) {
        return (
            <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: tokens.bg, color: tokens.text }}>
                <span style={{ fontSize: '0.9375rem', color: tokens.textMuted }}>{t('common.loading', 'Yükleniyor...')}</span>
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
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: 8 }}>{t('login.title', 'Giriş')}</h1>
            <p style={{ fontSize: '0.9375rem', color: tokens.textMuted, marginBottom: 24 }}>
                {t('login.subtitle', 'Finans portalına erişmek için Keycloak ile giriş yapın veya yeni hesap oluşturun.')}
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
                {t('login.keycloak', 'Keycloak ile Giriş')}
            </button>
            <p style={{ fontSize: '0.875rem', color: tokens.textMuted, marginTop: 16 }}>
                {t('login.noAccount', 'Hesabınız yok mu?')}
            </p>
            <button
                type="button"
                onClick={() => {
                    setRegisterMode((v) => !v);
                    setRegisterError(null);
                    setRegisterMessage(null);
                }}
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
                {t('login.register', 'Kayıt ol')}
            </button>
            {registerMode && (
                <div style={{ marginTop: 16, textAlign: 'left', display: 'grid', gap: 8 }}>
                    <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="E-posta" style={{ padding: 10, borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bg }} />
                    <button type="button" disabled={registerBusy} onClick={requestRegisterCode} style={{ padding: '10px 12px', borderRadius: 8, border: `1px solid ${tokens.accent}`, background: 'transparent', color: tokens.accent, cursor: 'pointer' }}>
                        {registerBusy ? 'Gönderiliyor...' : 'Doğrulama kodu gönder'}
                    </button>
                    <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="Kullanıcı adı" style={{ padding: 10, borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bg }} />
                    <input value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Şifre" type="password" style={{ padding: 10, borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bg }} />
                    <input value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} placeholder="Şifre (tekrar)" type="password" style={{ padding: 10, borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bg }} />
                    <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="Mail doğrulama kodu (6 hane)" style={{ padding: 10, borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bg }} />
                    <button type="button" disabled={registerBusy} onClick={completeRegistration} style={{ padding: '10px 12px', borderRadius: 8, border: 'none', background: tokens.accentGradient, color: '#fff', cursor: 'pointer' }}>
                        {registerBusy ? 'İşleniyor...' : 'Kayıtı tamamla'}
                    </button>
                    {registerMessage && <div style={{ color: '#22c55e', fontSize: '0.85rem' }}>{registerMessage}</div>}
                    {registerError && <div style={{ color: tokens.error ?? '#f87171', fontSize: '0.85rem' }}>{registerError}</div>}
                </div>
            )}
        </div>
    );
}