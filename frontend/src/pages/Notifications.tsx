import { useState, useEffect, useCallback } from 'react';
import { notificationClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';

type NotificationItem = {
    id: number;
    title: string;
    body: string | null;
    type: string;
    readAt: string | null;
    createdAt: string;
    lastOccurredAt: string | null;
    referenceType: string | null;
    referenceId: number | null;
    occurrenceCount: number;
};

type PageResponse = {
    content: NotificationItem[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
};

export function Notifications() {
    const { tokens } = useTheme();
    const [items, setItems] = useState<NotificationItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const size = 20;
    const [unreadOnly, setUnreadOnly] = useState(false);

    const fetchNotifications = useCallback(() => {
        setLoading(true);
        setError(null);
        notificationClient
            .get<PageResponse>('/api/notifications/me', {
                params: { page, size, unreadOnly },
            })
            .then((res) => {
                const data = res.data;
                const content = data?.content ?? [];
                setItems(Array.isArray(content) ? content : []);
                setTotalPages(data?.totalPages ?? 0);
                setTotalElements(data?.totalElements ?? 0);
            })
            .catch((err) => {
                setError(err.response?.data?.message ?? err.message ?? 'Bildirimler yüklenemedi');
            })
            .finally(() => setLoading(false));
    }, [page, unreadOnly]);

    useEffect(() => {
        fetchNotifications();
    }, [fetchNotifications]);
    useRefetchOnFocus(fetchNotifications);

    const markAsRead = (id: number) => {
        notificationClient.patch(`/api/notifications/${id}/read`).then(() => {
            setItems((prev) =>
                prev.map((n) => (n.id === id ? { ...n, readAt: new Date().toISOString() } : n))
            );
            fetchNotifications();
        });
    };

    const pageStyle: React.CSSProperties = {
        padding: 24,
        background: tokens.bg,
        color: tokens.text,
        minHeight: '100%',
    };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };
    const cardStyle: React.CSSProperties = {
        padding: 16,
        borderRadius: 12,
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        marginBottom: 12,
    };

    if (error) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>🔔 Bildirimler</h1>
                <p style={{ ...mutedStyle, color: tokens.error }}>{error}</p>
            </div>
        );
    }

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>🔔 Bildirimler</h1>
            <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer', fontSize: '0.875rem' }}>
                    <input
                        type="checkbox"
                        checked={unreadOnly}
                        onChange={(e) => { setUnreadOnly(e.target.checked); setPage(0); }}
                    />
                    Sadece okunmamış
                </label>
                <span style={mutedStyle}>
                    Toplam {totalElements} bildirim
                </span>
            </div>

            {loading ? (
                <p style={mutedStyle}>Yükleniyor…</p>
            ) : items.length === 0 ? (
                <p style={mutedStyle}>Bildirim yok.</p>
            ) : (
                <>
                    {items.map((n) => (
                        <div
                            key={n.id}
                            style={{
                                ...cardStyle,
                                opacity: n.readAt ? 0.85 : 1,
                                cursor: 'pointer',
                            }}
                            onClick={() => markAsRead(n.id)}
                        >
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
                                <div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                        <strong style={{ fontSize: '0.9375rem' }}>{n.title}</strong>
                                        {n.occurrenceCount > 1 && (
                                            <span style={{
                                                background: tokens.accent,
                                                color: '#fff',
                                                borderRadius: 10,
                                                padding: '1px 8px',
                                                fontSize: '0.7rem',
                                                fontWeight: 700,
                                            }}>
                            {n.occurrenceCount}x
                        </span>
                                        )}
                                    </div>
                                    {n.body && <p style={{ ...mutedStyle, marginTop: 4, marginBottom: 0 }}>{n.body}</p>}
                                </div>
                                {!n.readAt && <span style={{ fontSize: '0.75rem', color: tokens.accent }}>Yeni</span>}
                            </div>
                            <p style={{ ...mutedStyle, fontSize: '0.75rem', marginTop: 8, marginBottom: 0 }}>
                                {n.type} · {new Date(n.lastOccurredAt ?? n.createdAt).toLocaleString('tr-TR')}
                                {n.occurrenceCount > 1 && ` · ilk: ${new Date(n.createdAt).toLocaleString('tr-TR')}`}
                            </p>
                        </div>
                    ))}
                    {totalPages > 1 && (
                        <div style={{ marginTop: 16, display: 'flex', gap: 8, alignItems: 'center' }}>
                            <button
                                type="button"
                                disabled={page <= 0}
                                onClick={() => setPage((p) => p - 1)}
                                style={{
                                    padding: '6px 12px',
                                    borderRadius: 8,
                                    border: `1px solid ${tokens.border}`,
                                    background: tokens.bgCard,
                                    color: tokens.text,
                                    cursor: page <= 0 ? 'not-allowed' : 'pointer',
                                }}
                            >
                                Önceki
                            </button>
                            <span style={mutedStyle}>
                                Sayfa {page + 1} / {totalPages}
                            </span>
                            <button
                                type="button"
                                disabled={page >= totalPages - 1}
                                onClick={() => setPage((p) => p + 1)}
                                style={{
                                    padding: '6px 12px',
                                    borderRadius: 8,
                                    border: `1px solid ${tokens.border}`,
                                    background: tokens.bgCard,
                                    color: tokens.text,
                                    cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer',
                                }}
                            >
                                Sonraki
                            </button>
                        </div>
                    )}
                </>
            )}
        </div>
    );
}