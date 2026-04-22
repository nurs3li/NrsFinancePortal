import { Outlet, Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';
import { useState, useEffect, useCallback, useMemo, useRef, type CSSProperties } from 'react';
import { notificationClient } from '../api/client';
import { Bell, ChevronDown, Landmark, LogOut, Moon, Sun } from 'lucide-react';
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
        notificationClient.get<{ content: { id: number; title: string; readAt: string | null; type: string }[] }>('/api/notifications/me', { params: { size: 10 } })
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
        navigate('/');
    };

    const isFm = role === 'FINANCE_MANAGER';
    const isAdmin = role === 'ADMIN';
    /** Portföy ve İşlem Geçmişi sadece müşteri (USER); personel (Admin/FM) menüde görmesin */
    const showCustomerPortfolio = !isFm && !isAdmin;
    /** Dashboard sadece müşteri (USER); Admin/FM görmez */
    const showDashboard = !isFm && !isAdmin;
    /** Görevler (FM) sadece FM görsün; Admin sadece Admin Görevler görsün */
    const showFmTasks = isFm;
    const navItems = useMemo<NavItem[]>(
        () => [
            { key: 'dashboard', to: '/dashboard', label: 'Dashboard', show: showDashboard },
            { key: 'market', to: '/market', label: 'Piyasa', show: true },
            { key: 'wallet', to: '/wallet', label: 'Cüzdan', show: showCustomerPortfolio },
            { key: 'portfolio', to: '/portfolio', label: 'Portföy', show: showCustomerPortfolio },
            { key: 'simulation', to: '/simulation', label: 'Simülasyon', show: showCustomerPortfolio },
            { key: 'trade', to: '/trade', label: 'Trade', show: showCustomerPortfolio },
            { key: 'transactions', to: '/transactions', label: 'İşlem Geçmişi', show: showCustomerPortfolio },
            { key: 'news', to: '/news', label: 'Haberler', show: true },
            { key: 'fm-tasks', to: '/fm/tasks', label: 'Görevler', show: showFmTasks },
            { key: 'fm-funds', to: '/fm/fund-requests', label: 'Para Talepleri', show: showFmTasks },
            { key: 'fm-risk', to: '/fm/risk', label: 'Risk Monitor', show: showFmTasks },
            { key: 'fm-suspicious', to: '/operasyon/suspicious', label: 'Şüpheli Olaylar', show: showFmTasks },
            { key: 'admin', to: '/admin', label: 'Admin Dashboard', show: isAdmin },
            { key: 'admin-tasks', to: '/admin/tasks', label: 'Admin Görevler', show: isAdmin },
            { key: 'admin-users', to: '/admin/users', label: 'Kullanıcı Yönetimi', show: isAdmin },
            { key: 'admin-settings', to: '/admin/settings', label: 'Sistem Ayarları', show: isAdmin },
            { key: 'admin-audit', to: '/admin/audit', label: 'Audit Logs', show: isAdmin },
            { key: 'admin-metrics', to: '/admin/metrics', label: 'Metrikler', show: isAdmin },
        ],
        [showDashboard, showCustomerPortfolio, showFmTasks, isAdmin]
    );
    const visibleNavItems = navItems.filter((item) => item.show);
    const activeNavKey = useMemo(() => {
        const matched = visibleNavItems.find((item) => location.pathname.startsWith(item.to));
        return matched?.key ?? '';
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
                    <Link to="/" className="app-header__logo">
                        <Landmark size={16} />
                        <span>NRS Finance Portal</span>
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
                            className={({ isActive }) =>
                                `app-header__nav-link ${isActive || location.pathname.startsWith(item.to) ? 'is-active' : ''}`
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
                            <span>{(user?.username ?? user?.email ?? 'testuser')} - {user?.role ?? role ?? 'USER'}</span>
                            <ChevronDown size={14} className={isUserMenuOpen ? 'rotated' : ''} />
                        </button>
                        {isUserMenuOpen && (
                            <div className="header-dropdown header-dropdown--user">
                                <button
                                    type="button"
                                    className="header-dropdown__action"
                                    onClick={toggleTheme}
                                    title="Tema değiştir"
                                >
                                    <span className="theme-switch-icons">
                                        <Sun size={14} className={theme === 'light' ? 'is-active' : ''} />
                                        <Moon size={14} className={theme === 'dark' ? 'is-active' : ''} />
                                    </span>
                                    <span>{theme === 'light' ? 'Açık Tema' : 'Koyu Tema'}</span>
                                </button>
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