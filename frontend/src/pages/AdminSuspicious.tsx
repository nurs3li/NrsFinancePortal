import { useState, useEffect, useCallback, useMemo } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
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

type UserGroup = {
    userId: number;
    username: string | null;
    email: string | null;
    events: SuspiciousRow[];
};

export function AdminSuspicious() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const [events, setEvents] = useState<SuspiciousRow[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [expandedUserIds, setExpandedUserIds] = useState<Set<number>>(new Set());

    const fetchEvents = useCallback(() => {
        setLoading(true);
        financeClient
            .get('/api/admin/suspicious/events', { params: { limit: 100 } })
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                setEvents(Array.isArray(raw) ? raw : []);
            })
            .catch((err) => setError(err.response?.data?.message ?? err.message ?? t('common.loadingFailed', 'Yüklenemedi')))
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        fetchEvents();
    }, [fetchEvents]);

    useRefetchOnFocus(fetchEvents);
    usePolling(fetchEvents, 60_000);

    // Kullanıcıya göre grupla
    const groups: UserGroup[] = useMemo(() => {
        const map = new Map<number, UserGroup>();
        for (const e of events) {
            const existing = map.get(e.userId);
            if (!existing) {
                map.set(e.userId, {
                    userId: e.userId,
                    username: e.username,
                    email: e.email,
                    events: [e],
                });
            } else {
                existing.events.push(e);
            }
        }
        // Son olaya göre sırala (en güncel üstte)
        return Array.from(map.values()).sort((a, b) => {
            const aLast = a.events[0]?.occurredAt ?? '';
            const bLast = b.events[0]?.occurredAt ?? '';
            return bLast.localeCompare(aLast);
        });
    }, [events]);

    const toggleUser = (userId: number) => {
        setExpandedUserIds((prev) => {
            const next = new Set(prev);
            if (next.has(userId)) next.delete(userId);
            else next.add(userId);
            return next;
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
    };

    if (error) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>{t('nav.suspiciousEvents', 'Şüpheli Olaylar')}</h1>
                <p style={{ ...mutedStyle, color: tokens.error }}>{t('news.errorPrefix', 'Hata')}: {error}</p>
            </div>
        );
    }

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>{t('nav.suspiciousEvents', 'Şüpheli Olaylar')}</h1>
            <p style={mutedStyle}>
                {t('admin.suspiciousSubtitle', 'Şüpheli işlem uyarıları, kullanıcıya göre gruplu. Satıra tıklayınca kullanıcının detaylı olay listesi açılır.')}
            </p>

            {loading ? (
                <div style={cardStyle}>
                    <p style={mutedStyle}>{t('common.loading', 'Yükleniyor...')}</p>
                </div>
            ) : groups.length === 0 ? (
                <div style={cardStyle}>
                    <p style={mutedStyle}>{t('admin.noRecords', 'Kayıt yok.')}</p>
                </div>
            ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                    {groups.map((group) => {
                        const isOpen = expandedUserIds.has(group.userId);
                        const latest = group.events[0];
                        const headerLabel =
                            (group.username ?? t('admin.user', 'Kullanıcı')) +
                            ` (ID: ${group.userId}` +
                            (group.email ? `, ${group.email}` : '') +
                            ')';

                        return (
                            <div key={group.userId} style={cardStyle}>
                                <button
                                    type="button"
                                    onClick={() => toggleUser(group.userId)}
                                    style={{
                                        all: 'unset',
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'center',
                                        width: '100%',
                                        cursor: 'pointer',
                                        paddingBottom: 8,
                                        borderBottom: `1px solid ${tokens.border}`,
                                    }}
                                >
                                    <div>
                                        <div style={{ fontSize: '1.05rem', fontWeight: 600 }}>{headerLabel}</div>
                                        <div style={mutedStyle}>
                                            {group.events.length} {t('admin.events', 'olay')} • {t('admin.last', 'Son')}: {latest && new Date(latest.occurredAt).toLocaleString(lang === 'en' ? 'en-US' : 'tr-TR')}
                                        </div>
                                    </div>
                                    <div style={{ fontSize: '1.25rem' }}>{isOpen ? '▾' : '▸'}</div>
                                </button>

                                {isOpen && (
                                    <div style={{ marginTop: 12 }}>
                                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                                            <thead>
                                            <tr style={{ borderBottom: `2px solid ${tokens.border}` }}>
                                                <th style={{ textAlign: 'left', padding: 8 }}>{t('admin.reason', 'Sebep')}</th>
                                                <th style={{ textAlign: 'right', padding: 8 }}>{t('transactions.amount', 'Tutar')}</th>
                                                <th style={{ textAlign: 'left', padding: 8 }}>{t('news.date', 'Tarih')}</th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            {group.events.map((e, i) => (
                                                <tr key={i} style={{ borderBottom: `1px solid ${tokens.border}` }}>
                                                    <td style={{ padding: 8 }}>{e.reason}</td>
                                                    <td style={{ padding: 8, textAlign: 'right' }}>
                                                        ₺{Number(e.amount).toLocaleString(lang === 'en' ? 'en-US' : 'tr-TR')}
                                                    </td>
                                                    <td style={{ padding: 8 }}>
                                                        {new Date(e.occurredAt).toLocaleString(lang === 'en' ? 'en-US' : 'tr-TR')}
                                                    </td>
                                                </tr>
                                            ))}
                                            </tbody>
                                        </table>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}