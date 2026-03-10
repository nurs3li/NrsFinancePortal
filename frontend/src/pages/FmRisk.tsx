import React, { useState, useEffect, useCallback } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useAuth } from '../auth/AuthContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';

type UserOption = { id: number; username: string; email: string; role: string };

type WhaleTimelineItem = {
    id: number;
    userId: number;
    whaleLevel: string;
    impactScore: number | null;
    reason: string | null;
    dailyVolume: number | null;
    hourlyTransactionCount: number | null;
    maxSingleTransaction: number | null;
    pattern: string | null;
    behavior: string | null;
    risk: string | null;
    triggeredAt: string;
    createdAt: string;
};

export function FmRisk() {
    const { tokens } = useTheme();
    const { role } = useAuth();
    const [users, setUsers] = useState<UserOption[]>([]);
    const [selectedUserId, setSelectedUserId] = useState<number | ''>('');
    const [timeline, setTimeline] = useState<WhaleTimelineItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const canViewAllUsers = role === 'ADMIN' || role === 'FINANCE_MANAGER';

    useEffect(() => {
        if (!canViewAllUsers) return;
        financeClient
            .get('/api/users')
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                const list = Array.isArray(raw) ? raw : [];
                setUsers(list);
                if (list.length > 0 && selectedUserId === '') {
                    setSelectedUserId(list[0].id);
                }
            })
            .catch(() => setUsers([]));
    }, [canViewAllUsers]);

    const loadTimeline = useCallback((userId: number) => {
        setLoading(true);
        setError(null);
        financeClient
            .get<WhaleTimelineItem[]>(`/api/whales/${userId}/timeline`)
            .then((res) => {
                const list = Array.isArray(res.data) ? res.data : [];
                setTimeline(list);
            })
            .catch((err) => {
                setError(err.response?.data?.message ?? err.message ?? 'Timeline yüklenemedi');
            })
            .finally(() => setLoading(false));
    }, []);

    const refetchTimeline = useCallback(() => {
        if (selectedUserId !== '') {
            loadTimeline(selectedUserId);
        }
    }, [selectedUserId, loadTimeline]);

    useRefetchOnFocus(refetchTimeline);
    usePolling(refetchTimeline, 60_000);

    useEffect(() => {
        if (selectedUserId === '') return;
        loadTimeline(selectedUserId);
    }, [selectedUserId, loadTimeline]);

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };
    const cardStyle: React.CSSProperties = {
        padding: 16,
        borderRadius: 12,
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
    };

    const showUserSelect = canViewAllUsers && users.length > 0;

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Risk Monitör</h1>
            <p style={mutedStyle}>Whale timeline ve risk görünümü.</p>
            {showUserSelect && (
                <div style={{ ...cardStyle, marginBottom: 16 }}>
                    <label style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                        <span style={mutedStyle}>Kullanıcı:</span>
                        <select
                            value={selectedUserId === '' ? '' : String(selectedUserId)}
                            onChange={(e) => setSelectedUserId(e.target.value === '' ? '' : Number(e.target.value))}
                            style={{
                                padding: '8px 12px',
                                borderRadius: 8,
                                border: `1px solid ${tokens.border}`,
                                background: tokens.bgCard,
                                color: tokens.text,
                                minWidth: 200,
                            }}
                        >
                            {users.map((u) => (
                                <option key={u.id} value={u.id}>{u.username ?? u.email ?? `#${u.id}`}</option>
                            ))}
                        </select>
                    </label>
                </div>
            )}
            {error && <p style={{ ...mutedStyle, color: tokens.error, marginBottom: 8 }}>Hata: {error}</p>}
            <div style={cardStyle}>
                {loading ? (
                    <p style={mutedStyle}>Yükleniyor...</p>
                ) : timeline.length === 0 ? (
                    <p style={mutedStyle}>
                        Bu kullanıcı için whale kaydı bulunmuyor. Whale verisi oluştuğunda burada listelenecektir.
                    </p>
                ) : (
                    <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                        {timeline.map((item) => (
                            <li
                                key={item.id}
                                style={{
                                    borderBottom: `1px solid ${tokens.border}`,
                                    padding: '12px 0',
                                }}
                            >
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center', marginBottom: 6 }}>
                                    <span style={{ fontWeight: 600 }}>{item.whaleLevel}</span>
                                    <span style={mutedStyle}>
                {new Date(item.triggeredAt).toLocaleString('tr-TR')}
            </span>
                                    {item.reason && <span style={mutedStyle}>— {item.reason}</span>}
                                    {item.risk && (
                                        <span style={{
                                            fontSize: '0.7rem',
                                            fontWeight: 700,
                                            padding: '2px 8px',
                                            borderRadius: 8,
                                            background: item.risk === 'CRITICAL' ? '#e74c3c'
                                                : item.risk === 'HIGH' ? '#e67e22'
                                                    : item.risk === 'MEDIUM' ? '#f1c40f'
                                                        : tokens.accent,
                                            color: '#fff',
                                        }}>
                    {item.risk}
                </span>
                                    )}
                                </div>
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, fontSize: '0.8rem', color: tokens.textMuted }}>
                                    {item.dailyVolume != null && (
                                        <span>Günlük hacim: <strong style={{ color: tokens.text }}>₺{Number(item.dailyVolume).toLocaleString('tr-TR')}</strong></span>
                                    )}
                                    {item.hourlyTransactionCount != null && (
                                        <span>Saatlik işlem: <strong style={{ color: tokens.text }}>{item.hourlyTransactionCount}</strong></span>
                                    )}
                                    {item.maxSingleTransaction != null && (
                                        <span>Maks. tek işlem: <strong style={{ color: tokens.text }}>₺{Number(item.maxSingleTransaction).toLocaleString('tr-TR')}</strong></span>
                                    )}
                                    {item.impactScore != null && (
                                        <span>Impact: <strong style={{ color: tokens.text }}>{item.impactScore}/100</strong></span>
                                    )}
                                    {item.pattern && <span>Pattern: <strong style={{ color: tokens.text }}>{item.pattern}</strong></span>}
                                    {item.behavior && <span>Davranış: <strong style={{ color: tokens.text }}>{item.behavior}</strong></span>}
                                </div>
                            </li>
                        ))}
                    </ul>
                )}
            </div>
        </div>
    );
}