import { useEffect, useMemo, useState } from 'react';
import { financeClient, metricsClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { Activity, AlertTriangle, ShieldAlert } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

type TradeCountBySymbol = { symbol: string; count: number };
type WhaleCountByLevel = { level: string; count: number };
type DashboardMetricsDto = {
    totalTrades: number;
    totalWhaleAlerts: number;
    totalSuspiciousEvents: number;
    topSymbols: TradeCountBySymbol[];
    whaleByLevel: WhaleCountByLevel[];
};
type MetricsApiResponse = { success: boolean; data: DashboardMetricsDto | null; errors: unknown };

type SuspiciousRow = {
    userId: number;
    username: string | null;
    email: string | null;
    reason: string;
    amount: number;
    occurredAt: string;
};

const COLORS = ['#64FFDA', '#22c55e', '#38bdf8', '#8892B0', '#eab308', '#f97316', '#a855f7', '#ef4444'];

export function AdminDashboard() {
    const { tokens } = useTheme();
    const [metrics, setMetrics] = useState<DashboardMetricsDto | null>(null);
    const [recentEvents, setRecentEvents] = useState<SuspiciousRow[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        Promise.all([
            metricsClient.get<MetricsApiResponse>('/api/admin/metrics/dashboard'),
            financeClient.get('/api/admin/suspicious/events', { params: { limit: 5 } }),
        ])
            .then(([metricsRes, eventsRes]) => {
                const raw = metricsRes.data as DashboardMetricsDto | MetricsApiResponse | null | undefined;
                const m = raw && typeof raw === 'object' && 'data' in raw
                    ? (raw as MetricsApiResponse).data
                    : (raw as DashboardMetricsDto | null | undefined);
                setMetrics(m ?? null);
                const eventsRaw = eventsRes.data?.data ?? eventsRes.data;
                setRecentEvents(Array.isArray(eventsRaw) ? eventsRaw.slice(0, 5) : []);
            })
            .catch((err) => setError(err.response?.data?.message ?? err.message ?? 'Yönetim paneli verisi yüklenemedi'))
            .finally(() => setLoading(false));
    }, []);

    const pageStyle: React.CSSProperties = {
        padding: 24,
        background: '#0A192F',
        color: '#CCD6F6',
        minHeight: '100%',
        fontFamily: 'Inter, Roboto, Arial, sans-serif',
    };
    const panelStyle: React.CSSProperties = {
        background: 'rgba(17,34,64,0.92)',
        border: '1px solid rgba(136,146,176,0.35)',
        borderRadius: 8,
        boxShadow: '0 8px 22px rgba(2,12,27,0.35)',
        padding: 16,
    };
    const titleStyle: React.CSSProperties = { fontSize: '1.6rem', fontWeight: 700, marginBottom: 4, color: '#E6F1FF' };
    const mutedStyle: React.CSSProperties = { color: '#8892B0', fontSize: '0.875rem' };
    const tooltipStyle = {
        background: '#112240',
        border: '1px solid rgba(136,146,176,0.35)',
        borderRadius: 8,
        fontSize: 12,
        color: '#CCD6F6',
    };

    const kpis = useMemo(() => ([
        { label: 'Toplam Trade', value: metrics?.totalTrades ?? 0, Icon: Activity, color: '#64FFDA' },
        { label: 'Whale Uyarıları', value: metrics?.totalWhaleAlerts ?? 0, Icon: AlertTriangle, color: '#22c55e' },
        { label: 'Şüpheli Olaylar', value: metrics?.totalSuspiciousEvents ?? 0, Icon: ShieldAlert, color: '#f59e0b' },
    ]), [metrics]);

    if (loading) {
        return <div style={pageStyle}><h1 style={titleStyle}>Yönetim Paneli</h1><p style={mutedStyle}>Yükleniyor...</p></div>;
    }
    if (error) {
        return <div style={pageStyle}><h1 style={titleStyle}>Yönetim Paneli</h1><p style={{ ...mutedStyle, color: tokens.error }}>Hata: {error}</p></div>;
    }

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Yönetim Paneli</h1>
            <p style={{ ...mutedStyle, marginBottom: 18 }}>Back-office operasyonlarının canlı özeti ve hızlı aksiyon alanı.</p>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 12, marginBottom: 16 }}>
                {kpis.map((kpi) => (
                    <div key={kpi.label} style={panelStyle}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <span style={mutedStyle}>{kpi.label}</span>
                            <kpi.Icon size={16} color={kpi.color} />
                        </div>
                        <div style={{ fontSize: '1.7rem', marginTop: 8, fontWeight: 700, color: kpi.color }}>
                            {kpi.value.toLocaleString('tr-TR')}
                        </div>
                    </div>
                ))}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 }}>
                <div style={panelStyle}>
                    <h2 style={{ marginTop: 0, fontSize: '1rem', color: '#E6F1FF' }}>Sembol Dağılımı</h2>
                    <div style={{ width: '100%', height: 280 }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <PieChart>
                                <Pie data={metrics?.topSymbols ?? []} dataKey="count" nameKey="symbol" outerRadius="78%" label={({ name }) => name}>
                                    {(metrics?.topSymbols ?? []).map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                                </Pie>
                                <Tooltip contentStyle={tooltipStyle} />
                            </PieChart>
                        </ResponsiveContainer>
                    </div>
                    <div style={{ ...mutedStyle, fontSize: '0.75rem' }}>Metrikler Servisinden Canlı Veri</div>
                </div>

                <div style={panelStyle}>
                    <h2 style={{ marginTop: 0, fontSize: '1rem', color: '#E6F1FF' }}>En Çok İşlem Gören Semboller</h2>
                    <div style={{ width: '100%', height: 280 }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <BarChart data={metrics?.topSymbols ?? []} layout="vertical" margin={{ top: 8, right: 16, left: 56, bottom: 8 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(136,146,176,0.22)" />
                                <XAxis type="number" tick={{ fill: '#8892B0', fontSize: 11 }} />
                                <YAxis type="category" dataKey="symbol" tick={{ fill: '#CCD6F6', fontSize: 11 }} width={56} />
                                <Tooltip contentStyle={tooltipStyle} />
                                <Bar dataKey="count" fill="#64FFDA" radius={[0, 4, 4, 0]} />
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                    <div style={{ ...mutedStyle, fontSize: '0.75rem' }}>Metrikler Servisinden Canlı Veri</div>
                </div>
            </div>

            <div style={panelStyle}>
                <h2 style={{ marginTop: 0, fontSize: '1rem', color: '#E6F1FF' }}>Son Hareketler</h2>
                {recentEvents.length === 0 ? (
                    <p style={mutedStyle}>Kayıt bulunamadı.</p>
                ) : (
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.825rem' }}>
                        <thead>
                        <tr style={{ borderBottom: '1px solid rgba(136,146,176,0.35)' }}>
                            <th style={{ textAlign: 'left', padding: '8px 6px', color: '#8892B0' }}>Kullanıcı</th>
                            <th style={{ textAlign: 'left', padding: '8px 6px', color: '#8892B0' }}>Sebep</th>
                            <th style={{ textAlign: 'right', padding: '8px 6px', color: '#8892B0' }}>Tutar</th>
                            <th style={{ textAlign: 'right', padding: '8px 6px', color: '#8892B0' }}>Zaman</th>
                        </tr>
                        </thead>
                        <tbody>
                        {recentEvents.map((row, idx) => (
                            <tr key={`${row.userId}-${idx}`} style={{ borderBottom: '1px solid rgba(136,146,176,0.2)' }}>
                                <td style={{ padding: '8px 6px' }}>{row.email ?? row.username ?? `Kullanıcı#${row.userId}`}</td>
                                <td style={{ padding: '8px 6px' }}>{row.reason}</td>
                                <td style={{ padding: '8px 6px', textAlign: 'right' }}>₺{Number(row.amount).toLocaleString('tr-TR')}</td>
                                <td style={{ padding: '8px 6px', textAlign: 'right' }}>{new Date(row.occurredAt).toLocaleString('tr-TR')}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </div>
        </div>
    );
}