import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useLanguage } from '../i18n/LanguageContext';
import { financeClient } from '../api/client';
import { NrsBrandLockup } from '../components/NrsBrandLockup';
import { LandingHeroCarousel } from '../components/landing/LandingHeroCarousel';
import { LandingFeaturesFlip } from '../components/landing/LandingFeaturesFlip';
import { LandingSystemArchitecture } from '../components/landing/LandingSystemArchitecture';
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

export function LandingPage() {
    const { login, ready } = useAuth();
    const { lang, setLang, t } = useLanguage();
    const [searchParams, setSearchParams] = useSearchParams();
    const [langOpen, setLangOpen] = useState(false);
    const [panelOpen, setPanelOpen] = useState(false);
    const [panelTab, setPanelTab] = useState<'register' | 'signin'>('signin');
    const [loginError, setLoginError] = useState<string | null>(null);
    const [registerBusy, setRegisterBusy] = useState(false);
    const [registerMessage, setRegisterMessage] = useState<string | null>(null);
    const [registerError, setRegisterError] = useState<string | null>(null);
    const [email, setEmail] = useState('');
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [code, setCode] = useState('');
    const [cursor, setCursor] = useState({ x: 0, y: 0 });
    const [trail, setTrail] = useState<{ id: number; x: number; y: number }[]>([]);
    const trailId = useRef(0);

    const suspendedBanner = useMemo(() => searchParams.get('suspended'), [searchParams]);

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

    const openPanel = useCallback((tab: 'register' | 'signin') => {
        setPanelTab(tab);
        setPanelOpen(true);
        setLoginError(null);
        setRegisterError(null);
        setRegisterMessage(null);
    }, []);

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
            const ax = err as { response?: { data?: { errors?: { message?: string }; message?: string } } };
            setRegisterError(ax?.response?.data?.errors?.message ?? ax?.response?.data?.message ?? 'Kod gönderilemedi.');
        } finally {
            setRegisterBusy(false);
        }
    };

    const completeRegistration = async () => {
        setRegisterError(null);
        setRegisterMessage(null);
        if (!email.trim() || !username.trim() || !password || !code.trim()) {
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
                password,
                code,
            });
            const msg = res.data?.data ?? t('landing.registerDone', 'Kayıt tamamlandı.');
            setRegisterMessage(msg);
            setCode('');
        } catch (err: unknown) {
            const ax = err as { response?: { data?: { errors?: { message?: string }; message?: string } } };
            setRegisterError(ax?.response?.data?.errors?.message ?? ax?.response?.data?.message ?? 'Kayıt tamamlanamadı.');
        } finally {
            setRegisterBusy(false);
        }
    };

    const particles = useMemo(() => Array.from({ length: 28 }, (_, i) => i), []);

    if (!ready) {
        return <div className="landing-loading-screen">{t('common.loading', 'Yükleniyor...')}</div>;
    }

    return (
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
            <div
                className="landing-cursor-glow"
                style={{ left: cursor.x, top: cursor.y, opacity: panelOpen ? 0.15 : 0.45 }}
            />
            {trail.slice(-6).map((pt) => (
                <span key={pt.id} className="landing-cursor-trail-dot" style={{ left: pt.x, top: pt.y }} />
            ))}

            <div className="landing-inner">
                <header className="landing-header">
                    <a href="/" className="landing-brand" onClick={(e) => e.preventDefault()}>
                        <NrsBrandLockup />
                    </a>
                    <nav className="landing-nav" aria-label="Main">
                        <a href="#features">{t('landing.navFeatures', 'Özellikler')}</a>
                        <a href="#architecture">{t('landing.navArchitecture', 'Altyapı')}</a>
                    </nav>
                    <div className="landing-header-actions">
                        <div className="landing-lang-wrap">
                            <button
                                type="button"
                                className="landing-lang-btn"
                                onClick={() => setLangOpen((v) => !v)}
                                aria-expanded={langOpen}
                            >
                                {lang === 'tr' ? '🇹🇷 TR' : '🇬🇧 EN'} ▾
                            </button>
                            {langOpen ? (
                                <div className="landing-lang-menu" role="menu">
                                    <button type="button" role="menuitem" onClick={() => { setLang('tr'); setLangOpen(false); }}>
                                        🇹🇷 Türkçe
                                    </button>
                                    <button type="button" role="menuitem" onClick={() => { setLang('en'); setLangOpen(false); }}>
                                        🇬🇧 English
                                    </button>
                                </div>
                            ) : null}
                        </div>
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
                        }}
                    >
                        {t('landing.tabSignin', 'Giriş')}
                    </button>
                </div>

                {panelTab === 'signin' ? (
                    <div className="landing-form">
                        <p style={{ fontSize: '0.85rem', color: '#94a3b8', margin: 0 }}>
                            {t(
                                'landing.signinIntro',
                                'Kurumsal giriş Keycloak üzerinden yapılır. Rolünüze göre OTP istenebilir.'
                            )}
                        </p>
                        {loginError ? <div className="landing-alert">{loginError}</div> : null}
                        <p style={{ fontSize: '0.8rem', color: '#94a3b8' }}>
                            {t('landing.signinNote', '“Beni hatırla” ve şifre politikaları Keycloak oturum ekranında yönetilir.')}
                        </p>
                        <button type="button" className="landing-btn-primary" style={{ marginTop: '0.5rem' }} onClick={() => login()}>
                            {t('landing.signinKeycloak', 'Keycloak ile giriş')}
                        </button>
                    </div>
                ) : (
                    <div className="landing-form">
                        <label htmlFor="reg-email">{t('landing.email', 'E-posta')}</label>
                        <input id="reg-email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
                        <button type="button" className="landing-btn-ghost" disabled={registerBusy} onClick={requestRegisterCode}>
                            {registerBusy ? '…' : t('landing.sendCode', 'Doğrulama kodu gönder')}
                        </button>
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
        </div>
    );
}
