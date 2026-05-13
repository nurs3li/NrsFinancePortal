import { useState, useEffect } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts';

type SnapshotRow = { snapshotAt: string; portfolioValueTry?: number };

function unwrapSnapshotList(payload: unknown): SnapshotRow[] {
    if (Array.isArray(payload)) return payload as SnapshotRow[];
    if (payload && typeof payload === 'object' && 'data' in payload && Array.isArray((payload as { data: unknown }).data)) {
        return (payload as { data: SnapshotRow[] }).data;
    }
    return [];
}

const tooltipValueFormatter = ((value: number) => [Number(value).toLocaleString('tr-TR') + ' ₺', 'Portföy']) as never;

export function BalanceChart() {
    const { tokens } = useTheme();
    const [points, setPoints] = useState<{ date: string; balance: number }[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const end = new Date();
        const start = new Date();
        start.setDate(start.getDate() - 30);
        financeClient
            .get('/api/portfolio/snapshots/me', {
                params: { from: start.toISOString(), to: end.toISOString() },
            })
            .then((res) => {
                const list = unwrapSnapshotList(res.data);
                const sorted = [...list].sort(
                    (a, b) => new Date(a.snapshotAt).getTime() - new Date(b.snapshotAt).getTime()
                );
                const chartData = sorted.map((row) => ({
                    date: new Date(row.snapshotAt).toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit' }),
                    balance: Number(row.portfolioValueTry ?? 0),
                }));
                setPoints(chartData);
            })
            .catch(() => setError('Portföy geçmişi yüklenemedi'))
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
                <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 8 }}>Portföy değeri (anık kayıtlar)</h2>
                <p style={{ fontSize: '0.875rem', color: tokens.error }}>{error}</p>
            </div>
        );
    }

    if (loading) {
        return (
            <div style={cardStyle}>
                <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 8 }}>Portföy değeri (anık kayıtlar)</h2>
                <p style={{ fontSize: '0.875rem', color: tokens.textMuted }}>Yükleniyor...</p>
            </div>
        );
    }

    if (points.length === 0) {
        return (
            <div style={cardStyle}>
                <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 8 }}>Portföy değeri (anık kayıtlar)</h2>
                <p style={{ fontSize: '0.875rem', color: tokens.textMuted }}>
                    Son 30 günde portföy anlığı yok; Portföy sayfasından manuel pozisyonlarınızı kaydettiğinizde grafik oluşur.
                </p>
            </div>
        );
    }

    return (
        <div style={cardStyle}>
            <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 16 }}>Portföy değeri — son 30 gün (TRY)</h2>
            <div style={{ width: '100%', height: 260 }}>
                <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={points} margin={{ top: 8, right: 16, left: 8, bottom: 8 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} />
                        <XAxis dataKey="date" tick={{ fill: tokens.textMuted, fontSize: 11 }} />
                        <YAxis tick={{ fill: tokens.textMuted, fontSize: 11 }} tickFormatter={(v) => v.toLocaleString('tr-TR')} />
                        <Tooltip
                            contentStyle={{ background: tokens.bgCard, border: `1px solid ${tokens.border}`, borderRadius: 8 }}
                            formatter={tooltipValueFormatter}
                        />
                        <Line type="monotone" dataKey="balance" name="Portföy" stroke={tokens.accent} strokeWidth={2} dot={{ r: 3 }} />
                    </LineChart>
                </ResponsiveContainer>
            </div>
        </div>
    );
}
