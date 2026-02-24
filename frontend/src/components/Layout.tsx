import { Outlet, Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';

const linkStyle = (tokens: { headerText: string }) => ({
    color: tokens.headerText,
    textDecoration: 'none',
    fontSize: '0.9375rem',
});

export function Layout() {
    const { isAuthenticated, logout, role } = useAuth();
    const { theme, toggleTheme, tokens } = useTheme();
    const navigate = useNavigate();

    const handleLogout = () => {
        logout();
        navigate('/');
    };

    const isFm = role === 'FINANCE_MANAGER';
    const isAdmin = role === 'ADMIN';

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
                    {/* Kullanıcı tarafı – herkes */}
                    <Link to="/dashboard" style={linkStyle(tokens)}>Dashboard</Link>
                    <Link to="/market" style={linkStyle(tokens)}>Piyasa</Link>
                    <Link to="/portfolio" style={linkStyle(tokens)}>Portföy</Link>
                    <Link to="/trade" style={linkStyle(tokens)}>Trade</Link>
                    <Link to="/transactions" style={linkStyle(tokens)}>İşlem Geçmişi</Link>
                    <Link to="/news" style={linkStyle(tokens)}>Haberler</Link>
                    <Link to="/profile" style={linkStyle(tokens)}>Profil</Link>
                    <Link to="/notifications" style={linkStyle(tokens)}>🔔 Bildirimler</Link>

                    {/* Operasyon – FM veya Admin */}
                    {(isFm || isAdmin) && (
                        <>
                            <Link to="/fm/tasks" style={linkStyle(tokens)}>📋 Görevler</Link>
                            <Link to="/fm/risk" style={linkStyle(tokens)}>⚠️ Risk Monitor</Link>
                            <Link to="/operasyon/suspicious" style={linkStyle(tokens)}>🚨 Şüpheli Olaylar</Link>
                        </>
                    )}

                    {/* Admin özel */}
                    {isAdmin && (
                        <>
                            <Link to="/admin" style={linkStyle(tokens)}>📊 Admin Dashboard</Link>
                            <Link to="/admin/tasks" style={linkStyle(tokens)}>Admin Görevler</Link>
                            <Link to="/admin/users" style={linkStyle(tokens)}>Kullanıcı Yönetimi</Link>
                            <Link to="/admin/accounts" style={linkStyle(tokens)}>Hesap Yönetimi</Link>
                            <Link to="/admin/settings" style={linkStyle(tokens)}>Sistem Ayarları</Link>
                            <Link to="/admin/audit" style={linkStyle(tokens)}>Audit Logs</Link>
                        </>
                    )}
                </nav>
                <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 12 }}>
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