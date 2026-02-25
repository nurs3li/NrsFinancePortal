import { useTheme } from '../theme/ThemeContext';

export function Notifications() {
    const { tokens } = useTheme();
    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>🔔 Bildirimler</h1>
            <p style={mutedStyle}>Bildirimler (Notification Service sonrası burada listelenecek).</p>
        </div>
    );
}