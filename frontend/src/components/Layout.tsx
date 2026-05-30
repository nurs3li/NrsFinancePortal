import { Outlet, Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import { useState, useEffect, useCallback, useMemo, useRef, type CSSProperties, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationClient } from '../api/client';
import { Bell, ChevronDown, LogOut, Moon, MoreVertical, Settings, Sun } from 'lucide-react';
import { NrsBrandLockup, NrsBrandMark } from './NrsBrandLockup';
import { useHeaderInteractions } from './header';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { useDocumentVisibility } from '../hooks/useDocumentVisibility';
import { notificationKeys } from '../queries/notificationKeys';
import { HeaderNotificationsDropdown } from './inbox/HeaderNotificationsDropdown';
import { buildLayoutNavItems, resolveActiveNavItem } from './layoutNavConfig';
import './Layout.css';

function HeaderThemeLangSwitch({
    theme,
    toggleTheme,
    lang,
    onLangChange,
    t,
    compact,
    userMenu,
}: {
    theme: 'light' | 'dark';
    toggleTheme: () => void;
    lang: string;
    onLangChange: (value: string) => void;
    t: (key: string, fallback?: string) => string;
    compact?: boolean;
    userMenu?: boolean;
}) {
    const iconSize = userMenu ? 13 : compact ? 14 : 16;
    const switchClass = [
        'header-quick-switch',
        compact ? 'header-quick-switch--compact' : '',
        userMenu ? 'header-quick-switch--user-menu' : '',
    ]
        .filter(Boolean)
        .join(' ');

    return (
        <div className={switchClass}>
            <div className="header-quick-switch__theme">
                <button
                    type="button"
                    className={`header-quick-switch__icon-btn ${theme === 'light' ? 'is-active' : ''}`}
                    onClick={() => {
                        if (theme !== 'light') toggleTheme();
                    }}
                    title={t('theme.light', 'Açık Tema')}
                >
                    <Sun size={iconSize} />
                </button>
                <button
                    type="button"
                    className={`header-quick-switch__icon-btn ${theme === 'dark' ? 'is-active' : ''}`}
                    onClick={() => {
                        if (theme !== 'dark') toggleTheme();
                    }}
                    title={t('theme.dark', 'Koyu Tema')}
                >
                    <Moon size={iconSize} />
                </button>
            </div>
            <div className="header-quick-switch__lang">
                <button
                    type="button"
                    className={`header-quick-switch__lang-btn ${lang === 'tr' ? 'is-active' : ''}`}
                    onClick={() => onLangChange('tr')}
                >
                    {t('lang.turkish', 'Türkçe')}
                </button>
                <button
                    type="button"
                    className={`header-quick-switch__lang-btn ${lang === 'en' ? 'is-active' : ''}`}
                    onClick={() => onLangChange('en')}
                >
                    {t('lang.english', 'English')}
                </button>
                {!compact ? (
                    <>
                        <span className="header-quick-switch__divider" />
                        <ChevronDown size={18} className="header-quick-switch__caret" />
                    </>
                ) : null}
            </div>
        </div>
    );
}

export function Layout() {
    const { isAuthenticated, logout, role, user } = useAuth();
    const { theme, toggleTheme, tokens } = useTheme();
    const location = useLocation();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const tabVisible = useDocumentVisibility();
    const [isNotificationOpen, setIsNotificationOpen] = useState(false);
    const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
    const { lang, setLang, t } = useLanguage();
    const notificationRef = useRef<HTMLDivElement>(null);
    const notificationMobileRef = useRef<HTMLDivElement>(null);
    const userMenuRef = useRef<HTMLDivElement>(null);
    const navRef = useRef<HTMLDivElement>(null);
    const mobileMenuRef = useRef<HTMLDivElement>(null);

    const isAdmin = role === 'ADMIN';

    const navItems = useMemo(() => buildLayoutNavItems({ isAdmin, t }), [isAdmin, t]);
    const visibleNavItems = useMemo(() => navItems.filter((item) => item.show), [navItems]);
    const activeNavItem = useMemo(
        () => resolveActiveNavItem(location.pathname, visibleNavItems),
        [location.pathname, visibleNavItems],
    );
    const activeNavKey = activeNavItem?.key ?? '';

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

    useRefetchOnFocus(
        useCallback(() => {
            if (!isAuthenticated) return;
            void queryClient.invalidateQueries({ queryKey: notificationKeys.all });
        }, [isAuthenticated, queryClient]),
        isAuthenticated,
    );

    useEffect(() => {
        const close = (e: MouseEvent) => {
            const target = e.target as Node;
            if (notificationRef.current?.contains(target) || notificationMobileRef.current?.contains(target)) return;
            setIsNotificationOpen(false);
        };
        document.addEventListener('click', close);
        return () => document.removeEventListener('click', close);
    }, []);

    useEffect(() => {
        setIsMobileMenuOpen(false);
        setIsNotificationOpen(false);
    }, [location.pathname]);

    useEffect(() => {
        if (!isMobileMenuOpen) return;
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setIsMobileMenuOpen(false);
        };
        const onClick = (e: MouseEvent) => {
            const target = e.target as Node;
            if (mobileMenuRef.current?.contains(target)) return;
            const btn = document.querySelector('.app-header-mobile__menu-btn');
            if (btn?.contains(target)) return;
            setIsMobileMenuOpen(false);
        };
        document.addEventListener('keydown', onKey);
        document.addEventListener('mousedown', onClick);
        document.body.style.overflow = 'hidden';
        return () => {
            document.removeEventListener('keydown', onKey);
            document.removeEventListener('mousedown', onClick);
            document.body.style.overflow = '';
        };
    }, [isMobileMenuOpen]);

    const markNotificationRead = (id: number) => {
        notificationClient.patch(`/api/notifications/${id}/read`).then(() => {
            void queryClient.invalidateQueries({ queryKey: notificationKeys.all });
        });
    };

    const handleLogout = () => {
        setIsMobileMenuOpen(false);
        logout();
    };

    const handleNewsLangChange = (value: string) => setLang(value === 'en' ? 'en' : 'tr');

    const toggleNotification = (e: React.MouseEvent) => {
        e.stopPropagation();
        setIsNotificationOpen((open) => !open);
    };

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

    const shellStyle = {
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
    } as CSSProperties;

    const mobilePageChip = activeNavItem?.mobileLabel ?? t('nav.dashboard', 'Dashboard');

    const mobileMenuPanel: ReactNode = isMobileMenuOpen ? (
        <>
            <button
                type="button"
                className="app-mobile-menu-backdrop"
                aria-label="Menüyü kapat"
                onClick={() => setIsMobileMenuOpen(false)}
            />
            <nav ref={mobileMenuRef} className="app-mobile-menu" aria-label={t('nav.menu', 'Ana menü')}>
                <ul className="app-mobile-menu__list">
                    {visibleNavItems.map((item) => {
                        const Icon = item.icon;
                        return (
                            <li key={item.key}>
                                <NavLink
                                    to={item.to}
                                    className={() =>
                                        `app-mobile-menu__link${activeNavKey === item.key ? ' is-active' : ''}`
                                    }
                                    onClick={() => setIsMobileMenuOpen(false)}
                                >
                                    <Icon size={18} aria-hidden />
                                    <span>{item.label}</span>
                                </NavLink>
                            </li>
                        );
                    })}
                    <li>
                        <NavLink
                            to="/notifications"
                            className={() =>
                                `app-mobile-menu__link${location.pathname.startsWith('/notifications') ? ' is-active' : ''}`
                            }
                            onClick={() => setIsMobileMenuOpen(false)}
                        >
                            <Bell size={18} aria-hidden />
                            <span>{t('nav.notifications', 'Bildirimler')}</span>
                        </NavLink>
                    </li>
                </ul>
                <div className="app-mobile-menu__footer">
                    <div className="app-mobile-menu__user">
                        <span className="app-mobile-menu__user-name">
                            {user?.username ?? user?.email ?? '—'}
                        </span>
                        <span className="app-mobile-menu__user-role">{role ?? user?.role ?? 'USER'}</span>
                    </div>
                    <HeaderThemeLangSwitch
                        theme={theme}
                        toggleTheme={toggleTheme}
                        lang={lang}
                        onLangChange={handleNewsLangChange}
                        t={t}
                        compact
                    />
                    <button type="button" className="app-mobile-menu__logout" onClick={handleLogout}>
                        <LogOut size={16} aria-hidden />
                        {t('auth.logout', 'Çıkış yap')}
                    </button>
                </div>
            </nav>
        </>
    ) : null;

    return (
        <div className="app-shell" style={shellStyle}>
            {/* Desktop header (lg+) */}
            <header className={`app-header app-header--desktop ${isScrolled ? 'is-scrolled' : ''}`}>
                <div className="app-header__left">
                    <Link to="/" className="app-header__logo" aria-label="NRS Finance Portal">
                        <NrsBrandLockup />
                    </Link>
                </div>

                <div className="app-header__center" ref={navRef} onMouseLeave={() => setHoveredNavKey(null)}>
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
                            className={() => `app-header__nav-link ${activeNavKey === item.key ? 'is-active' : ''}`}
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
                    <HeaderNotificationsDropdown
                        unreadCount={unreadCount}
                        isOpen={isNotificationOpen}
                        onToggle={toggleNotification}
                        onMarkRead={markNotificationRead}
                        wrapRef={notificationRef}
                    />

                    <div className="header-control-wrap" ref={userMenuRef}>
                        <button
                            type="button"
                            className="header-user-trigger"
                            onClick={() => setIsUserMenuOpen((open) => !open)}
                            onMouseEnter={() => setIsUserMenuOpen(true)}
                        >
                            <span>
                                {(user?.username ?? user?.email ?? '—')} — {role ?? user?.role ?? 'USER'}
                            </span>
                            <ChevronDown size={14} className={isUserMenuOpen ? 'rotated' : ''} />
                        </button>
                        {isUserMenuOpen && (
                            <div className="header-dropdown header-dropdown--user">
                                <button
                                    type="button"
                                    className="header-user-menu__settings"
                                    onClick={() => {
                                        setIsUserMenuOpen(false);
                                        navigate('/settings');
                                    }}
                                >
                                    <Settings size={14} aria-hidden />
                                    <span>{t('nav.settings', 'Ayarlar')}</span>
                                </button>
                                <HeaderThemeLangSwitch
                                    theme={theme}
                                    toggleTheme={toggleTheme}
                                    lang={lang}
                                    onLangChange={handleNewsLangChange}
                                    t={t}
                                    compact
                                    userMenu
                                />
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

            {/* Mobile header (< lg) */}
            <header className={`app-header app-header--mobile ${isScrolled ? 'is-scrolled' : ''}`}>
                <div className="app-header-mobile__left">
                    <Link to="/" className="app-header__logo app-header__logo--compact" aria-label="NRS Finance Portal">
                        <NrsBrandMark />
                    </Link>
                </div>
                <div className="app-header-mobile__center">
                    <span className="app-header-mobile__page-chip" title={activeNavItem?.label}>
                        {mobilePageChip}
                    </span>
                </div>
                <div className="app-header-mobile__right">
                    <HeaderNotificationsDropdown
                        unreadCount={unreadCount}
                        isOpen={isNotificationOpen}
                        onToggle={toggleNotification}
                        onMarkRead={markNotificationRead}
                        wrapRef={notificationMobileRef}
                    />
                    <button
                        type="button"
                        className="header-icon-btn app-header-mobile__menu-btn"
                        aria-label={t('nav.openMenu', 'Menüyü aç')}
                        aria-expanded={isMobileMenuOpen}
                        onClick={(e) => {
                            e.stopPropagation();
                            setIsMobileMenuOpen((open) => !open);
                            setIsNotificationOpen(false);
                        }}
                    >
                        <MoreVertical size={18} />
                    </button>
                </div>
            </header>

            {mobileMenuPanel}

            <main className="app-main">
                {/* pathname key: transition/suspense sirasinda eski route'un DOM'da kalmasini engeller */}
                <Outlet key={location.pathname} />
            </main>
        </div>
    );
}
