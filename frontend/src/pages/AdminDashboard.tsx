import { useTheme } from '../theme/ThemeContext';

export function AdminDashboard() {
    const { tokens } = useTheme();
    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Admin Dashboard</h1>
            <p style={mutedStyle}>Dondurulmuş hesaplar, escalation sayıları (Faz 1 sonrası doldurulacak).</p>
        </div>
    );
}