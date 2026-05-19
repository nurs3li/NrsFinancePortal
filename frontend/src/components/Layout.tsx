import { Outlet, Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import { useState, useEffect, useCallback, useMemo, useRef, type CSSProperties } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationClient } from '../api/client';
import { Bell, ChevronDown, LogOut, Moon, Sun } from 'lucide-react';
import { NrsBrandLockup } from './NrsBrandLockup';
import { useHeaderInteractions } from './header';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { useDocumentVisibility } from '../hooks/useDocumentVisibility';
import { notificationKeys } from '../queries/notificationKeys';
import './Layout.css';

type NavItem = {
    key: string;
    to: string;
    label: string;
    show: boolean;
};

export function Layout() {
    const { isAuthenticated, logout, role, user } = useAuth();
    const { theme, toggleTheme, tokens } = useTheme();
    const location = useLocation();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const tabVisible = useDocumentVisibility();
    const [isNotificationOpen, setIsNotificationOpen] = useState(false);
    const { lang, setLang, t } = useLanguage();
    const notificationRef = useRef<HTMLDivElement>(null);
    const userMenuRef = useRef<HTMLDivElement>(null);
    const navRef = useRef<HTMLDivElement>(null);

    const { data: unreadCount = 0 } = useQuery({
        queryKey: notificationKeys.unreadCount(),
        queryFn: async () => {
            const res = await notificationClient.get<{ count: number }>('/api/notifications/me/unread-count');
            return res.data?.count ?? 0;
        },
        enabled: isAuthenticated,
        staleTime: 45_000,
        refetchInterval: tabVisible && isAuthenticated ? 60_000 : false,
    });

    const { data: dropdownItems = [] } = useQuery({
        queryKey: notificationKeys.headerDropdown(),
        queryFn: async () => {
            const res = await notificationClient.get<{
                content: { id: number; title: string; readAt: string | null; type: string }[];
            }>('/api/notifications/me', {
                params: {
                    size: 10,
                    unreadOnly: true,
                    sort: ['lastOccurredAt,desc', 'createdAt,desc'],
                },
            });
            return res.data?.content ?? [];
        },
        enabled: isAuthenticated && isNotificationOpen,
        staleTime: 20_000,
    });

    useRefetchOnFocus(
        useCallback(() => {
            if (!isAuthenticated) return;
            void queryClient.invalidateQueries({ queryKey: notificationKeys.all });
        }, [isAuthenticated, queryClient]),
        isAuthenticated
    );

    useEffect(() => {
        const close = (e: MouseEvent) => {
            if (notificationRef.current && !notificationRef.current.contains(e.target as Node)) setIsNotificationOpen(false);
        };
        document.addEventListener('click', close);
        return () => document.removeEventListener('click', close);
    }, []);

    const markNotificationRead = (id: number) => {
        notificationClient.patch(`/api/notifications/${id}/read`).then(() => {
            void queryClient.invalidateQueries({ queryKey: notificationKeys.all });
        });
    };
    const handleLogout = () => {
        logout();
    };
    const handleNewsLangChange = (value: string) => setLang(value === 'en' ? 'en' : 'tr');

    const isAdmin = role === 'ADMIN';

    const navItems = useMemo<NavItem[]>(() => {
        if (isAdmin) {
            return [
                { key: 'market', to: '/market', label: t('nav.market', 'Piyasa'), show: true },
                { key: 'marketMacro', to: '/market/macro', label: t('nav.marketMacro', 'Faiz & Enflasyon Paneli'), show: true },
                { key: 'news', to: '/news', label: t('nav.news', 'Haberler'), show: true },
                { key: 'admin-users', to: '/admin/users', label: t('nav.userManagement', 'Kullanıcı Yönetimi'), show: true },
                { key: 'admin-audit', to: '/admin/audit', label: t('nav.auditLogs', 'Audit Logs'), show: true },
            ];
        }
        return [
            { key: 'dashboard', to: '/dashboard', label: t('nav.dashboard', 'Dashboard'), show: true },
            { key: 'market', to: '/market', label: t('nav.market', 'Piyasa'), show: true },
            { key: 'news', to: '/news', label: t('nav.news', 'Haberler'), show: true },
            { key: 'portfolio', to: '/portfolio', label: t('nav.portfolio', 'Portföy Analizi'), show: true },
            { key: 'viop-bond', to: '/viop-bond-analysis', label: t('nav.viopBond', 'VİOP & Tahvil'), show: true },
            { key: 'transactions', to: '/transactions', label: t('nav.transactions', 'İşlem Geçmişi'), show: true },
            { key: 'simulation', to: '/simulation', label: t('nav.simulation', 'Simülasyon'), show: true },
            { key: 'marketMacro', to: '/market/macro', label: t('nav.marketMacro', 'Faiz & Enflasyon Paneli'), show: true },
        ];
    }, [isAdmin, t]);
    const visibleNavItems = navItems.filter((item) => item.show);
    const activeNavKey = useMemo(() => {
        let best: NavItem | undefined;
        for (const item of visibleNavItems) {
            if (location.pathname.startsWith(item.to)) {
                if (!best || item.to.length > best.to.length) best = item;
            }
        }
        return best?.key ?? '';
    }, [location.pathname, visibleNavItems]);

    const {
        setHoveredNavKey,
        highlightStyle,
        isScrolled,
        isUserMenuOpen,
        setIsUserMenuOpen,
        focusNavItem,
    } = useHeaderInteractions({
        navRef,
        userMenuRef,
        activeNavKey,
    });

    return (
        <div
            className="app-shell"
            style={
                {
                    '--header-bg': tokens.headerBg,
                    '--header-text': tokens.headerText,
                    '--header-card': tokens.bgCard,
                    '--header-card-text': tokens.text,
                    '--header-card-muted': tokens.textMuted,
                    '--header-border': tokens.border,
                    '--header-accent': tokens.accent,
                    '--header-muted': tokens.textMuted,
                    '--header-danger': tokens.error,
                    '--page-bg': tokens.bg,
                } as CSSProperties
            }
        >
            <header className={`app-header ${isScrolled ? 'is-scrolled' : ''}`}>
                <div className="app-header__left">
                    <Link to="/" className="app-header__logo" aria-label="NRS Finance Portal">
                        <NrsBrandLockup />
                    </Link>
                </div>

                <div
                    className="app-header__center"
                    ref={navRef}
                    onMouseLeave={() => setHoveredNavKey(null)}
                >
                    <span
                        className="app-header__nav-highlight"
                        style={{
                            width: highlightStyle.width,
                            transform: `translateX(${highlightStyle.left}px)`,
                            opacity: highlightStyle.visible ? 1 : 0,
                        }}
                    />
                    {visibleNavItems.map((item) => (
                        <NavLink
                            key={item.key}
                            data-nav-key={item.key}
                            to={item.to}
                            // NavLink'in built-in onClick davranisi: preventDefault + React Router'in
                              // history API'siyle navigate eder. Onceden ozel onClick handler vardi ama
                              // React event delegation ile celisti ve defaultPrevented hep false kaldi,
                              // sonucta browser native anchor click URL'i degistiriyordu ama React Router
                              // state'i guncelle*mi*yordu (URL=/dashboard, DOM=terminal-page bug'i).
                              // Default davranisa guveniyoruz; modifier (ctrl/meta/shift/alt) tiklamalari
                              // NavLink kendi icinde "yeni sekmede ac" olarak dogru ele aliyor.
                            className={() =>
                                `app-header__nav-link ${activeNavKey === item.key ? 'is-active' : ''}`
                            }
                            onMouseEnter={(event) => {
                                setHoveredNavKey(item.key);
                                focusNavItem(event.currentTarget);
                            }}
                            onFocus={(event) => {
                                setHoveredNavKey(item.key);
                                focusNavItem(event.currentTarget);
                            }}
                        >
                            {item.label}
                        </NavLink>
                    ))}
                </div>

                <div className="app-header__right">
                    <div ref={notificationRef} className="header-control-wrap">
                        <button
                            type="button"
                            className="header-icon-btn"
                            aria-label="Bildirimleri aç"
                            onClick={(e) => {
                                e.stopPropagation();
                                setIsNotificationOpen((open) => !open);
                            }}
                        >
                            <Bell size={17} />
                            {unreadCount > 0 && (
                                <span className="header-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>
                            )}
                        </button>
                        {isNotificationOpen && (
                            <div className="header-dropdown header-dropdown--notifications">
                                <div className="header-dropdown__title">Bildirimler</div>
                                {dropdownItems.length === 0 ? (
                                    <div className="header-dropdown__empty">Bildirim yok</div>
                                ) : (
                                    dropdownItems.map((n) => (
                                        <div
                                            key={n.id}
                                            className="header-dropdown__item"
                                            onClick={() => {
                                                markNotificationRead(n.id);
                                                setIsNotificationOpen(false);
                                                navigate('/notifications');
                                            }}
                                        >
                                            <div className="header-dropdown__item-title">{n.title}</div>
                                            <div className="header-dropdown__item-meta">{n.type}</div>
                                        </div>
                                    ))
                                )}
                                <Link
                                    to="/notifications"
                                    className="header-dropdown__footer"
                                    onClick={() => setIsNotificationOpen(false)}
                                >
                                    Tümünü gör
                                </Link>
                            </div>
                        )}
                    </div>

                    <div className="header-control-wrap" ref={userMenuRef}>
                        <button
                            type="button"
                            className="header-user-trigger"
                            onClick={() => setIsUserMenuOpen((open) => !open)}
                            onMouseEnter={() => setIsUserMenuOpen(true)}
                        >
                            <span>{(user?.username ?? user?.email ?? '—')} — {role ?? user?.role ?? 'USER'}</span>
                            <ChevronDown size={14} className={isUserMenuOpen ? 'rotated' : ''} />
                        </button>
                        {isUserMenuOpen && (
                            <div className="header-dropdown header-dropdown--user">
                                <div className="header-quick-switch">
                                    <div className="header-quick-switch__theme">
                                        <button
                                            type="button"
                                            className={`header-quick-switch__icon-btn ${theme === 'light' ? 'is-active' : ''}`}
                                            onClick={() => {
                                                if (theme !== 'light') toggleTheme();
                                            }}
                                            title={t('theme.light', 'Açık Tema')}
                                        >
                                            <Sun size={16} />
                                        </button>
                                        <button
                                            type="button"
                                            className={`header-quick-switch__icon-btn ${theme === 'dark' ? 'is-active' : ''}`}
                                            onClick={() => {
                                                if (theme !== 'dark') toggleTheme();
                                            }}
                                            title={t('theme.dark', 'Koyu Tema')}
                                        >
                                            <Moon size={16} />
                                        </button>
                                    </div>
                                    <div className="header-quick-switch__lang">
                                        <button
                                            type="button"
                                            className={`header-quick-switch__lang-btn ${lang === 'tr' ? 'is-active' : ''}`}
                                            onClick={() => handleNewsLangChange('tr')}
                                        >
                                            {t('lang.turkish', 'Türkçe')}
                                        </button>
                                        <button
                                            type="button"
                                            className={`header-quick-switch__lang-btn ${lang === 'en' ? 'is-active' : ''}`}
                                            onClick={() => handleNewsLangChange('en')}
                                        >
                                            {t('lang.english', 'English')}
                                        </button>
                                        <span className="header-quick-switch__divider" />
                                        <ChevronDown size={18} className="header-quick-switch__caret" />
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>

                    {isAuthenticated && (
                        <button
                            type="button"
                            className="header-icon-btn"
                            onClick={handleLogout}
                            aria-label="Çıkış yap"
                            title="Çıkış yap"
                        >
                            <LogOut size={17} />
                        </button>
                    )}
                </div>
            </header>

            <main className="app-main">
                {/*
                 * Eager import'a donduk; lazy + Suspense ile pending stuck olusuyordu
                 * (VIOP'tan cikista URL degisse de Outlet eski sayfada kaliyordu).
                 * React Router her route icin ayri component mount ediyor, dolayisiyla
                 * boundary'siz Outlet yeterli ve navigation deterministik.
                 */}
                <Outlet />
            </main>
        </div>
    );
}