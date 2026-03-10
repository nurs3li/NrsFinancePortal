import { useState, useEffect } from 'react';
import { metricsClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import {
    BarChart,
    Bar,
    XAxis,
    YAxis,
    Tooltip,
    Legend,
    ResponsiveContainer,
    Cell,
    PieChart,
    Pie,
    CartesianGrid,
} from 'recharts';

type TradeCountBySymbol = { symbol: string; count: number };
type WhaleCountByLevel = { level: string; count: number };

type DashboardMetricsDto = {
    totalTrades: number;
    totalWhaleAlerts: number;
    totalSuspiciousEvents: number;
    topSymbols: TradeCountBySymbol[];
    whaleByLevel: WhaleCountByLevel[];
};

type MetricsApiResponse = {
    success: boolean;
    data: DashboardMetricsDto | null;
    errors: unknown;
};

const CHART_COLORS = ['#3b82f6', '#22c55e', '#eab308', '#ef4444', '#a855f7', '#ec4899', '#06b6d4', '#f97316'];

const tooltipAdetFormatter = ((value: number) => [String(value), 'Adet']) as never;

export function AdminMetrics() {
    const { tokens } = useTheme();
    const [metrics, setMetrics] = useState<DashboardMetricsDto | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        metricsClient
            .get<MetricsApiResponse>('/api/admin/metrics/dashboard')
            .then((res) => {
                const d = res.data?.data;
                setMetrics(d ?? null);
            })
            .catch((err) => {
                setError(err.response?.data?.message ?? err.message ?? 'Metrikler yüklenemedi');
            })
            .finally(() => setLoading(false));
    }, []);

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };
    const cardStyle: React.CSSProperties = {
        padding: 20,
        borderRadius: 12,
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        marginBottom: 20,
    };
    const chartTooltipStyle = {
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        borderRadius: 8,
        padding: '10px 14px',
        fontSize: 13,
    };

    if (error) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Metrik Dashboard</h1>
                <p style={{ ...mutedStyle, color: tokens.error }}>Hata: {error}</p>
            </div>
        );
    }

    if (loading) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Metrik Dashboard</h1>
                <p style={mutedStyle}>Yükleniyor...</p>
            </div>
        );
    }

    if (!metrics) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Metrik Dashboard</h1>
                <p style={mutedStyle}>Veri yok.</p>
            </div>
        );
    }

    const summaryBarData = [
        { name: 'Toplam Trade', value: metrics.totalTrades, fill: CHART_COLORS[0] },
        { name: 'Whale Uyarıları', value: metrics.totalWhaleAlerts, fill: CHART_COLORS[1] },
        { name: 'Şüpheli Olaylar', value: metrics.totalSuspiciousEvents, fill: CHART_COLORS[2] },
    ];

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Metrik Dashboard</h1>
            <p style={{ ...mutedStyle, marginBottom: 24 }}>Trade, whale ve şüpheli olay özeti (Metrics Service).</p>

            {/* KPI kartları */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 16, marginBottom: 24 }}>
                <div style={cardStyle}>
                    <div style={mutedStyle}>Toplam Trade</div>
                    <div style={{ fontSize: '2rem', fontWeight: 700, color: tokens.accent }}>{metrics.totalTrades}</div>
                </div>
                <div style={cardStyle}>
                    <div style={mutedStyle}>Whale Uyarıları</div>
                    <div style={{ fontSize: '2rem', fontWeight: 700, color: tokens.success }}>{metrics.totalWhaleAlerts}</div>
                </div>
                <div style={cardStyle}>
                    <div style={mutedStyle}>Şüpheli Olaylar</div>
                    <div style={{ fontSize: '2rem', fontWeight: 700, color: tokens.error }}>{metrics.totalSuspiciousEvents}</div>
                </div>
            </div>

            {/* Özet karşılaştırma bar grafiği */}
            <div style={cardStyle}>
                <h2 style={{ fontSize: '1.125rem', marginBottom: 16, fontWeight: 600 }}>Özet karşılaştırma</h2>
                <div style={{ width: '100%', height: 260 }}>
                    <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={summaryBarData} margin={{ top: 16, right: 24, left: 8, bottom: 8 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} />
                            <XAxis dataKey="name" tick={{ fill: tokens.textMuted, fontSize: 12 }} />
                            <YAxis tick={{ fill: tokens.textMuted, fontSize: 12 }} />
                            <Tooltip contentStyle={chartTooltipStyle} cursor={{ fill: 'rgba(0,0,0,0.04)' }} />
                            <Legend wrapperStyle={{ fontSize: 12 }} />
                            <Bar dataKey="value" name="Adet" radius={[6, 6, 0, 0]} />
                        </BarChart>
                    </ResponsiveContainer>
                </div>
            </div>

            {/* İki sütun: Pasta grafikler */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: 20, marginBottom: 20 }}>
                {metrics.topSymbols && metrics.topSymbols.length > 0 && (
                    <div style={cardStyle}>
                        <h2 style={{ fontSize: '1.125rem', marginBottom: 16, fontWeight: 600 }}>Sembol dağılımı (pasta)</h2>
                        <div style={{ width: '100%', height: 300 }}>
                            <ResponsiveContainer width="100%" height="100%">
                                <PieChart>
                                    <Pie
                                        data={metrics.topSymbols}
                                        dataKey="count"
                                        nameKey="symbol"
                                        cx="50%"
                                        cy="50%"
                                        outerRadius="75%"
                                        label={({ name, value }) => `${name}: ${value}`}
                                        labelLine={{ stroke: tokens.border }}
                                    >
                                        {metrics.topSymbols.map((_, i) => (
                                            <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} stroke={tokens.border} strokeWidth={1} />
                                        ))}
                                    </Pie>
                                    <Tooltip contentStyle={chartTooltipStyle} formatter={tooltipAdetFormatter} />
                                    <Legend />
                                </PieChart>
                            </ResponsiveContainer>
                        </div>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem', marginTop: 12 }}>
                            <thead>
                            <tr style={{ borderBottom: `2px solid ${tokens.border}` }}>
                                <th style={{ textAlign: 'left', padding: 8 }}>Sembol</th>
                                <th style={{ textAlign: 'right', padding: 8 }}>Adet</th>
                            </tr>
                            </thead>
                            <tbody>
                            {metrics.topSymbols.map((row) => (
                                <tr key={row.symbol} style={{ borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                    <td style={{ padding: 8 }}>{row.symbol}</td>
                                    <td style={{ padding: 8, textAlign: 'right' }}>{row.count}</td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {metrics.whaleByLevel && metrics.whaleByLevel.length > 0 && (
                    <div style={cardStyle}>
                        <h2 style={{ fontSize: '1.125rem', marginBottom: 16, fontWeight: 600 }}>Whale seviye dağılımı (kullanıcı)</h2>
                        <div style={{ width: '100%', height: 300 }}>
                            <ResponsiveContainer width="100%" height="100%">
                                <PieChart>
                                    <Pie
                                        data={metrics.whaleByLevel}
                                        dataKey="count"
                                        nameKey="level"
                                        cx="50%"
                                        cy="50%"
                                        outerRadius="75%"
                                        label={({ name, value }) => `${name}: ${value}`}
                                        labelLine={{ stroke: tokens.border }}
                                    >
                                        {metrics.whaleByLevel.map((_, i) => (
                                            <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} stroke={tokens.border} strokeWidth={1} />
                                        ))}
                                    </Pie>
                                    <Tooltip contentStyle={chartTooltipStyle} formatter={((value: number) => [String(value), 'Kullanıcı']) as never} />
                                    <Legend />
                                </PieChart>
                            </ResponsiveContainer>
                        </div>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem', marginTop: 12 }}>
                            <thead>
                            <tr style={{ borderBottom: `2px solid ${tokens.border}` }}>
                                <th style={{ textAlign: 'left', padding: 8 }}>Seviye</th>
                                <th style={{ textAlign: 'right', padding: 8 }}>Kullanıcı</th>
                            </tr>
                            </thead>
                            <tbody>
                            {metrics.whaleByLevel.map((row) => (
                                <tr key={row.level} style={{ borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                    <td style={{ padding: 8 }}>{row.level}</td>
                                    <td style={{ padding: 8, textAlign: 'right' }}>{row.count}</td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {/* En çok işlem gören semboller – bar grafik */}
            {metrics.topSymbols && metrics.topSymbols.length > 0 && (
                <div style={cardStyle}>
                    <h2 style={{ fontSize: '1.125rem', marginBottom: 16, fontWeight: 600 }}>En çok işlem gören semboller</h2>
                    <div style={{ width: '100%', height: 320 }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <BarChart data={metrics.topSymbols} layout="vertical" margin={{ top: 8, right: 24, left: 80, bottom: 8 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} />
                                <XAxis type="number" tick={{ fill: tokens.textMuted, fontSize: 12 }} />
                                <YAxis type="category" dataKey="symbol" width={70} tick={{ fill: tokens.textMuted, fontSize: 12 }} />
                                <Tooltip contentStyle={chartTooltipStyle} formatter={tooltipAdetFormatter} />
                                <Bar dataKey="count" name="Adet" radius={[0, 4, 4, 0]} barSize={28}>
                                    {metrics.topSymbols.map((_, i) => (
                                        <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
                                    ))}
                                </Bar>
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem', marginTop: 12 }}>
                        <thead>
                        <tr style={{ borderBottom: `2px solid ${tokens.border}` }}>
                            <th style={{ textAlign: 'left', padding: 8 }}>Sembol</th>
                            <th style={{ textAlign: 'right', padding: 8 }}>Adet</th>
                        </tr>
                        </thead>
                        <tbody>
                        {metrics.topSymbols.map((row) => (
                            <tr key={row.symbol} style={{ borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                <td style={{ padding: 8 }}>{row.symbol}</td>
                                <td style={{ padding: 8, textAlign: 'right' }}>{row.count}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}

            {/* Whale seviyeye göre – bar grafik (kullanıcı) */}
            {metrics.whaleByLevel && metrics.whaleByLevel.length > 0 && (
                <div style={cardStyle}>
                    <h2 style={{ fontSize: '1.125rem', marginBottom: 16, fontWeight: 600 }}>Whale seviyeye göre (kullanıcı)</h2>
                    <div style={{ width: '100%', height: 260 }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <BarChart data={metrics.whaleByLevel} margin={{ top: 16, right: 24, left: 8, bottom: 8 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} />
                                <XAxis dataKey="level" tick={{ fill: tokens.textMuted, fontSize: 11 }} />
                                <YAxis tick={{ fill: tokens.textMuted, fontSize: 12 }} allowDecimals={false} />
                                <Tooltip contentStyle={chartTooltipStyle} formatter={((value: number) => [String(value), 'Kullanıcı']) as never} />
                                <Bar dataKey="count" name="Kullanıcı" radius={[6, 6, 0, 0]} fill={tokens.accent} />
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem', marginTop: 12 }}>
                        <thead>
                        <tr style={{ borderBottom: `2px solid ${tokens.border}` }}>
                            <th style={{ textAlign: 'left', padding: 8 }}>Seviye</th>
                            <th style={{ textAlign: 'right', padding: 8 }}>Kullanıcı</th>
                        </tr>
                        </thead>
                        <tbody>
                        {metrics.whaleByLevel.map((row) => (
                            <tr key={row.level} style={{ borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                <td style={{ padding: 8 }}>{row.level}</td>
                                <td style={{ padding: 8, textAlign: 'right' }}>{row.count}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}