import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Moon, Settings, Sun } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { useLanguage } from '../i18n/LanguageContext';
import { readApiError } from '../api/envelope';
import { financeClient, readFinanceBinaryErrorMessage } from '../api/client';
import {
    completePasswordReset,
    requestPasswordResetCode,
    verifyPasswordResetCode,
} from '../services/passwordResetApi';
import { NrsBrandLockup } from '../components/NrsBrandLockup';
import { LandingHeroCarousel } from '../components/landing/LandingHeroCarousel';
import { LandingFeaturesFlip } from '../components/landing/LandingFeaturesFlip';
import { LandingSystemArchitecture } from '../components/landing/LandingSystemArchitecture';
import { useTheme } from '../theme/ThemeContext';
import './LandingPage.css';

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
        return 'Hesabınız geçici olarak devre dışı. Daha sonra tekrar deneyin veya destek ile iletişime geçin.';
    }
    if (err || desc) return desc || err || null;
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

type LandingPreferenceControlsProps = {
    className?: string;
    compact?: boolean;
};

function LandingPreferenceControls({ className = '', compact = false }: LandingPreferenceControlsProps) {
    const { theme, setTheme } = useTheme();
    const { lang, setLang, t } = useLanguage();
    const iconSize = compact ? 14 : 16;

    return (
        <div
            className={`landing-pref-panel${compact ? ' landing-pref-panel--compact' : ''}${className ? ` ${className}` : ''}`}
            aria-label={t('landing.preferencePanel', 'Tema ve dil ayarları')}
        >
            <div className="landing-segment-control landing-segment-control--icons" role="group" aria-label={t('landing.themeSwitchLabel', 'Tema seçimi')}>
                <button
                    type="button"
                    className={`landing-segment-btn landing-segment-btn--icon ${theme === 'light' ? 'is-active' : ''}`}
                    onClick={() => setTheme('light')}
                    aria-pressed={theme === 'light'}
                    title={t('theme.light', 'Açık Tema')}
                >
                    <Sun size={iconSize} />
                </button>
                <button
                    type="button"
                    className={`landing-segment-btn landing-segment-btn--icon ${theme === 'dark' ? 'is-active' : ''}`}
                    onClick={() => setTheme('dark')}
                    aria-pressed={theme === 'dark'}
                    title={t('theme.dark', 'Koyu Tema')}
                >
                    <Moon size={iconSize} />
                </button>
            </div>
            <div className="landing-segment-control" role="group" aria-label={t('landing.langSwitchLabel', 'Dil seçimi')}>
                <button
                    type="button"
                    className={`landing-segment-btn ${lang === 'tr' ? 'is-active' : ''}`}
                    onClick={() => setLang('tr')}
                    aria-pressed={lang === 'tr'}
                    title={t('lang.turkish', 'Türkçe')}
                >
                    {compact ? 'TR' : t('lang.turkish', 'Türkçe')}
                </button>
                <button
                    type="button"
                    className={`landing-segment-btn ${lang === 'en' ? 'is-active' : ''}`}
                    onClick={() => setLang('en')}
                    aria-pressed={lang === 'en'}
                    title={t('lang.english', 'English')}
                >
                    {compact ? 'ENG' : t('lang.english', 'English')}
                </button>
            </div>
        </div>
    );
}

