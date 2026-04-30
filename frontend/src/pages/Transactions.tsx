import { useState, useEffect, useCallback } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
type TransactionRow = {
    id: number;
    accountId: number;
    amount: number;
    balanceAfter: number;
    type: string;
    createdAt: string;
};

function unwrapPayload<T>(payload: unknown): T {
    if (payload && typeof payload === 'object' && 'data' in (payload as object)) {
        return (payload as { data: T }).data;
    }
    return payload as T;
}

function toISOStartOfDay(date: Date): string {
    const d = new Date(date);
    d.setHours(0, 0, 0, 0);
    return d.toISOString();
}

function toISOEndOfDay(date: Date): string {
    const d = new Date(date);
    d.setHours(23, 59, 59, 999);
    return d.toISOString();
}

export function Transactions() {
    const { tokens } = useTheme();
    const [items, setItems] = useState<TransactionRow[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [page, setPage] = useState(0);
    const size = 20;
    const [totalPages, setTotalPages] = useState(0);
    const [startDate, setStartDate] = useState<string>('');
    const [endDate, setEndDate] = useState<string>('');

    const fetchTransactions = useCallback(() => {
        setLoading(true);
        setError(null);
        const useRange = startDate && endDate;
        const params: { page: number; size: number; start?: string; end?: string } = { page, size };
        if (useRange) {
            params.start = toISOStartOfDay(new Date(startDate));
            params.end = toISOEndOfDay(new Date(endDate));
        }
        const url = useRange ? '/api/transactions/me/range' : '/api/transactions/me';
        financeClient
            .get(url, { params })
            .then((res) => {
                const body = unwrapPayload<{ content?: TransactionRow[]; totalPages?: number; totalElements?: number } | TransactionRow[]>(res.data);
                const content = Array.isArray(body) ? body : body?.content ?? [];
                const list = Array.isArray(content) ? content : [];
                setItems(list);
                const total =
                    !Array.isArray(body) && typeof body === 'object'
                        ? body?.totalPages ?? body?.totalElements ?? list.length
                        : list.length;
                setTotalPages(typeof total === 'number' ? Math.max(0, total - 1) : 0);
            })
            .catch((err) => {
                setError(err.response?.data?.message ?? err.message ?? 'Yüklenemedi');
            })
            .finally(() => setLoading(false));
    }, [page, startDate, endDate]);

    useEffect(() => {
        fetchTransactions();
    }, [fetchTransactions]);
    useRefetchOnFocus(fetchTransactions);
    usePolling(fetchTransactions, 60_000);

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
                <h1 style={titleStyle}>İşlem Geçmişi</h1>
                <p style={{ ...mutedStyle, color: tokens.error }}>Hata: {error}</p>
            </div>
        );
    }

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>İşlem Geçmişi</h1>
            <p style={mutedStyle}>Son işlemleriniz. Tarih aralığı seçerek filtreleyebilirsiniz.</p>
            <div style={{ ...cardStyle, marginBottom: 16, display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'flex-end' }}>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                    <span style={mutedStyle}>Başlangıç</span>
                    <input
                        type="date"
                        value={startDate}
                        onChange={(e) => setStartDate(e.target.value)}
                        style={{ padding: 8, borderRadius: 8, border: `1px solid ${tokens.border}` }}
                    />
                </label>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                    <span style={mutedStyle}>Bitiş</span>
                    <input
                        type="date"
                        value={endDate}
                        onChange={(e) => setEndDate(e.target.value)}
                        style={{ padding: 8, borderRadius: 8, border: `1px solid ${tokens.border}` }}
                    />
                </label>
                <button
                    type="button"
                    onClick={() => { setPage(0); fetchTransactions(); }}
                    style={{
                        padding: '8px 16px',
                        borderRadius: 8,
                        border: `1px solid ${tokens.border}`,
                        background: tokens.bgCard,
                        color: tokens.text,
                        cursor: 'pointer',
                    }}
                >
                    Filtrele
                </button>
                {(startDate || endDate) && (
                    <button
                        type="button"
                        onClick={() => { setStartDate(''); setEndDate(''); setPage(0); }}
                        style={{
                            padding: '8px 16px',
                            borderRadius: 8,
                            border: '1px solid transparent',
                            background: 'transparent',
                            color: tokens.textMuted,
                            cursor: 'pointer',
                        }}
                    >
                        Temizle
                    </button>
                )}
            </div>
            <div style={cardStyle}>
                {loading ? (
                    <p style={mutedStyle}>Yükleniyor...</p>
                ) : items.length === 0 ? (
                    <p style={mutedStyle}>Henüz işlem yok.</p>
                ) : (
                    <>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                            <thead>
                            <tr style={{ borderBottom: `2px solid ${tokens.border}` }}>
                                <th style={{ textAlign: 'left', padding: 8 }}>Tarih</th>
                                <th style={{ textAlign: 'left', padding: 8 }}>Tür</th>
                                <th style={{ textAlign: 'right', padding: 8 }}>Tutar</th>
                                <th style={{ textAlign: 'right', padding: 8 }}>Bakiye (sonra)</th>
                            </tr>
                            </thead>
                            <tbody>
                            {items.map((tx) => (
                                <tr key={tx.id} style={{ borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                    <td style={{ padding: 8 }}>{new Date(tx.createdAt).toLocaleString('tr-TR')}</td>
                                    <td style={{ padding: 8 }}>{tx.type}</td>
                                    <td style={{ padding: 8, textAlign: 'right' }}>₺{Number(tx.amount).toLocaleString('tr-TR')}</td>
                                    <td style={{ padding: 8, textAlign: 'right' }}>₺{Number(tx.balanceAfter).toLocaleString('tr-TR')}</td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                        {totalPages > 0 && (
                            <div style={{ marginTop: 12, display: 'flex', gap: 8, alignItems: 'center' }}>
                                <button
                                    type="button"
                                    disabled={page <= 0}
                                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                                    style={{ padding: '6px 12px', cursor: page <= 0 ? 'not-allowed' : 'pointer', opacity: page <= 0 ? 0.5 : 1 }}
                                >
                                    Önceki
                                </button>
                                <span style={mutedStyle}>Sayfa {page + 1} / {totalPages + 1}</span>
                                <button
                                    type="button"
                                    disabled={page >= totalPages}
                                    onClick={() => setPage((p) => p + 1)}
                                    style={{ padding: '6px 12px', cursor: page >= totalPages ? 'not-allowed' : 'pointer', opacity: page >= totalPages ? 0.5 : 1 }}
                                >
                                    Sonraki
                                </button>
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}