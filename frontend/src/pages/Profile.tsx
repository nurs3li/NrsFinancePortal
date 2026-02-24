import { useTheme } from '../theme/ThemeContext';
import { useAuth } from '../auth/AuthContext';

export function Profile() {
    const { tokens } = useTheme();
    const { user } = useAuth();

    const pageStyle: React.CSSProperties = {
        padding: 24,
        background: tokens.bg,
        color: tokens.text,
        minHeight: '100%',
    };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem', marginTop: 4 };
    const cardStyle: React.CSSProperties = {
        padding: 16,
        borderRadius: 12,
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        maxWidth: 400,
    };

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Profil</h1>
            <p style={mutedStyle}>Hesap bilgileriniz.</p>
            {user ? (
                <div style={cardStyle}>
                    <p style={{ margin: '8px 0' }}><strong>Kullanıcı adı:</strong> {user.username || '—'}</p>
                    <p style={{ margin: '8px 0' }}><strong>E-posta:</strong> {user.email || '—'}</p>
                    <p style={{ margin: '8px 0' }}><strong>Rol:</strong> {user.role}</p>
                </div>
            ) : (
                <p style={mutedStyle}>Profil yükleniyor...</p>
            )}
        </div>
    );
}