export function LandingPage() {
    const { loginWithCredentials, ready, isAuthenticated, role } = useAuth();
    const navigate = useNavigate();
    const { t } = useLanguage();
    const [searchParams, setSearchParams] = useSearchParams();
    const [panelOpen, setPanelOpen] = useState(false);
    const [settingsOpen, setSettingsOpen] = useState(false);
    const settingsRef = useRef<HTMLDivElement>(null);
    const [panelTab, setPanelTab] = useState<'register' | 'signin'>('signin');
    const [loginError, setLoginError] = useState<string | null>(null);
    const [loginBusy, setLoginBusy] = useState(false);
    const [loginUsername, setLoginUsername] = useState('');
    const [loginPassword, setLoginPassword] = useState('');
    const [loginOtp, setLoginOtp] = useState('');
    const [loginRemember, setLoginRemember] = useState(false);
    const [loginOtpStep, setLoginOtpStep] = useState(false);
    const [forgotStep, setForgotStep] = useState<'email' | 'code' | 'password' | null>(null);
    const [forgotEmail, setForgotEmail] = useState('');
    const [forgotCode, setForgotCode] = useState('');
    const [forgotNewPassword, setForgotNewPassword] = useState('');
    const [forgotConfirmPassword, setForgotConfirmPassword] = useState('');
    const [forgotBusy, setForgotBusy] = useState(false);
    const [forgotError, setForgotError] = useState<string | null>(null);
    const [forgotMessage, setForgotMessage] = useState<string | null>(null);
    const [registerBusy, setRegisterBusy] = useState(false);
    const [registerMessage, setRegisterMessage] = useState<string | null>(null);
    const [registerError, setRegisterError] = useState<string | null>(null);
    const [email, setEmail] = useState('');
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [code, setCode] = useState('');
    const [cursor, setCursor] = useState({ x: 0, y: 0 });
    const [trail, setTrail] = useState<{ id: number; x: number; y: number }[]>([]);
    const trailId = useRef(0);

    const suspendedBanner = useMemo(() => searchParams.get('suspended'), [searchParams]);
    const signinBanner = useMemo(() => searchParams.get('signin'), [searchParams]);

    useEffect(() => {
        if (signinBanner === '1') {
            setPanelOpen(true);
            setPanelTab('signin');
        }
    }, [signinBanner]);

    useEffect(() => {
        const fromOAuth = parseOAuthErrorsFromLocation();
        if (fromOAuth) {
            setLoginError(fromOAuth);
            setPanelOpen(true);
            setPanelTab('signin');
        } else if (suspendedBanner === '1') {
            setLoginError(t('login.suspended', 'Hesabınız askıya alındı. Erişim için destek ile iletişime geçin.'));
            setPanelOpen(true);
            setPanelTab('signin');
        }
        if (fromOAuth || suspendedBanner === '1') {
            setSearchParams({}, { replace: true });
            if (window.location.hash.includes('state=')) {
                window.history.replaceState(null, '', window.location.pathname + window.location.search);
            }
        }
    }, [suspendedBanner, setSearchParams, t]);

    useEffect(() => {
        if (!settingsOpen) return;
        const onPointerDown = (e: PointerEvent) => {
            if (settingsRef.current && !settingsRef.current.contains(e.target as Node)) {
                setSettingsOpen(false);
            }
        };
        const onKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setSettingsOpen(false);
        };
        document.addEventListener('pointerdown', onPointerDown);
        document.addEventListener('keydown', onKeyDown);
        return () => {
            document.removeEventListener('pointerdown', onPointerDown);
            document.removeEventListener('keydown', onKeyDown);
        };
    }, [settingsOpen]);

    useEffect(() => {
        const move = (e: MouseEvent) => {
            setCursor({ x: e.clientX, y: e.clientY });
            const id = ++trailId.current;
            setTrail((prev) => [...prev.slice(-10), { id, x: e.clientX, y: e.clientY }]);
        };
        window.addEventListener('mousemove', move);
        return () => window.removeEventListener('mousemove', move);
    }, []);

    useEffect(() => {
        const obs = new IntersectionObserver(
            (entries) => {
                entries.forEach((en) => {
                    if (en.isIntersecting) en.target.classList.add('in-view');
                });
            },
            { threshold: 0.12 }
        );
        const id = window.requestAnimationFrame(() => {
            document.querySelectorAll('.landing-root .landing-reveal').forEach((el) => obs.observe(el));
        });
        return () => {
            window.cancelAnimationFrame(id);
            obs.disconnect();
        };
    }, []);

    const resetForgotPassword = useCallback(() => {
        setForgotStep(null);
        setForgotEmail('');
        setForgotCode('');
        setForgotNewPassword('');
        setForgotConfirmPassword('');
        setForgotBusy(false);
        setForgotError(null);
        setForgotMessage(null);
    }, []);

    const openPanel = useCallback((tab: 'register' | 'signin') => {
        setPanelTab(tab);
        setPanelOpen(true);
        setLoginError(null);
        setLoginOtpStep(false);
        setLoginOtp('');
        resetForgotPassword();
        setRegisterError(null);
        setRegisterMessage(null);
    }, [resetForgotPassword]);

    useEffect(() => {
        if (!ready || !isAuthenticated) return;
        const dest = role === 'ADMIN' ? '/market' : '/dashboard';
        navigate(dest, { replace: true });
    }, [ready, isAuthenticated, role, navigate]);

    const submitLogin = async () => {
        setLoginError(null);
        if (!loginUsername.trim() || !loginPassword) {
            setLoginError(t('landing.loginFillRequired', 'Kullanıcı adı ve şifre zorunludur.'));
            return;
        }
        if (loginOtpStep && !loginOtp.trim()) {
            setLoginError(t('landing.loginOtpRequired', 'Doğrulama kodunu girin.'));
            return;
        }
        setLoginBusy(true);
        try {
            const result = await loginWithCredentials({
                usernameOrEmail: loginUsername.trim(),
                password: loginPassword,
                otp: loginOtpStep ? loginOtp.trim() : undefined,
                rememberMe: loginRemember,
            });
            if (result.otpRequired) {
                setLoginOtpStep(true);
                setLoginError(null);
                return;
            }
            setPanelOpen(false);
        } catch (err: unknown) {
            const msg = readFinanceBinaryErrorMessage(err) ?? t('landing.loginFailed', 'Giriş başarısız.');
            setLoginError(msg);
        } finally {
            setLoginBusy(false);
        }
    };

    const requestForgotCode = async () => {
        setForgotError(null);
        setForgotMessage(null);
        if (!forgotEmail.trim()) {
            setForgotError(t('landing.registerEmailRequired', 'Lütfen e-posta girin.'));
            return;
        }
        setForgotBusy(true);
        try {
            const msg = await requestPasswordResetCode(forgotEmail.trim());
            setForgotMessage(msg);
            setForgotStep('code');
        } catch (err: unknown) {
            setForgotError(readApiError(err).message || 'Kod gönderilemedi.');
        } finally {
            setForgotBusy(false);
        }
    };

    const verifyForgotCode = async () => {
        setForgotError(null);
        setForgotMessage(null);
        if (!forgotCode.trim()) {
            setForgotError(t('landing.forgotCodeRequired', 'Doğrulama kodunu girin.'));
            return;
        }
        setForgotBusy(true);
        try {
            const msg = await verifyPasswordResetCode(forgotEmail.trim(), forgotCode.trim());
            setForgotMessage(msg);
            setForgotStep('password');
        } catch (err: unknown) {
            setForgotError(readApiError(err).message || 'Kod doğrulanamadı.');
        } finally {
            setForgotBusy(false);
        }
    };

    const completeForgotReset = async () => {
        setForgotError(null);
        setForgotMessage(null);
        if (!forgotNewPassword || !forgotConfirmPassword) {
            setForgotError(t('landing.forgotFillPasswords', 'Lütfen yeni şifre alanlarını doldurun.'));
            return;
        }
        if (forgotNewPassword !== forgotConfirmPassword) {
            setForgotError(t('landing.passwordMismatch', 'Şifreler eşleşmiyor.'));
            return;
        }
        setForgotBusy(true);
        try {
            const result = await completePasswordReset(
                forgotEmail.trim(),
                forgotNewPassword,
                forgotConfirmPassword
            );
            setForgotMessage(result.message ?? t('landing.forgotDone', 'Şifreniz güncellendi.'));
            setLoginUsername(result.username);
            setLoginPassword(forgotNewPassword);
            setLoginOtp('');
            setLoginOtpStep(false);
            setLoginError(null);
            const loginResult = await loginWithCredentials({
                usernameOrEmail: result.username,
                password: forgotNewPassword,
                rememberMe: false,
            });
            if (loginResult.otpRequired) {
                resetForgotPassword();
                setForgotStep(null);
                setLoginOtpStep(true);
                return;
            }
            resetForgotPassword();
            setPanelOpen(false);
        } catch (err: unknown) {
            setForgotError(readApiError(err).message || readFinanceBinaryErrorMessage(err) || 'Şifre güncellenemedi.');
        } finally {
            setForgotBusy(false);
        }
    };

    const requestRegisterCode = async () => {
        setRegisterError(null);
        setRegisterMessage(null);
        if (!email.trim()) {
            setRegisterError(t('landing.registerEmailRequired', 'Lütfen e-posta girin.'));
            return;
        }
        setRegisterBusy(true);
        try {
            const res = await financeClient.post('/api/public/register/request-code', { email });
            const msg = res.data?.data ?? t('landing.codeSent', 'Doğrulama kodu e-postanıza gönderildi.');
            setRegisterMessage(msg);
        } catch (err: unknown) {
            setRegisterError(readApiError(err).message || 'Kod gönderilemedi.');
        } finally {
            setRegisterBusy(false);
        }
    };

    const completeRegistration = async () => {
        setRegisterError(null);
        setRegisterMessage(null);
        if (!email.trim() || !firstName.trim() || !lastName.trim() || !username.trim() || !password || !code.trim()) {
            setRegisterError(t('landing.registerFillAll', 'Lütfen tüm alanları doldurun.'));
            return;
        }
        if (password !== confirmPassword) {
            setRegisterError(t('landing.passwordMismatch', 'Şifreler eşleşmiyor.'));
            return;
        }
        setRegisterBusy(true);
        try {
            const res = await financeClient.post('/api/public/register/complete', {
                email,
                username,
                firstName: firstName.trim(),
                lastName: lastName.trim(),
                password,
                code,
            });
            const msg = res.data?.data ?? t('landing.registerDone', 'Kayıt tamamlandı.');
            setRegisterMessage(msg);
            setCode('');
        } catch (err: unknown) {
            setRegisterError(readApiError(err).message || 'Kayıt tamamlanamadı.');
        } finally {
            setRegisterBusy(false);
        }
    };

    const particles = useMemo(() => Array.from({ length: 28 }, (_, i) => i), []);

    if (!ready) {
        return <div className="landing-loading-screen">{t('common.loading', 'Yükleniyor...')}</div>;
    }

    return (
        <>
        <div className="landing-root">
            <div className="landing-particles" aria-hidden>
                {particles.map((i) => (
                    <span
                        key={i}
                        className="landing-particle"
                        style={{
                            left: `${(i * 37) % 100}%`,
                            animationDelay: `${(i % 12) * 0.9}s`,
                            animationDuration: `${12 + (i % 8)}s`,
                        }}
                    />
                ))}
            </div>
            {!panelOpen ? (
                <>
                    <div
                        className="landing-cursor-glow"
                        style={{ left: cursor.x, top: cursor.y, opacity: 0.45 }}
                    />
                    {trail.slice(-6).map((pt) => (
                        <span key={pt.id} className="landing-cursor-trail-dot" style={{ left: pt.x, top: pt.y }} />
                    ))}
                </>
            ) : null}

            <div className="landing-inner">
                <header className="landing-header">
                    <a href="/" className="landing-brand" onClick={(e) => e.preventDefault()}>
                        <NrsBrandLockup />
                    </a>
                    <div className="landing-header-top-end">
                        <nav className="landing-nav" aria-label="Main">
                            <a href="#features">{t('landing.navFeatures', 'Özellikler')}</a>
                            <a href="#architecture">{t('landing.navArchitecture', 'Altyapı')}</a>
                        </nav>
                        <div className="landing-settings" ref={settingsRef}>
                            <button
                                type="button"
                                className="landing-settings-trigger"
                                onClick={() => setSettingsOpen((open) => !open)}
                                aria-expanded={settingsOpen}
                                aria-haspopup="dialog"
                                aria-controls="landing-settings-menu"
                                title={t('landing.settings', 'Ayarlar')}
                            >
                                <Settings size={18} aria-hidden />
                                <span className="sr-only">{t('landing.settings', 'Ayarlar')}</span>
                            </button>
                            {settingsOpen ? (
                                <div
                                    id="landing-settings-menu"
                                    className="landing-settings-menu"
                                    role="dialog"
                                    aria-label={t('landing.preferencePanel', 'Tema ve dil ayarları')}
                                >
                                    <LandingPreferenceControls compact />
                                </div>
                            ) : null}
                        </div>
                    </div>
                    <div className="landing-header-cta">
                        <LandingPreferenceControls className="landing-pref-panel--desktop" />
                        <button type="button" className="landing-btn-ghost" onClick={() => openPanel('signin')}>
                            {t('landing.btnLogin', 'Giriş Yap')}
                        </button>
                        <button type="button" className="landing-btn-primary" onClick={() => openPanel('register')}>
                            {t('landing.btnStart', 'Hemen Başla')}
                        </button>
                    </div>
                </header>

                <section className="landing-hero">
                    <div>
                        <h1>{t('landing.heroTitle', 'Yatırımlarınızı Veri ve Analizle Yönetin.')}</h1>
                        <p>
                            {t(
                                'landing.heroBody',
                                'Canlı piyasa takibinden enflasyona göre arındırılmış reel getiri analizine, simülasyonlardan akıllı alarmlara kadar tüm finansal süreçleriniz tek bir portalda.',
                            )}
                        </p>
                        <div className="landing-hero-ctas">
                            <button type="button" className="landing-btn-primary" onClick={() => openPanel('signin')}>
                                {t('landing.ctaExplore', 'Portalı Keşfet')}
                            </button>
                            <button type="button" className="landing-btn-ghost" onClick={() => openPanel('register')}>
                                {t('landing.ctaRegister', 'Güvenli Kayıt Ol')}
                            </button>
                        </div>
                    </div>
                    <LandingHeroCarousel />
                </section>

                <LandingFeaturesFlip />

                <LandingSystemArchitecture />

                <footer className="landing-footer">
                    <span>© {new Date().getFullYear()} NRS Finans Portalı</span>
                </footer>
            </div>
        </div>

            <div
                className={`landing-overlay ${panelOpen ? 'open' : ''}`}
                role="presentation"
                onClick={() => setPanelOpen(false)}
            />
            <aside className={`landing-drawer ${panelOpen ? 'open' : ''}`} aria-hidden={!panelOpen}>
                <div className="landing-drawer-head">
                    <strong style={{ fontSize: '1rem' }}>{t('landing.drawerTitle', 'Hesap')}</strong>
                    <button type="button" className="landing-close" onClick={() => setPanelOpen(false)} aria-label="Kapat">
                        ×
                    </button>
                </div>
                <div className="landing-drawer-tabs">
                    <button
                        type="button"
                        className={panelTab === 'register' ? 'active' : ''}
                        onClick={() => {
                            setPanelTab('register');
                            setLoginError(null);
                        }}
                    >
                        {t('landing.tabRegister', 'Kayıt')}
                    </button>
                    <button
                        type="button"
                        className={panelTab === 'signin' ? 'active' : ''}
                        onClick={() => {
                            setPanelTab('signin');
                            setRegisterError(null);
                            resetForgotPassword();
                        }}
                    >
                        {t('landing.tabSignin', 'Giriş')}
                    </button>
                </div>

                {panelTab === 'signin' ? (
                    forgotStep ? (
                        <div className="landing-form">
                            {forgotStep === 'email' ? (
                                <p style={{ fontSize: '0.85rem', color: 'var(--landing-silver-muted)', margin: 0 }}>
                                    {t(
                                        'landing.forgotEmailIntro',
                                        'Kayıtlı e-posta adresinize doğrulama kodu göndereceğiz.'
                                    )}
                                </p>
                            ) : null}
                            {forgotError ? <div className="landing-alert">{forgotError}</div> : null}
                            {forgotMessage ? <div className="landing-alert ok">{forgotMessage}</div> : null}

                            {forgotStep === 'email' ? (
                                <>
                                    <label htmlFor="forgot-email">{t('landing.email', 'E-posta')}</label>
                                    <input
                                        id="forgot-email"
                                        type="email"
                                        value={forgotEmail}
                                        onChange={(e) => setForgotEmail(e.target.value)}
                                        autoComplete="email"
                                    />
                                    <button
                                        type="button"
                                        className="landing-btn-primary"
                                        disabled={forgotBusy}
                                        onClick={() => void requestForgotCode()}
                                    >
                                        {forgotBusy ? '…' : t('landing.sendCode', 'Doğrulama kodu gönder')}
                                    </button>
                                </>
                            ) : null}

                            {forgotStep === 'code' ? (
                                <>
                                    <label htmlFor="forgot-code">{t('landing.code', 'Doğrulama kodu')}</label>
                                    <input
                                        id="forgot-code"
                                        value={forgotCode}
                                        onChange={(e) => setForgotCode(e.target.value)}
                                        autoComplete="one-time-code"
                                        inputMode="numeric"
                                        maxLength={6}
                                    />
                                    <button
                                        type="button"
                                        className="landing-btn-primary"
                                        disabled={forgotBusy}
                                        onClick={() => void verifyForgotCode()}
                                    >
                                        {forgotBusy ? '…' : t('landing.forgotVerifyCode', 'Doğrula')}
                                    </button>
                                </>
                            ) : null}

                            {forgotStep === 'password' ? (
                                <>
                                    <label htmlFor="forgot-new-pass">{t('landing.forgotNewPassword', 'Yeni şifre')}</label>
                                    <input
                                        id="forgot-new-pass"
                                        type="password"
                                        value={forgotNewPassword}
                                        onChange={(e) => setForgotNewPassword(e.target.value)}
                                        autoComplete="new-password"
                                    />
                                    <label htmlFor="forgot-confirm-pass">
                                        {t('landing.forgotConfirmPassword', 'Yeni şifre (tekrar)')}
                                    </label>
                                    <input
                                        id="forgot-confirm-pass"
                                        type="password"
                                        value={forgotConfirmPassword}
                                        onChange={(e) => setForgotConfirmPassword(e.target.value)}
                                        autoComplete="new-password"
                                    />
                                    <button
                                        type="button"
                                        className="landing-btn-primary"
                                        disabled={forgotBusy}
                                        onClick={() => void completeForgotReset()}
                                    >
                                        {forgotBusy ? '…' : t('landing.forgotSubmit', 'Tamam')}
                                    </button>
                                </>
                            ) : null}

                            <button
                                type="button"
                                className="landing-btn-ghost landing-form-back-btn"
                                onClick={resetForgotPassword}
                            >
                                {t('landing.forgotBackToSignin', 'Girişe dön')}
                            </button>
                        </div>
                    ) : (
                    <div className="landing-form">
                        <p style={{ fontSize: '0.85rem', color: 'var(--landing-silver-muted)', margin: 0 }}>
                            {t(
                                'landing.signinIntro',
                                'İki aşamalı doğrulama yalnızca Hesap Ayarlarından etkinleştirdiyseniz istenir.'
                            )}
                        </p>
                        {loginError ? <div className="landing-alert">{loginError}</div> : null}
                        <label htmlFor="login-user">{t('landing.loginUsername', 'Kullanıcı adı veya e-posta')}</label>
                        <input
                            id="login-user"
                            value={loginUsername}
                            onChange={(e) => setLoginUsername(e.target.value)}
                            autoComplete="username"
                            disabled={loginOtpStep}
                        />
                        <label htmlFor="login-pass">{t('landing.password', 'Şifre')}</label>
                        <input
                            id="login-pass"
                            type="password"
                            value={loginPassword}
                            onChange={(e) => setLoginPassword(e.target.value)}
                            autoComplete="current-password"
                            disabled={loginOtpStep}
                        />
                        {!loginOtpStep ? (
                            <button
                                type="button"
                                className="landing-btn-ghost landing-form-back-btn"
                                style={{ alignSelf: 'flex-start', marginTop: '-0.25rem' }}
                                onClick={() => {
                                    setForgotStep('email');
                                    setForgotEmail(loginUsername.includes('@') ? loginUsername : '');
                                    setForgotError(null);
                                    setForgotMessage(null);
                                    if (!loginUsername.includes('@')) {
                                        setForgotMessage(
                                            t(
                                                'landing.forgotUseRegisteredEmail',
                                                'Kayıtlı e-posta adresinizi girin (kullanıcı adı değil).'
                                            )
                                        );
                                    }
                                }}
                            >
                                {t('landing.forgotPassword', 'Şifremi unuttum')}
                            </button>
                        ) : null}
                        {loginOtpStep ? (
                            <>
                                <p style={{ fontSize: '0.8rem', color: 'var(--landing-silver-muted)', margin: '0 0 0.25rem' }}>
                                    {t(
                                        'landing.loginOtpPasswordHint',
                                        'Şifre, kayıt veya «Şifremi unuttum» ile en son belirlediğiniz şifre olmalıdır.'
                                    )}
                                </p>
                                <label htmlFor="login-otp">{t('landing.loginOtp', 'İki aşamalı doğrulama kodu')}</label>
                                <input
                                    id="login-otp"
                                    value={loginOtp}
                                    onChange={(e) => setLoginOtp(e.target.value)}
                                    autoComplete="one-time-code"
                                    inputMode="numeric"
                                    maxLength={6}
                                />
                                <button
                                    type="button"
                                    className="landing-btn-ghost landing-form-back-btn"
                                    onClick={() => {
                                        setLoginOtpStep(false);
                                        setLoginOtp('');
                                        setLoginError(null);
                                    }}
                                >
                                    {t('landing.loginBackToPassword', 'Şifreyi değiştir / geri dön')}
                                </button>
                            </>
                        ) : null}
                        <label className="landing-checkbox-row">
                            <input
                                type="checkbox"
                                checked={loginRemember}
                                onChange={(e) => setLoginRemember(e.target.checked)}
                            />
                            <span>{t('landing.rememberMe', 'Beni hatırla')}</span>
                        </label>
                        <button
                            type="button"
                            className="landing-btn-primary"
                            style={{ marginTop: '0.5rem' }}
                            disabled={loginBusy}
                            onClick={() => void submitLogin()}
                        >
                            {loginBusy ? '…' : loginOtpStep ? t('landing.loginSubmitOtp', 'Doğrula ve giriş yap') : t('landing.loginSubmit', 'Giriş yap')}
                        </button>
                    </div>
                    )
                ) : (
                    <div className="landing-form">
                        <label htmlFor="reg-email">{t('landing.email', 'E-posta')}</label>
                        <input id="reg-email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
                        <button type="button" className="landing-btn-ghost" disabled={registerBusy} onClick={requestRegisterCode}>
                            {registerBusy ? '…' : t('landing.sendCode', 'Doğrulama kodu gönder')}
                        </button>
                        <div className="landing-form-row">
                            <div>
                                <label htmlFor="reg-first">{t('landing.firstName', 'Ad')}</label>
                                <input
                                    id="reg-first"
                                    value={firstName}
                                    onChange={(e) => setFirstName(e.target.value)}
                                    autoComplete="given-name"
                                />
                            </div>
                            <div>
                                <label htmlFor="reg-last">{t('landing.lastName', 'Soyad')}</label>
                                <input
                                    id="reg-last"
                                    value={lastName}
                                    onChange={(e) => setLastName(e.target.value)}
                                    autoComplete="family-name"
                                />
                            </div>
                        </div>
                        <label htmlFor="reg-user">{t('landing.username', 'Kullanıcı adı')}</label>
                        <input id="reg-user" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
                        <label htmlFor="reg-pass">{t('landing.password', 'Şifre')}</label>
                        <input
                            id="reg-pass"
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            autoComplete="new-password"
                        />
                        <label htmlFor="reg-pass2">{t('landing.passwordAgain', 'Şifre (tekrar)')}</label>
                        <input
                            id="reg-pass2"
                            type="password"
                            value={confirmPassword}
                            onChange={(e) => setConfirmPassword(e.target.value)}
                            autoComplete="new-password"
                        />
                        <label htmlFor="reg-code">{t('landing.code', 'Doğrulama kodu')}</label>
                        <input id="reg-code" value={code} onChange={(e) => setCode(e.target.value)} autoComplete="one-time-code" />
                        <button type="button" className="landing-btn-primary" disabled={registerBusy} onClick={completeRegistration}>
                            {t('landing.registerSubmit', 'Kayıt ol')}
                        </button>
                        {registerMessage ? <div className="landing-alert ok">{registerMessage}</div> : null}
                        {registerError ? <div className="landing-alert">{registerError}</div> : null}
                        <p className="landing-form-note">
                            {t(
                                'landing.registerLegal',
                                'Kayıt ol’a tıklayarak /api/public/register akışı üzerinden e-posta doğrulama kodu almayı kabul etmiş olursunuz.'
                            )}
                        </p>
                    </div>
                )}
            </aside>
        </>
    );
}
