import { useState, useEffect } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';

type TransactionRow = {
    id: number;
    accountId: number;
    amount: number;
    balanceAfter: number;
    type: string;
    createdAt: string;
};

export function Transactions() {
    const { tokens } = useTheme();
    const [items, setItems] = useState<TransactionRow[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        financeClient
            .get('/api/transactions/me', { params: { page: 0, size: 20 } })
            .then((res) => {
                const content = res.data?.content ?? res.data ?? [];
                setItems(Array.isArray(content) ? content : []);
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
                <h1 style={titleStyle}>İşlem Geçmişi</h1>
                <p style={{ ...mutedStyle, color: tokens.error }}>Hata: {error}</p>
            </div>
        );
    }

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>İşlem Geçmişi</h1>
            <p style={mutedStyle}>Son işlemleriniz.</p>
            <div style={cardStyle}>
                {loading ? (
                    <p style={mutedStyle}>Yükleniyor...</p>
                ) : items.length === 0 ? (
                    <p style={mutedStyle}>Henüz işlem yok.</p>
                ) : (
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
                )}
            </div>
        </div>
    );
}