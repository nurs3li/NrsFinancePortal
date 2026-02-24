import { useTheme } from '../theme/ThemeContext';

export function AdminSettings() {
    const { tokens } = useTheme();
    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Sistem Ayarları</h1>
            <p style={mutedStyle}>Eşik ve risk config (Faz 6 / config API sonrası).</p>
        </div>
    );
}