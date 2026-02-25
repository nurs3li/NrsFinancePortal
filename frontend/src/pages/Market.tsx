import { useState, useEffect } from 'react';
import { marketClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';

type LatestPrice = {
    symbol?: string;
    buyPrice?: number;
    sellPrice?: number;
    price?: number;
    source?: string;
    timestamp?: string;
    status?: string;
    message?: string;
};

type HistoryPoint = {
    buyPrice: number;
    sellPrice: number;
    timestamp: string;
};

type TabId = 'doviz' | 'crypto' | 'metals' | 'funds';

const FX_SYMBOLS = ['USDTRY', 'EURTRY', 'GBPTRY'] as const;
const CHART_DAYS = 7;

export function Market() {
    const { tokens } = useTheme();
    const [activeTab, setActiveTab] = useState<TabId>('doviz');
    const [dovizLatest, setDovizLatest] = useState<Record<string, LatestPrice>>({});
    const [cryptoLatest, setCryptoLatest] = useState<Record<string, LatestPrice>>({});
    const [metalsLatest, setMetalsLatest] = useState<Record<string, LatestPrice>>({});
    const [fundsLatest, setFundsLatest] = useState<Record<string, LatestPrice>>({});
    const [chartSymbol, setChartSymbol] = useState<string>('USDTRY');
    const [chartData, setChartData] = useState<HistoryPoint[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadingChart, setLoadingChart] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        setLoading(true);
        setError(null);
        Promise.all([
            marketClient.get<Record<string, LatestPrice>>('/api/market/doviz/latest').then((r) => r.data),
            marketClient.get<Record<string, LatestPrice>>('/api/market/crypto/latest').then((r) => r.data),
            marketClient.get<Record<string, LatestPrice>>('/api/market/metals/latest').then((r) => r.data),
            marketClient.get<Record<string, LatestPrice>>('/api/market/funds/latest').then((r) => r.data),
        ])
            .then(([doviz, crypto, metals, funds]) => {
                setDovizLatest(doviz ?? {});
                setCryptoLatest(crypto ?? {});
                setMetalsLatest(metals ?? {});
                setFundsLatest(funds ?? {});
            })
            .catch((err) => setError(err.message ?? 'Veri yüklenemedi'))
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        if (activeTab !== 'doviz' || !chartSymbol) return;
        setLoadingChart(true);
        marketClient
            .get<HistoryPoint[]>(`/api/market/doviz/history`, { params: { symbol: chartSymbol, days: CHART_DAYS } })
            .then((res) => setChartData(Array.isArray(res.data) ? res.data : []))
            .catch(() => setChartData([]))
            .finally(() => setLoadingChart(false));
    }, [activeTab, chartSymbol]);

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };

    const tabs: { id: TabId; label: string }[] = [
        { id: 'doviz', label: 'Döviz' },
        { id: 'crypto', label: 'Kripto' },
        { id: 'metals', label: 'Altın' },
        { id: 'funds', label: 'Fonlar' },
    ];

    const getTableData = (): Record<string, LatestPrice> => {
        switch (activeTab) {
            case 'doviz': return dovizLatest;
            case 'crypto': return cryptoLatest;
            case 'metals': return metalsLatest;
            case 'funds': return fundsLatest;
            default: return {};
        }
    };

    const getPrice = (v: LatestPrice): number | null => {
        if (!v || v.status === 'NO_DATA') return null;
        if (v.buyPrice != null) return Number(v.buyPrice);
        if (v.sellPrice != null) return Number(v.sellPrice);
        if (v.price != null) return Number(v.price);
        return null;
    };

    const tableEntries = Object.entries(getTableData()).filter(([, v]) => v && typeof v === 'object');

    if (error) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Piyasa Verileri</h1>
                <p style={{ ...mutedStyle, color: tokens.error }}>Hata: {error}</p>
            </div>
        );
    }

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Piyasa Verileri</h1>
            <p style={mutedStyle}>Döviz, kripto, altın ve fon fiyatları — marketdata servisi</p>

            <div style={{ display: 'flex', gap: 8, marginBottom: 20, flexWrap: 'wrap' }}>
                {tabs.map((t) => (
                    <button
                        key={t.id}
                        type="button"
                        onClick={() => setActiveTab(t.id)}
                        style={{
                            padding: '8px 16px',
                            fontSize: '0.875rem',
                            fontWeight: activeTab === t.id ? 600 : 500,
                            background: activeTab === t.id ? tokens.accent : tokens.bgCard,
                            color: activeTab === t.id ? '#fff' : tokens.text,
                            border: `1px solid ${tokens.border}`,
                            borderRadius: 8,
                            cursor: 'pointer',
                        }}
                    >
                        {t.label}
                    </button>
                ))}
            </div>

            {loading ? (
                <p style={mutedStyle}>Yükleniyor...</p>
            ) : (
                <>
                    <div
                        style={{
                            marginBottom: 24,
                            padding: 16,
                            borderRadius: 12,
                            background: tokens.bgCard,
                            border: `1px solid ${tokens.border}`,
                        }}
                    >
                        <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 12 }}>Güncel Fiyatlar</h2>
                        {tableEntries.length === 0 ? (
                            <p style={mutedStyle}>Bu kategoride veri yok.</p>
                        ) : (
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9375rem' }}>
                                <thead>
                                <tr style={{ borderBottom: `2px solid ${tokens.border}` }}>
                                    <th style={{ textAlign: 'left', padding: 12 }}>Sembol</th>
                                    <th style={{ textAlign: 'right', padding: 12 }}>Alış</th>
                                    <th style={{ textAlign: 'right', padding: 12 }}>Satış</th>
                                    <th style={{ textAlign: 'left', padding: 12 }}>Kaynak / Tarih</th>
                                </tr>
                                </thead>
                                <tbody>
                                {tableEntries.map(([sym, row]) => {
                                    const buy = row.buyPrice != null ? Number(row.buyPrice) : getPrice(row);
                                    const sell = row.sellPrice != null ? Number(row.sellPrice) : null;
                                    const noData = row.status === 'NO_DATA';
                                    return (
                                        <tr key={sym} style={{ borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                            <td style={{ padding: 12 }}>{row.symbol ?? sym}</td>
                                            <td style={{ padding: 12, textAlign: 'right' }}>
                                                {noData ? '-' : (buy != null ? buy.toLocaleString('tr-TR') : '-')}
                                            </td>
                                            <td style={{ padding: 12, textAlign: 'right' }}>
                                                {noData ? '-' : (sell != null ? sell.toLocaleString('tr-TR') : buy != null ? buy.toLocaleString('tr-TR') : '-')}
                                            </td>
                                            <td style={{ padding: 12, color: tokens.textMuted, fontSize: '0.8125rem' }}>
                                                {noData ? (row.message ?? '-') : (row.source ?? '') + (row.timestamp ? ' · ' + new Date(row.timestamp).toLocaleString('tr-TR') : '')}
                                            </td>
                                        </tr>
                                    );
                                })}
                                </tbody>
                            </table>
                        )}
                    </div>

                    {activeTab === 'doviz' && (
                        <div
                            style={{
                                padding: 16,
                                borderRadius: 12,
                                background: tokens.bgCard,
                                border: `1px solid ${tokens.border}`,
                            }}
                        >
                            <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 12 }}>Döviz grafiği (son 7 gün)</h2>
                            <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
                                <label style={{ fontSize: '0.875rem' }}>
                                    Sembol:
                                    <select
                                        value={chartSymbol}
                                        onChange={(e) => setChartSymbol(e.target.value)}
                                        style={{
                                            marginLeft: 8,
                                            padding: '6px 10px',
                                            borderRadius: 8,
                                            border: `1px solid ${tokens.border}`,
                                            background: tokens.inputBg,
                                            color: tokens.text,
                                            fontSize: '0.875rem',
                                        }}
                                    >
                                        {FX_SYMBOLS.map((s) => (
                                            <option key={s} value={s}>{s}</option>
                                        ))}
                                    </select>
                                </label>
                            </div>
                            {loadingChart ? (
                                <p style={mutedStyle}>Grafik yükleniyor...</p>
                            ) : chartData.length === 0 ? (
                                <p style={mutedStyle}>Bu sembol için geçmiş veri yok.</p>
                            ) : (
                                <SimplePriceChart data={chartData} tokens={tokens} />
                            )}
                        </div>
                    )}

                    {activeTab !== 'doviz' && (
                        <div
                            style={{
                                padding: 16,
                                borderRadius: 12,
                                background: tokens.bgCard,
                                border: `1px solid ${tokens.border}`,
                            }}
                        >
                            <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 8 }}>Grafik</h2>
                            <p style={mutedStyle}>Geçmiş fiyat grafiği şu an sadece döviz (Döviz sekmesi) için mevcut.</p>
                        </div>
                    )}
                </>
            )}
        </div>
    );
}

