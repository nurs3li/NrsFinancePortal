import { useState, useEffect, useCallback } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
import { BalanceChart } from '../components/BalanceChart';
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts';

type WhaleSummary = { level?: string; impactScore?: number; triggeredAt?: string };
type CashSummary = { amountTry?: number };
type PortfolioSummary = { totalValueTry?: number; distribution?: Record<string, number> };
type ActivitySummary = { lastTradeAt?: string };

type SummaryResponse = {
    whale?: WhaleSummary;
    cash?: CashSummary;
    portfolio?: PortfolioSummary;
    activity?: ActivitySummary;
};

const PIE_COLORS = ['#3b82f6', '#22c55e', '#eab308', '#ef4444', '#a855f7'];
const tooltipTutarFormatter = ((v: number) => ['₺' + v.toLocaleString('tr-TR', { maximumFractionDigits: 2 }), 'Tutar']) as never;

export function Dashboard() {
    const { tokens } = useTheme();
    const [summary, setSummary] = useState<SummaryResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const fetchDashboard = useCallback(() => {
        setLoading(true);
        financeClient
            .get('/api/dashboard/summary')
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                setSummary(raw);
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'Bilinmeyen hata';
                setError(msg);
            })
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        fetchDashboard();
    }, [fetchDashboard]);

    useRefetchOnFocus(fetchDashboard);
    usePolling(fetchDashboard, 60_000);

    const formatMoney = (v: number) => '₺' + v.toLocaleString('tr-TR', { maximumFractionDigits: 2 });
    const pageStyle: React.CSSProperties = {
        padding: 24,
        background: tokens.bg,
        color: tokens.text,
        minHeight: '100%',
        fontFamily: 'inherit',
    };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem', marginTop: 4 };

    if (loading) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Dashboard</h1>
                <p style={mutedStyle}>Yükleniyor...</p>
            </div>
        );
    }
    if (error) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Dashboard</h1>
                <p style={{ ...mutedStyle, color: tokens.error }}>Hata: {error}</p>
            </div>
        );
    }
    if (!summary) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Dashboard</h1>
                <p style={mutedStyle}>Özet verisi bulunamadı.</p>
            </div>
        );
    }

    const totalCash = summary.cash?.amountTry ?? 0;
    const totalPortfolio = summary.portfolio?.totalValueTry ?? 0;
    const totalBalance = totalCash + totalPortfolio;
    const distribution = summary.portfolio?.distribution ?? {};
    const pieData = Object.entries(distribution).map(([name, value]) => ({ name, value }));
    const cardStyle: React.CSSProperties = {
        padding: 16,
        borderRadius: 12,
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
    };

    return (
        <div style={pageStyle}>
            <div style={{ marginBottom: 24 }}>
                <h1 style={titleStyle}>Dashboard</h1>
                <p style={mutedStyle}>Portföy özeti, balina sinyalleri ve son aktivite.</p>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16, marginBottom: 24 }}>
                <div style={{ ...cardStyle, background: tokens.accentGradient, color: '#fff', border: 'none' }}>
                    <div style={{ fontSize: '0.8125rem', opacity: 0.9 }}>Toplam Portföy Değeri</div>
                    <div style={{ fontSize: '1.5rem', fontWeight: 700, marginTop: 8 }}>{formatMoney(totalPortfolio)}</div>
                    {summary.whale?.impactScore != null && (
                        <div style={{ fontSize: '0.75rem', marginTop: 8, opacity: 0.9 }}>Balina etki skoru: {summary.whale.impactScore}</div>
                    )}
                </div>
                <div style={cardStyle}>
                    <div style={{ fontSize: '0.8125rem', color: tokens.textMuted }}>Nakit (TRY)</div>
                    <div style={{ fontSize: '1.25rem', fontWeight: 700, marginTop: 8 }}>{formatMoney(totalCash)}</div>
                    <div style={{ fontSize: '0.75rem', marginTop: 8, color: tokens.textMuted }}>Hesaplardaki toplam nakit.</div>
                </div>
                <div style={cardStyle}>
                    <div style={{ fontSize: '0.8125rem', color: tokens.textMuted }}>Toplam Bakiye (Portföy + Nakit)</div>
                    <div style={{ fontSize: '1.25rem', fontWeight: 700, marginTop: 8 }}>{formatMoney(totalBalance)}</div>
                    {summary.activity?.lastTradeAt && (
                        <div style={{ fontSize: '0.75rem', marginTop: 8, color: tokens.textMuted }}>
                            Son işlem: {new Date(summary.activity.lastTradeAt).toLocaleString('tr-TR')}
                        </div>
                    )}
                </div>
                <div style={cardStyle}>
                    <div style={{ fontSize: '0.8125rem', color: tokens.textMuted }}>Balina Seviyesi</div>
                    <div style={{ fontSize: '1.125rem', fontWeight: 600, marginTop: 8 }}>{summary.whale?.level ?? 'Bilinmiyor'}</div>
                    {summary.whale?.triggeredAt && (
                        <div style={{ fontSize: '0.75rem', marginTop: 8, color: tokens.textMuted }}>
                            Son sinyal: {new Date(summary.whale.triggeredAt).toLocaleString('tr-TR')}
                        </div>
                    )}
                </div>
            </div>
            <BalanceChart />
            <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 2fr) minmax(0, 1.5fr)', gap: 16 }}>
                <div style={cardStyle}>
                    <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 12 }}>Portföy Dağılımı</h2>
                    {pieData.length === 0 ? (
                        <p style={{ color: tokens.textMuted, fontSize: '0.875rem' }}>Dağılım verisi yok.</p>
                    ) : (
                        <>
                            <div style={{ width: '100%', height: 220, marginBottom: 16 }}>
                                <ResponsiveContainer width="100%" height="100%">
                                    <PieChart>
                                        <Pie
                                            data={pieData}
                                            dataKey="value"
                                            nameKey="name"
                                            cx="50%"
                                            cy="50%"
                                            outerRadius="70%"
                                            label={({ name, value }) => `${name}: ${formatMoney(value)}`}
                                            labelLine={{ stroke: tokens.border }}
                                        >
                                            {pieData.map((_, i) => (
                                                <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} stroke={tokens.border} strokeWidth={1} />
                                            ))}
                                        </Pie>
                                        <Tooltip contentStyle={{ background: tokens.bgCard, border: `1px solid ${tokens.border}`, borderRadius: 8 }} formatter={tooltipTutarFormatter} />
                                        <Legend />
                                    </PieChart>
                                </ResponsiveContainer>
                            </div>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                                {Object.entries(distribution).map(([asset, value]) => {
                                    const pct = totalPortfolio > 0 ? Math.round((value / totalPortfolio) * 100) : 0;
                                    return (
                                        <div key={asset}>
                                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8125rem', marginBottom: 4 }}>
                                                <span>{asset}</span>
                                                <span style={{ color: tokens.textMuted }}>{formatMoney(value)} ({pct}%)</span>
                                            </div>
                                            <div style={{ height: 6, borderRadius: 999, background: tokens.border }}>
                                                <div style={{ height: '100%', width: `${pct}%`, borderRadius: 999, background: tokens.success }} />
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </>
                    )}
                </div>
                <div style={cardStyle}>
                    <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 8 }}>Son Aktivite & Uyarılar</h2>
                    <ul style={{ listStyle: 'none', padding: 0, margin: 0, fontSize: '0.875rem' }}>
                        {summary.activity?.lastTradeAt && (
                            <li style={{ marginBottom: 8 }}>
                                <span style={{ color: tokens.success, marginRight: 6 }}>●</span>
                                Son işlem: <span>{new Date(summary.activity.lastTradeAt).toLocaleString('tr-TR')}</span>
                            </li>
                        )}
                        {summary.whale?.level && (
                            <li style={{ marginBottom: 8 }}>
                                <span style={{ color: tokens.accent, marginRight: 6 }}>●</span>
                                Balina seviyesi: <span>{summary.whale.level}</span>
                            </li>
                        )}
                        {!summary.activity?.lastTradeAt && !summary.whale?.level && (
                            <li style={{ color: tokens.textMuted }}>Gösterilecek aktivite yok.</li>
                        )}
                    </ul>
                </div>
            </div>
        </div>
    );
}