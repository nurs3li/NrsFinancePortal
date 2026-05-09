import { Outlet, Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import { useState, useEffect, useCallback, useMemo, useRef, type CSSProperties } from 'react';
import { notificationClient } from '../api/client';
import { Bell, ChevronDown, LogOut, Moon, Sun } from 'lucide-react';
import { NrsBrandLockup } from './NrsBrandLockup';
import { useHeaderInteractions } from './header';
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
    const [unreadCount, setUnreadCount] = useState(0);
    const [dropdownItems, setDropdownItems] = useState<{ id: number; title: string; readAt: string | null; type: string }[]>([]);
    const [isNotificationOpen, setIsNotificationOpen] = useState(false);
    const { lang, setLang, t } = useLanguage();
    const notificationRef = useRef<HTMLDivElement>(null);
    const userMenuRef = useRef<HTMLDivElement>(null);
    const navRef = useRef<HTMLDivElement>(null);

    const fetchUnreadCount = useCallback(() => {
        if (!isAuthenticated) return;
        notificationClient.get<{ count: number }>('/api/notifications/me/unread-count')
            .then((res) => setUnreadCount(res.data?.count ?? 0))
            .catch(() => setUnreadCount(0));
    }, [isAuthenticated]);

    const fetchDropdownNotifications = useCallback(() => {
        if (!isAuthenticated) return;
        notificationClient.get<{ content: { id: number; title: string; readAt: string | null; type: string }[] }>('/api/notifications/me', {
            params: {
                size: 10,
                unreadOnly: true,
                sort: ['lastOccurredAt,desc', 'createdAt,desc'],
            },
        })
            .then((res) => setDropdownItems(res.data?.content ?? []))
            .catch(() => setDropdownItems([]));
    }, [isAuthenticated]);

    useEffect(() => {
        fetchUnreadCount();
        const t = setInterval(fetchUnreadCount, 60_000);
        return () => clearInterval(t);
    }, [fetchUnreadCount]);

    useEffect(() => {
        if (isNotificationOpen && isAuthenticated) {
            fetchDropdownNotifications();
            fetchUnreadCount();
        }
    }, [isNotificationOpen, isAuthenticated, fetchDropdownNotifications, fetchUnreadCount]);

    useEffect(() => {
        const close = (e: MouseEvent) => {
            if (notificationRef.current && !notificationRef.current.contains(e.target as Node)) setIsNotificationOpen(false);
        };
        document.addEventListener('click', close);
        return () => document.removeEventListener('click', close);
    }, []);

    const markNotificationRead = (id: number) => {
        notificationClient.patch(`/api/notifications/${id}/read`).then(() => {
            fetchUnreadCount();
            fetchDropdownNotifications();
        });
    };
    const handleLogout = () => {
        logout();
    };
    const handleNewsLangChange = (value: string) => setLang(value === 'en' ? 'en' : 'tr');

    const isFm = role === 'FINANCE_MANAGER';
    const isAdmin = role === 'ADMIN';

    const navItems = useMemo<NavItem[]>(() => {
        if (isAdmin) {
            return [
                { key: 'market', to: '/market', label: t('nav.market', 'Piyasa'), show: true },
                { key: 'news', to: '/news', label: t('nav.news', 'Haberler'), show: true },
                { key: 'admin', to: '/admin', label: t('nav.admin', 'Yönetim Paneli'), show: true },
                { key: 'admin-tasks', to: '/admin/tasks', label: t('nav.adminTasks', 'Admin Görevler'), show: true },
                { key: 'admin-users', to: '/admin/users', label: t('nav.userManagement', 'Kullanıcı Yönetimi'), show: true },
                { key: 'admin-audit', to: '/admin/audit', label: t('nav.auditLogs', 'Audit Logs'), show: true },
            ];
        }
        if (isFm) {
            return [
                { key: 'market', to: '/market', label: t('nav.market', 'Piyasa'), show: true },
                { key: 'news', to: '/news', label: t('nav.news', 'Haberler'), show: true },
                { key: 'fm-tasks', to: '/fm/tasks', label: t('nav.tasks', 'Görevler'), show: true },
                { key: 'fm-funds', to: '/fm/fund-requests', label: t('nav.fundRequests', 'Para Talepleri'), show: true },
                { key: 'fm-risk', to: '/fm/risk', label: t('nav.riskMonitor', 'Risk Monitor'), show: true },
                { key: 'fm-suspicious', to: '/operasyon/suspicious', label: t('nav.suspiciousEvents', 'Şüpheli Olaylar'), show: true },
            ];
        }
        return [
            { key: 'dashboard', to: '/dashboard', label: t('nav.dashboard', 'Dashboard'), show: true },
            { key: 'market', to: '/market', label: t('nav.market', 'Piyasa'), show: true },
            { key: 'news', to: '/news', label: t('nav.news', 'Haberler'), show: true },
            { key: 'portfolio', to: '/portfolio', label: t('nav.portfolio', 'Portföy Analizi'), show: true },
            { key: 'trade', to: '/trade', label: t('nav.trade', 'Alım Satım'), show: true },
            { key: 'transactions', to: '/transactions', label: t('nav.transactions', 'İşlem Geçmişi'), show: true },
            { key: 'wallet', to: '/wallet', label: t('nav.wallet', 'Cüzdan'), show: true },
            { key: 'simulation', to: '/simulation', label: t('nav.simulation', 'Simülasyon'), show: true },
        ];
    }, [isAdmin, isFm, t]);
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
                            onClick={(event) => focusNavItem(event.currentTarget)}
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
                            {isFm && (
                                <span
                                    title="Finance Manager"
                                    style={{
                                        marginRight: 8,
                                        padding: '3px 9px',
                                        borderRadius: 999,
                                        background: 'linear-gradient(135deg, rgba(16,185,129,0.25), rgba(52,211,153,0.12))',
                                        border: '1px solid rgba(52,211,153,0.45)',
                                        color: '#a7f3d0',
                                        fontSize: '0.68rem',
                                        fontWeight: 700,
                                        letterSpacing: '0.04em',
                                    }}
                                >
                                    FM
                                </span>
                            )}
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
                <Outlet />
            </main>
        </div>
    );
}