import { useState, useEffect, useCallback } from 'react';
import { marketClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Legend } from 'recharts';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';

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

const DAYS_OPTIONS = [7, 14, 30];
const COMPARE_COLORS = ['#3b82f6', '#22c55e', '#eab308', '#ef4444'];

const tooltipFiyatFormatter = ((value: number) => [value != null ? value.toLocaleString('tr-TR') : '-', 'Fiyat']) as never;

function getHistoryUrl(tab: TabId): string {
    switch (tab) {
        case 'doviz': return '/api/market/doviz/history';
        case 'crypto': return '/api/market/crypto/history';
        case 'metals': return '/api/market/metals/history';
        case 'funds': return '/api/market/funds/history';
        default: return '/api/market/doviz/history';
    }
}

export function Market() {
    const { tokens } = useTheme();
    const [activeTab, setActiveTab] = useState<TabId>('doviz');
    const [dovizLatest, setDovizLatest] = useState<Record<string, LatestPrice>>({});
    const [cryptoLatest, setCryptoLatest] = useState<Record<string, LatestPrice>>({});
    const [metalsLatest, setMetalsLatest] = useState<Record<string, LatestPrice>>({});
    const [fundsLatest, setFundsLatest] = useState<Record<string, LatestPrice>>({});
    const [chartSymbol, setChartSymbol] = useState<string>('USDTRY');
    const [chartDays, setChartDays] = useState(7);
    const [chartData, setChartData] = useState<HistoryPoint[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadingChart, setLoadingChart] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const [compareSymbols, setCompareSymbols] = useState<string[]>([]);
    const [compareDays, setCompareDays] = useState(7);
    const [compareCategory, setCompareCategory] = useState<TabId>('doviz');
    const [compareData, setCompareData] = useState<{ date: string; [key: string]: number | string }[]>([]);
    const [loadingCompare, setLoadingCompare] = useState(false);

    const refetchLatest = useCallback(() => {
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
        refetchLatest();
    }, [refetchLatest]);

    useRefetchOnFocus(refetchLatest);
    usePolling(refetchLatest, 60_000);

    const fetchChart = useCallback((tab: TabId, symbol: string, days: number) => {
        return marketClient
            .get<HistoryPoint[]>(getHistoryUrl(tab), { params: { symbol, days } })
            .then((res) => Array.isArray(res.data) ? res.data : []);
    }, []);

    useEffect(() => {
        if (!chartSymbol) return;
        setLoadingChart(true);
        fetchChart(activeTab, chartSymbol, chartDays)
            .then(setChartData)
            .catch(() => setChartData([]))
            .finally(() => setLoadingChart(false));
    }, [activeTab, chartSymbol, chartDays, fetchChart]);

    const getSymbolsForTab = (tab: TabId): string[] => {
        const map: Record<TabId, Record<string, LatestPrice>> = {
            doviz: dovizLatest,
            crypto: cryptoLatest,
            metals: metalsLatest,
            funds: fundsLatest,
        };
        const data = map[tab];
        if (!data) return [];
        return Object.keys(data).filter((k) => data[k] && typeof data[k] === 'object' && data[k].status !== 'NO_DATA');
    };

    const loadCompare = useCallback(() => {
        if (compareSymbols.length < 2) {
            setCompareData([]);
            return;
        }
        setLoadingCompare(true);
        const url = getHistoryUrl(compareCategory);
        Promise.all(
            compareSymbols.slice(0, 4).map((sym) =>
                marketClient.get<HistoryPoint[]>(url, { params: { symbol: sym, days: compareDays } })
                    .then((r) => ({ symbol: sym, data: Array.isArray(r.data) ? r.data : [] }))
            )
        )
            .then((results) => {
                const byDate: Record<string, Record<string, number>> = {};
                results.forEach(({ symbol, data }) => {
                    data.forEach((p) => {
                        const t = new Date(p.timestamp).toISOString().slice(0, 10);
                        if (!byDate[t]) byDate[t] = {};
                        byDate[t][symbol] = (Number(p.buyPrice) + Number(p.sellPrice)) / 2;
                    });
                });
                const dates = Object.keys(byDate).sort();
                if (dates.length === 0) {
                    setCompareData([]);
                    return;
                }
                const first: Record<string, number> = {};
                compareSymbols.forEach((sym) => {
                    const d = dates.find((d) => byDate[d][sym] != null);
                    if (d != null) first[sym] = byDate[d][sym];
                });
                const out = dates.map((date) => {
                    const row: { date: string; [key: string]: number | string } = {
                        date: new Date(date).toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit' }),
                    };
                    compareSymbols.forEach((sym) => {
                        const v = byDate[date][sym];
                        const base = first[sym];
                        row[sym] = base && v != null ? Math.round((v / base) * 1000) / 10 : 0;
                    });
                    return row;
                });
                setCompareData(out);
            })
            .catch(() => setCompareData([]))
            .finally(() => setLoadingCompare(false));
    }, [compareSymbols, compareDays, compareCategory]);

    useEffect(() => {
        if (compareSymbols.length >= 2) loadCompare();
        else setCompareData([]);
    }, [compareSymbols, compareDays, compareCategory, loadCompare]);

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };
    const cardStyle: React.CSSProperties = {
        padding: 16,
        borderRadius: 12,
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        marginBottom: 16,
    };

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
    const symbolsForTab = getSymbolsForTab(activeTab);
    const symbolsForCompare = getSymbolsForTab(compareCategory);

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
            <p style={mutedStyle}>Döviz, kripto, altın ve fon fiyatları — tüm veriler için grafik ve karşılaştırma.</p>

            <div style={{ display: 'flex', gap: 8, marginBottom: 20, flexWrap: 'wrap' }}>
                {tabs.map((t) => (
                    <button
                        key={t.id}
                        type="button"
                        onClick={() => {
                            setActiveTab(t.id);
                            const syms = getSymbolsForTab(t.id);
                            setChartSymbol(syms[0] ?? '');
                        }}
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
                    <div style={cardStyle}>
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
                                        <tr key={sym} style={{ borderBottom: `1px solid ${tokens.border}` }}>
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

                    <div style={cardStyle}>
                        <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 12 }}>
                            {activeTab === 'doviz' && 'Döviz grafiği'}
                            {activeTab === 'crypto' && 'Kripto grafiği'}
                            {activeTab === 'metals' && 'Altın grafiği'}
                            {activeTab === 'funds' && 'Fon grafiği'}
                        </h2>
                        <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
                            <label style={{ fontSize: '0.875rem' }}>
                                Sembol:
                                <select
                                    value={chartSymbol}
                                    onChange={(e) => setChartSymbol(e.target.value)}
                                    style={{ marginLeft: 8, padding: '6px 10px', borderRadius: 8, border: `1px solid ${tokens.border}`, background: (tokens as { inputBg?: string }).inputBg ?? tokens.bgCard, color: tokens.text, fontSize: '0.875rem' }}
                                >
                                    {symbolsForTab.map((s) => (
                                        <option key={s} value={s}>{s}</option>
                                    ))}
                                </select>
                            </label>
                            <label style={{ fontSize: '0.875rem' }}>
                                Dönem:
                                <select
                                    value={chartDays}
                                    onChange={(e) => setChartDays(Number(e.target.value))}
                                    style={{ marginLeft: 8, padding: '6px 10px', borderRadius: 8, border: `1px solid ${tokens.border}`, background: (tokens as { inputBg?: string }).inputBg ?? tokens.bgCard, color: tokens.text, fontSize: '0.875rem' }}
                                >
                                    {DAYS_OPTIONS.map((d) => (
                                        <option key={d} value={d}>Son {d} gün</option>
                                    ))}
                                </select>
                            </label>
                        </div>
                        {loadingChart ? (
                            <p style={mutedStyle}>Grafik yükleniyor...</p>
                        ) : chartData.length === 0 ? (
                            <p style={mutedStyle}>Bu sembol için geçmiş veri yok.</p>
                        ) : (
                            <div style={{ width: '100%', height: 280 }}>
                                <ResponsiveContainer width="100%" height="100%">
                                    <LineChart
                                        data={chartData.map((p) => ({
                                            tarih: new Date(p.timestamp).toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit' }),
                                            fiyat: (Number(p.buyPrice) + Number(p.sellPrice)) / 2,
                                        }))}
                                        margin={{ top: 8, right: 16, left: 8, bottom: 8 }}
                                    >
                                        <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} />
                                        <XAxis dataKey="tarih" tick={{ fill: tokens.textMuted, fontSize: 11 }} />
                                        <YAxis tick={{ fill: tokens.textMuted, fontSize: 11 }} tickFormatter={(v) => v.toLocaleString('tr-TR')} />
                                        <Tooltip contentStyle={{ background: tokens.bgCard, border: `1px solid ${tokens.border}`, borderRadius: 8 }} formatter={tooltipFiyatFormatter} />
                                        <Line type="monotone" dataKey="fiyat" name="Fiyat" stroke={tokens.accent} strokeWidth={2} dot={{ r: 3 }} />
                                    </LineChart>
                                </ResponsiveContainer>
                            </div>
                        )}
                    </div>

                    <div style={cardStyle}>
                        <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 8 }}>Karşılaştırma (performans, baz 100)</h2>
                        <p style={{ ...mutedStyle, marginBottom: 12 }}>Aynı kategoriden 2–4 sembol seçin; ilk gün 100 kabul edilir.</p>
                        <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
                            <label style={{ fontSize: '0.875rem' }}>
                                Kategori:
                                <select
                                    value={compareCategory}
                                    onChange={(e) => { setCompareCategory(e.target.value as TabId); setCompareSymbols([]); }}
                                    style={{ marginLeft: 8, padding: '6px 10px', borderRadius: 8, border: `1px solid ${tokens.border}`, background: (tokens as { inputBg?: string }).inputBg ?? tokens.bgCard, color: tokens.text }}
                                >
                                    {tabs.map((t) => (
                                        <option key={t.id} value={t.id}>{t.label}</option>
                                    ))}
                                </select>
                            </label>
                            <label style={{ fontSize: '0.875rem' }}>
                                Dönem:
                                <select value={compareDays} onChange={(e) => setCompareDays(Number(e.target.value))} style={{ marginLeft: 8, padding: '6px 10px', borderRadius: 8, border: `1px solid ${tokens.border}`, background: (tokens as { inputBg?: string }).inputBg ?? tokens.bgCard, color: tokens.text }}>
                                    {DAYS_OPTIONS.map((d) => (
                                        <option key={d} value={d}>Son {d} gün</option>
                                    ))}
                                </select>
                            </label>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                                <span style={{ fontSize: '0.875rem' }}>Semboller:</span>
                                {symbolsForCompare.slice(0, 12).map((sym) => (
                                    <label key={sym} style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: '0.8125rem' }}>
                                        <input
                                            type="checkbox"
                                            checked={compareSymbols.includes(sym)}
                                            onChange={(e) => {
                                                if (e.target.checked) {
                                                    setCompareSymbols((prev) => (prev.length >= 4 ? prev : [...prev, sym]));
                                                } else {
                                                    setCompareSymbols((prev) => prev.filter((s) => s !== sym));
                                                }
                                            }}
                                        />
                                        {sym}
                                    </label>
                                ))}
                            </div>
                        </div>
                        {loadingCompare ? (
                            <p style={mutedStyle}>Karşılaştırma yükleniyor...</p>
                        ) : compareData.length === 0 ? (
                            <p style={mutedStyle}>En az 2 sembol seçin.</p>
                        ) : (
                            <div style={{ width: '100%', height: 300 }}>
                                <ResponsiveContainer width="100%" height="100%">
                                    <LineChart data={compareData} margin={{ top: 8, right: 16, left: 8, bottom: 8 }}>
                                        <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} />
                                        <XAxis dataKey="date" tick={{ fill: tokens.textMuted, fontSize: 11 }} />
                                        <YAxis tick={{ fill: tokens.textMuted, fontSize: 11 }} tickFormatter={(v) => String(v)} />
                                        <Tooltip contentStyle={{ background: tokens.bgCard, border: `1px solid ${tokens.border}`, borderRadius: 8 }} />
                                        <Legend />
                                        {compareSymbols.slice(0, 4).map((sym, i) => (
                                            <Line key={sym} type="monotone" dataKey={sym} name={sym} stroke={COMPARE_COLORS[i % COMPARE_COLORS.length]} strokeWidth={2} dot={false} />
                                        ))}
                                    </LineChart>
                                </ResponsiveContainer>
                            </div>
                        )}
                    </div>
                </>
            )}
        </div>
    );
}