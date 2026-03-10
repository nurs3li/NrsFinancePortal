import { Outlet, Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';
import { useState, useEffect, useCallback, useRef } from 'react';
import { notificationClient } from '../api/client';

const linkStyle = (tokens: { headerText: string }) => ({
    color: tokens.headerText,
    textDecoration: 'none',
    fontSize: '0.9375rem',
});

export function Layout() {
    const { isAuthenticated, logout, role, user } = useAuth();
    const { theme, toggleTheme, tokens } = useTheme();
    const navigate = useNavigate();
    const [unreadCount, setUnreadCount] = useState(0);
    const [dropdownItems, setDropdownItems] = useState<{ id: number; title: string; readAt: string | null; type: string }[]>([]);
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const dropdownRef = useRef<HTMLDivElement>(null);

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
        if (dropdownOpen && isAuthenticated) {
            fetchDropdownNotifications();
            fetchUnreadCount();
        }
    }, [dropdownOpen, isAuthenticated, fetchDropdownNotifications, fetchUnreadCount]);

    useEffect(() => {
        const close = (e: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) setDropdownOpen(false);
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

    return (
        <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: tokens.bg }}>
            <header
                style={{
                    padding: '12px 24px',
                    background: tokens.headerBg,
                    color: tokens.headerText,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 24,
                }}
            >
                <Link to="/" style={{ color: tokens.headerText, textDecoration: 'none', fontWeight: 700, fontSize: '1.125rem' }}>
                    NRS Finance Portal
                </Link>
                <nav style={{ display: 'flex', gap: 20, alignItems: 'center', flexWrap: 'wrap' }}>
                    {showDashboard && <Link to="/dashboard" style={linkStyle(tokens)}>Dashboard</Link>}
                    <Link to="/market" style={linkStyle(tokens)}>Piyasa</Link>
                    {showCustomerPortfolio && (
                        <>
                            <Link to="/portfolio" style={linkStyle(tokens)}>Portföy</Link>
                            <Link to="/trade" style={linkStyle(tokens)}>Trade</Link>
                            <Link to="/transactions" style={linkStyle(tokens)}>İşlem Geçmişi</Link>
                        </>
                    )}
                    <Link to="/news" style={linkStyle(tokens)}>Haberler</Link>
                    <div ref={dropdownRef} style={{ position: 'relative', display: 'inline-block' }}>
                        <button
                            type="button"
                            onClick={(e) => { e.stopPropagation(); setDropdownOpen((o) => !o); }}
                            style={{
                                ...linkStyle(tokens),
                                background: 'none',
                                border: 'none',
                                cursor: 'pointer',
                                padding: '4px 8px',
                                display: 'flex',
                                alignItems: 'center',
                                gap: 6,
                            }}
                        >
                            🔔
                            {unreadCount > 0 && (
                                <span style={{
                                    background: tokens.error,
                                    color: '#fff',
                                    borderRadius: 10,
                                    minWidth: 18,
                                    height: 18,
                                    fontSize: '0.7rem',
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                }}>
                                    {unreadCount > 99 ? '99+' : unreadCount}
                                </span>
                            )}
                        </button>
                        {dropdownOpen && (
                            <div style={{
                                position: 'absolute',
                                top: '100%',
                                right: 0,
                                marginTop: 4,
                                minWidth: 280,
                                maxWidth: 360,
                                maxHeight: 400,
                                overflow: 'auto',
                                background: tokens.bgCard,
                                border: `1px solid ${tokens.border}`,
                                borderRadius: 12,
                                boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
                                zIndex: 1000,
                            }}>
                                <div style={{ padding: '12px 16px', borderBottom: `1px solid ${tokens.border}`, fontWeight: 600, fontSize: '0.9375rem' }}>
                                    Bildirimler
                                </div>
                                {dropdownItems.length === 0 ? (
                                    <div style={{ padding: 16, color: tokens.textMuted, fontSize: '0.875rem' }}>Bildirim yok</div>
                                ) : (
                                    dropdownItems.map((n) => (
                                        <div
                                            key={n.id}
                                            onClick={() => { markNotificationRead(n.id); setDropdownOpen(false); navigate('/notifications'); }}
                                            style={{
                                                padding: '12px 16px',
                                                borderBottom: `1px solid ${tokens.border}`,
                                                cursor: 'pointer',
                                                opacity: n.readAt ? 0.9 : 1,
                                            }}
                                        >
                                            <div style={{ fontWeight: 500, fontSize: '0.875rem' }}>{n.title}</div>
                                            <div style={{ fontSize: '0.75rem', color: tokens.textMuted, marginTop: 2 }}>{n.type}</div>
                                        </div>
                                    ))
                                )}
                                <Link
                                    to="/notifications"
                                    onClick={() => setDropdownOpen(false)}
                                    style={{
                                        display: 'block',
                                        padding: '10px 16px',
                                        textAlign: 'center',
                                        fontSize: '0.875rem',
                                        color: tokens.accent,
                                        textDecoration: 'none',
                                        borderTop: `1px solid ${tokens.border}`,
                                    }}
                                >
                                    Tümünü gör
                                </Link>
                            </div>
                        )}
                    </div>
                    <Link to="/notifications" style={linkStyle(tokens)}> Bildirimler</Link>

                    {showFmTasks && (
                        <>
                            <Link to="/fm/tasks" style={linkStyle(tokens)}> Görevler</Link>
                            <Link to="/fm/risk" style={linkStyle(tokens)}> Risk Monitor</Link>
                            <Link to="/operasyon/suspicious" style={linkStyle(tokens)}> Şüpheli Olaylar</Link>
                        </>
                    )}

                    {isAdmin && (
                        <>
                            <Link to="/admin" style={linkStyle(tokens)}> Admin Dashboard</Link>
                            <Link to="/admin/tasks" style={linkStyle(tokens)}>Admin Görevler</Link>
                            <Link to="/admin/users" style={linkStyle(tokens)}>Kullanıcı Yönetimi</Link>
                            <Link to="/admin/settings" style={linkStyle(tokens)}>Sistem Ayarları</Link>
                            <Link to="/admin/audit" style={linkStyle(tokens)}>Audit Logs</Link>
                            <Link to="/admin/metrics" style={linkStyle(tokens)}>Metrikler</Link>
                        </>
                    )}
                </nav>

                <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                    {isAuthenticated && user && (
                        <span style={{ fontSize: '0.8125rem', color: tokens.headerText, opacity: 0.9 }}>
                            {user.username ?? user.email ?? '—'} · {user.role}
                        </span>
                    )}
                    <button
                        type="button"
                        onClick={toggleTheme}
                        style={{
                            padding: '6px 12px',
                            fontSize: '0.8125rem',
                            background: 'rgba(255,255,255,0.15)',
                            color: tokens.headerText,
                            border: '1px solid rgba(255,255,255,0.3)',
                            borderRadius: 8,
                            cursor: 'pointer',
                        }}
                    >
                        {theme === 'light' ? '🌙 Gece' : '☀️ Gündüz'}
                    </button>
                    {isAuthenticated && (
                        <button
                            type="button"
                            onClick={handleLogout}
                            style={{
                                padding: '6px 12px',
                                background: 'rgba(255,255,255,0.2)',
                                color: tokens.headerText,
                                border: 'none',
                                borderRadius: 8,
                                cursor: 'pointer',
                                fontSize: '0.8125rem',
                            }}
                        >
                            Çıkış
                        </button>
                    )}
                </div>
            </header>
            <main style={{ flex: 1, padding: 24, maxWidth: '100%' }}>
                <Outlet />
            </main>
        </div>
    );
}