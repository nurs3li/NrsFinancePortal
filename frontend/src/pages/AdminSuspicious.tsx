import { useState, useEffect, useMemo } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useCallback } from 'react';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
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

    const fetchEvents = useCallback(() => {
        setLoading(true);
        financeClient
            .get('/api/admin/suspicious/events', { params: { limit: 100 } })
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                setEvents(Array.isArray(raw) ? raw : []);
            })
            .catch((err) => setError(err.response?.data?.message ?? err.message ?? 'Yüklenemedi'))
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        fetchEvents();
    }, [fetchEvents]);

    useRefetchOnFocus(fetchEvents);
    usePolling(fetchEvents, 60_000);

    /** Kullanıcıya göre grupla: key = username ?? userId */
    const eventsByUser = useMemo(() => {
        const map: Record<string, SuspiciousRow[]> = {};
        for (const e of events) {
            const key = e.username ?? `Kullanıcı #${e.userId}`;
            if (!map[key]) map[key] = [];
            map[key].push(e);
        }
        return map;
    }, [events]);

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
            <p style={mutedStyle}>Şüpheli işlem uyarıları, kullanıcıya göre gruplu (Finance Manager / Admin).</p>
            {loading ? (
                <div style={cardStyle}>
                    <p style={mutedStyle}>Yükleniyor...</p>
                </div>
            ) : events.length === 0 ? (
                <div style={cardStyle}>
                    <p style={mutedStyle}>Kayıt yok.</p>
                </div>
            ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
                    {Object.entries(eventsByUser).map(([userKey, userEvents]) => (
                        <div key={userKey} style={cardStyle}>
                            <h2 style={{ fontSize: '1.125rem', fontWeight: 600, marginBottom: 12, borderBottom: `1px solid ${tokens.border}`, paddingBottom: 8 }}>
                                {userKey} <span style={mutedStyle}>({userEvents.length} olay)</span>
                            </h2>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                                <thead>
                                <tr style={{ borderBottom: `2px solid ${tokens.border}` }}>
                                    <th style={{ textAlign: 'left', padding: 8 }}>Sebep</th>
                                    <th style={{ textAlign: 'right', padding: 8 }}>Tutar</th>
                                    <th style={{ textAlign: 'left', padding: 8 }}>Tarih</th>
                                </tr>
                                </thead>
                                <tbody>
                                {userEvents.map((e, i) => (
                                    <tr key={i} style={{ borderBottom: `1px solid ${tokens.border}` }}>
                                        <td style={{ padding: 8 }}>{e.reason}</td>
                                        <td style={{ padding: 8, textAlign: 'right' }}>₺{Number(e.amount).toLocaleString('tr-TR')}</td>
                                        <td style={{ padding: 8 }}>{new Date(e.occurredAt).toLocaleString('tr-TR')}</td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}