import { useState, useEffect } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts';

type TxRow = { id: number; balanceAfter: number; createdAt: string };

const tooltipBalanceFormatter = ((value: number) => [Number(value).toLocaleString('tr-TR') + ' ₺', 'Bakiye']) as never;

export function BalanceChart() {
    const { tokens } = useTheme();
    const [points, setPoints] = useState<{ date: string; balance: number }[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const end = new Date();
        const start = new Date();
        start.setDate(start.getDate() - 30);
        const params = {
            start: start.toISOString(),
            end: end.toISOString(),
            page: 0,
            size: 200,
        };
        financeClient
            .get<{ content?: TxRow[] }>('/api/transactions/me/range', { params })
            .then((res) => {
                const content = res.data?.content ?? res.data ?? [];
                const list = Array.isArray(content) ? content : [];
                const sorted = [...list].sort(
                    (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
                );
                const chartData = sorted.map((tx) => ({
                    date: new Date(tx.createdAt).toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit' }),
                    balance: Number(tx.balanceAfter),
                }));
                setPoints(chartData);
            })
            .catch(() => setError('Bakiye geçmişi yüklenemedi'))
            .finally(() => setLoading(false));
    }, []);

    const cardStyle: React.CSSProperties = {
        padding: 20,
        borderRadius: 12,
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        marginBottom: 20,
    };

    if (error) {
        return (
            <div style={cardStyle}>
                <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 8 }}>Bakiye grafiği</h2>
                <p style={{ fontSize: '0.875rem', color: tokens.error }}>{error}</p>
            </div>
        );
    }

    if (loading) {
        return (
            <div style={cardStyle}>
                <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 8 }}>Bakiye grafiği</h2>
                <p style={{ fontSize: '0.875rem', color: tokens.textMuted }}>Yükleniyor...</p>
            </div>
        );
    }

    if (points.length === 0) {
        return (
            <div style={cardStyle}>
                <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 8 }}>Bakiye grafiği</h2>
                <p style={{ fontSize: '0.875rem', color: tokens.textMuted }}>Son 30 günde işlem yok; grafik oluşturulamadı.</p>
            </div>
        );
    }

    return (
        <div style={cardStyle}>
            <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 16 }}>Bakiye grafiği (son 30 gün)</h2>
            <div style={{ width: '100%', height: 260 }}>
                <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={points} margin={{ top: 8, right: 16, left: 8, bottom: 8 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} />
                        <XAxis dataKey="date" tick={{ fill: tokens.textMuted, fontSize: 11 }} />
                        <YAxis tick={{ fill: tokens.textMuted, fontSize: 11 }} tickFormatter={(v) => v.toLocaleString('tr-TR')} />
                        <Tooltip
                            contentStyle={{ background: tokens.bgCard, border: `1px solid ${tokens.border}`, borderRadius: 8 }}
                            formatter={tooltipBalanceFormatter}
                        />
                        <Line type="monotone" dataKey="balance" name="Bakiye" stroke={tokens.accent} strokeWidth={2} dot={{ r: 3 }} />
                    </LineChart>
                </ResponsiveContainer>
            </div>
        </div>
    );
}