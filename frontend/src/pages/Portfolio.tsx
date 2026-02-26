import { useState, useEffect, useCallback } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';

type AssetType = 'STOCK' | 'CRYPTO' | 'FX' | 'METAL' | 'FUND' | string;
type DashboardSummary = {
    whale?: { level: string | null; impactScore: number | null; triggeredAt: string | null } | null;
    cash?: { amountTry: number } | null;
    portfolio?: { totalValueTry: number; distribution: Record<AssetType, number> } | null;
    activity?: { lastTradeAt: string | null; todayTradeCount: number } | null;
    netWorthTry?: number | null;
};

export function Portfolio() {
    const { tokens } = useTheme();
    const [summary, setSummary] = useState<DashboardSummary | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const fetchPortfolio = useCallback(() => {
        setLoading(true);
        financeClient
            .get<DashboardSummary>('/api/dashboard/summary')
            .then((res) => {
                const raw = (res.data as { data?: DashboardSummary })?.data ?? res.data;
                setSummary(raw);
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'Bilinmeyen hata';
                setError(msg);
            })
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        fetchPortfolio();
    }, [fetchPortfolio]);

    useRefetchOnFocus(fetchPortfolio);
    usePolling(fetchPortfolio, 60_000);

    const formatMoney = (v: number) => '₺' + v.toLocaleString('tr-TR', { maximumFractionDigits: 2 });
    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem', marginTop: 4 };
    const cardStyle: React.CSSProperties = {
        padding: 16,
        borderRadius: 12,
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
    };

    if (loading) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Portföy</h1>
                <p style={mutedStyle}>Yükleniyor...</p>
            </div>
        );
    }
    if (error) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Portföy</h1>
                <p style={{ ...mutedStyle, color: tokens.error }}>Hata: {error}</p>
            </div>
        );
    }
    if (!summary) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Portföy</h1>
                <p style={mutedStyle}>Özet verisi bulunamadı.</p>
            </div>
        );
    }

    const totalCash = summary.cash?.amountTry ?? 0;
    const totalPortfolio = summary.portfolio?.totalValueTry ?? 0;
    const netWorth = summary.netWorthTry ?? totalCash + totalPortfolio;
    const distribution = summary.portfolio?.distribution ?? {};
    const todayTrades = summary.activity?.todayTradeCount ?? 0;

    return (
        <div style={pageStyle}>
            <div style={{ marginBottom: 24 }}>
                <h1 style={titleStyle}>Portföylerim</h1>
                <p style={mutedStyle}>Toplam portföy değeri, dağılım ve son işlemler.</p>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16, marginBottom: 24 }}>
                <div style={{ ...cardStyle, background: tokens.accentGradient, color: '#fff', border: 'none' }}>
                    <div style={{ fontSize: '0.8125rem', opacity: 0.9 }}>Toplam Portföy Değeri</div>
                    <div style={{ fontSize: '1.5rem', fontWeight: 700, marginTop: 8 }}>{formatMoney(totalPortfolio)}</div>
                </div>
                <div style={cardStyle}>
                    <div style={{ fontSize: '0.8125rem', color: tokens.textMuted }}>Nakit (TRY)</div>
                    <div style={{ fontSize: '1.25rem', fontWeight: 700, marginTop: 8 }}>{formatMoney(totalCash)}</div>
                </div>
                <div style={cardStyle}>
                    <div style={{ fontSize: '0.8125rem', color: tokens.textMuted }}>Toplam Servet</div>
                    <div style={{ fontSize: '1.25rem', fontWeight: 700, marginTop: 8 }}>{formatMoney(netWorth)}</div>
                </div>
                <div style={cardStyle}>
                    <div style={{ fontSize: '0.8125rem', color: tokens.textMuted }}>Bugünkü İşlem</div>
                    <div style={{ fontSize: '1.25rem', fontWeight: 700, marginTop: 8 }}>{todayTrades}</div>
                </div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.5fr) minmax(0, 2fr)', gap: 16 }}>
                <div style={cardStyle}>
                    <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 8 }}>Varlık Dağılımı</h2>
                    {Object.keys(distribution).length === 0 ? (
                        <p style={{ color: tokens.textMuted, fontSize: '0.875rem' }}>Dağılım verisi yok.</p>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                            {Object.entries(distribution).map(([asset, value]) => {
                                const valNum = Number(value);
                                const pct = totalPortfolio > 0 ? Math.round((valNum / totalPortfolio) * 100) : 0;
                                return (
                                    <div key={asset}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8125rem', marginBottom: 4 }}>
                                            <span>{asset}</span>
                                            <span style={{ color: tokens.textMuted }}>{formatMoney(valNum)} ({pct}%)</span>
                                        </div>
                                        <div style={{ height: 6, borderRadius: 999, background: tokens.border }}>
                                            <div style={{ height: '100%', width: `${pct}%`, borderRadius: 999, background: tokens.success }} />
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
                <div style={cardStyle}>
                    <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 8 }}>Varlık Listesi</h2>
                    <p style={{ color: tokens.textMuted, fontSize: '0.875rem' }}>
                        Tek tek varlıklar için backend'e holdings endpoint eklediğinde burada detaylı tabloyu gösterebiliriz.
                    </p>
                </div>
            </div>
        </div>
    );
}