function SimplePriceChart({
                              data,
                              tokens,
                          }: {
    data: HistoryPoint[];
    tokens: { border: string; textMuted: string; accent: string };
}) {
    const prices = data.map((p) => (Number(p.buyPrice) + Number(p.sellPrice)) / 2);
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    const range = max - min || 1;
    const height = 200;

    return (
        <div style={{ width: '100%' }}>
            <div
                style={{
                    display: 'flex',
                    alignItems: 'flex-end',
                    gap: 2,
                    height,
                    padding: '8px 0',
                }}
            >
                {data.map((p, i) => {
                    const price = (Number(p.buyPrice) + Number(p.sellPrice)) / 2;
                    const h = ((price - min) / range) * (height - 24) + 12;
                    return (
                        <div
                            key={i}
                            title={`${new Date(p.timestamp).toLocaleString('tr-TR')} — ${price.toLocaleString('tr-TR')}`}
                            style={{
                                flex: 1,
                                minWidth: 4,
                                height: h,
                                borderRadius: '4px 4px 0 0',
                                background: tokens.accent,
                            }}
                        />
                    );
                })}
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8, fontSize: '0.75rem', color: tokens.textMuted }}>
                <span>{data.length ? new Date(data[0].timestamp).toLocaleDateString('tr-TR') : ''}</span>
                <span>{data.length ? new Date(data[data.length - 1].timestamp).toLocaleDateString('tr-TR') : ''}</span>
            </div>
        </div>
    );
}