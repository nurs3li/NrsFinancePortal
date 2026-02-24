import { useState, useEffect } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';

type UserRow = { id: number; username: string; email: string; role: string };

export function AdminUsers() {
    const { tokens } = useTheme();
    const [users, setUsers] = useState<UserRow[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        financeClient
            .get('/api/users')
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                setUsers(Array.isArray(raw) ? raw : []);
            })
            .catch((err) => {
                setError(err.response?.data?.message ?? err.message ?? 'Yüklenemedi');
            })
            .finally(() => setLoading(false));
    }, []);

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };
    const cardStyle: React.CSSProperties = {
        padding: 16,
        borderRadius: 12,
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
    };

    if (error) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Kullanıcı Yönetimi</h1>
                <p style={{ ...mutedStyle, color: tokens.error }}>Hata: {error}</p>
            </div>
        );
    }

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Kullanıcı Yönetimi</h1>
            <p style={mutedStyle}>Kullanıcı listesi. Freeze / limit (Faz 1 sonrası eklenecek).</p>
            <div style={cardStyle}>
                {loading ? (
                    <p style={mutedStyle}>Yükleniyor...</p>
                ) : users.length === 0 ? (
                    <p style={mutedStyle}>Kullanıcı bulunamadı.</p>
                ) : (
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                        <thead>
                        <tr style={{ borderBottom: `2px solid ${tokens.border}` }}>
                            <th style={{ textAlign: 'left', padding: 8 }}>ID</th>
                            <th style={{ textAlign: 'left', padding: 8 }}>Kullanıcı adı</th>
                            <th style={{ textAlign: 'left', padding: 8 }}>E-posta</th>
                            <th style={{ textAlign: 'left', padding: 8 }}>Rol</th>
                        </tr>
                        </thead>
                        <tbody>
                        {users.map((u) => (
                            <tr key={u.id} style={{ borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                <td style={{ padding: 8 }}>{u.id}</td>
                                <td style={{ padding: 8 }}>{u.username ?? '—'}</td>
                                <td style={{ padding: 8 }}>{u.email ?? '—'}</td>
                                <td style={{ padding: 8 }}>{u.role}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </div>
        </div>
    );
}