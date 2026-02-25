import { useState, useEffect } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';

type SuspiciousRow = {
    userId: number;
    username: string | null;
    email: string | null;
    transactionId: number | null;
    reason: string;
    amount: number;
    countInWindow: number | null;
    thresholdAmount: number | null;
    thresholdCount: number | null;
    occurredAt: string;
};

export function AdminSuspicious() {
    const { tokens } = useTheme();
    const [events, setEvents] = useState<SuspiciousRow[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        financeClient
            .get('/api/admin/suspicious/events', { params: { limit: 100 } })
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                setEvents(Array.isArray(raw) ? raw : []);
            })
            .catch((err) => setError(err.response?.data?.message ?? err.message ?? 'Yüklenemedi'))
            .finally(() => setLoading(false));
    }, []);

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };
    const cardStyle: React.CSSProperties = { padding: 16, borderRadius: 12, background: tokens.bgCard, border: `1px solid ${tokens.border}` };

    if (error) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Şüpheli Olaylar</h1>
                <p style={{ ...mutedStyle, color: tokens.error }}>Hata: {error}</p>
            </div>
        );
    }

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Şüpheli Olaylar</h1>
            <p style={mutedStyle}>Şüpheli işlem uyarıları (Finance Manager / Admin).</p>
            <div style={cardStyle}>
                {loading ? (
                    <p style={mutedStyle}>Yükleniyor...</p>
                ) : events.length === 0 ? (
                    <p style={mutedStyle}>Kayıt yok.</p>
                ) : (
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                        <thead>
                        <tr style={{ borderBottom: `2px solid ${tokens.border}` }}>
                            <th style={{ textAlign: 'left', padding: 8 }}>Kullanıcı</th>
                            <th style={{ textAlign: 'left', padding: 8 }}>Sebep</th>
                            <th style={{ textAlign: 'right', padding: 8 }}>Tutar</th>
                            <th style={{ textAlign: 'left', padding: 8 }}>Tarih</th>
                        </tr>
                        </thead>
                        <tbody>
                        {events.map((e, i) => (
                            <tr key={i} style={{ borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                <td style={{ padding: 8 }}>{e.username ?? e.userId}</td>
                                <td style={{ padding: 8 }}>{e.reason}</td>
                                <td style={{ padding: 8, textAlign: 'right' }}>₺{Number(e.amount).toLocaleString('tr-TR')}</td>
                                <td style={{ padding: 8 }}>{new Date(e.occurredAt).toLocaleString('tr-TR')}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </div>
        </div>
    );
}