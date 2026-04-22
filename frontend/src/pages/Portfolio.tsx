import { useCallback, useEffect, useMemo, useState } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';

type AssetType = 'STOCK' | 'CRYPTO' | 'FX' | 'METAL' | 'FUND';

type UnifiedPortfolioItem = {
    source: 'TRADE' | 'MANUAL' | string;
    type: AssetType | string;
    symbol: string;
    quantity: number;
    avgBuyPrice: number;
};

type PerformanceItem = {
    source: string;
    type: string;
    symbol: string;
    quantity: number;
    avgBuyPrice: number;
    currentPrice: number;
    currentPriceCurrency?: string;
    cost: number;
    currentValue: number;
    pnl: number;
    pnlPct: number;
};

type PortfolioPerformance = {
    totalCost: number;
    totalCurrentValue: number;
    totalPnl: number;
    totalPnlPct: number;
    items: PerformanceItem[];
};

type ManualCreatePayload = {
    type: AssetType;
    symbol: string;
    quantity: number;
    buyPrice: number;
    buyDate: string;
    note?: string;
};

type MarketOverview = {
    doviz?: Record<string, { buyPrice?: number; sellPrice?: number; source?: string }>;
    metals?: Record<string, { buyPrice?: number; source?: string }>;
    crypto?: Record<string, { buyPrice?: number; source?: string }>;
    funds?: Record<string, { buyPrice?: number; source?: string }>;
    stocks?: Record<string, { buyPrice?: number; source?: string }>;
    timestamp?: string;
};

function unwrapData<T>(res: any): T {
    return (res?.data?.data ?? res?.data) as T;
}

function getOverviewKey(type: AssetType): keyof MarketOverview {
    switch (type) {
        case 'CRYPTO': return 'crypto';
        case 'FX': return 'doviz';
        case 'METAL': return 'metals';
        case 'FUND': return 'funds';
        case 'STOCK': return 'stocks';
        default: return 'crypto';
    }
}

export function Portfolio() {
    const { tokens } = useTheme();

    const [unifiedItems, setUnifiedItems] = useState<UnifiedPortfolioItem[]>([]);
    const [perf, setPerf] = useState<PortfolioPerformance | null>(null);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [savingManual, setSavingManual] = useState(false);

    const [type, setType] = useState<AssetType>('CRYPTO');
    const [symbol, setSymbol] = useState('');
    const [quantity, setQuantity] = useState('0.1');
    const [buyPrice, setBuyPrice] = useState('100000');
    const [buyDate, setBuyDate] = useState(new Date().toISOString().slice(0, 10));
    const [note, setNote] = useState('');
    const [overview, setOverview] = useState<MarketOverview | null>(null);
    const [overviewLoading, setOverviewLoading] = useState(true);

    const fetchAll = useCallback(() => {
        setLoading(true);
        setOverviewLoading(true);
        setError(null);

        Promise.all([
            financeClient.get('/api/portfolio/me/unified'),
            financeClient.get('/api/portfolio/performance/me'),
            financeClient.get('/api/market/overview'),
        ])
            .then(([uRes, pRes, oRes]) => {
                setUnifiedItems(unwrapData<UnifiedPortfolioItem[]>(uRes) ?? []);
                setPerf(unwrapData<PortfolioPerformance>(pRes));
                setOverview(unwrapData<MarketOverview>(oRes));
            })
            .catch((err) => {
                const msg =
                    err.response?.data?.errors?.error ??
                    err.response?.data?.message ??
                    err.message ??
                    'Portföy verisi alınamadı';
                setError(msg);
            })
            .finally(() => {
                setLoading(false);
                setOverviewLoading(false);
            });
    }, []);

    useEffect(() => {
        fetchAll();
    }, [fetchAll]);

    useRefetchOnFocus(fetchAll);
    usePolling(fetchAll, 60_000);

    const symbolOptions = useMemo(() => {
        if (!overview) return [] as string[];
        const key = getOverviewKey(type);
        const map = overview[key];
        if (!map || typeof map !== 'object') return [] as string[];
        return Object.keys(map).filter((k) => map[k] != null);
    }, [overview, type]);

    const perfItemMap = useMemo(() => {
        const map = new Map<string, PerformanceItem>();
        (perf?.items ?? []).forEach((item) => {
            const key = `${item.source}|${item.type}|${item.symbol}`;
            map.set(key, item);
        });
        return map;
    }, [perf]);

    useEffect(() => {
        if (symbolOptions.length === 0) {
            setSymbol('');
            return;
        }
        if (!symbolOptions.includes(symbol)) {
            setSymbol(symbolOptions[0]);
        }
    }, [symbolOptions, symbol]);

    const saveManual = async (e: React.FormEvent) => {
        e.preventDefault();

        const q = Number(quantity);
        const bp = Number(buyPrice);
        if (!q || q <= 0 || !bp || bp <= 0) {
            alert('Miktar ve alış fiyatı 0’dan büyük olmalı.');
            return;
        }

        const normalizedSymbol = symbol.trim().toUpperCase();
        if (!normalizedSymbol) {
            alert('Sembol seçmelisin veya girmelisin.');
            return;
        }

        const payload: ManualCreatePayload = {
            type,
            symbol: normalizedSymbol,
            quantity: q,
            buyPrice: bp,
            buyDate,
            note: note?.trim() || undefined,
        };

        try {
            setSavingManual(true);
            await financeClient.post('/api/portfolio/manual', payload);
            await fetchAll();
        } catch (err: any) {
            alert(
                err?.response?.data?.errors?.error ??
                    err?.response?.data?.message ??
                    err?.message ??
                    'Manuel pozisyon kaydedilemedi'
            );
        } finally {
            setSavingManual(false);
        }
    };

    const fmtMoney = (v: number) => `₺${Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}`;

    const pageStyle: React.CSSProperties = {
        padding: 24,
        background: tokens.bg,
        color: tokens.text,
        minHeight: '100%',
    };

    const cardStyle: React.CSSProperties = {
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        borderRadius: 12,
        padding: 16,
    };

    const inputStyle: React.CSSProperties = {
        width: '100%',
        padding: 8,
        borderRadius: 8,
        border: `1px solid ${tokens.border}`,
        background: tokens.inputBg,
        color: tokens.text,
        fontSize: '0.9375rem',
    };

    if (loading) {
        return (
            <div style={pageStyle}>
                <h1 style={{ fontSize: '1.75rem', fontWeight: 700 }}>Portföy</h1>
                <p style={{ color: tokens.textMuted }}>Yükleniyor...</p>
            </div>
        );
    }

    if (error) {
        return (
            <div style={pageStyle}>
                <h1 style={{ fontSize: '1.75rem', fontWeight: 700 }}>Portföy</h1>
                <p style={{ color: tokens.error }}>Hata: {error}</p>
            </div>
        );
    }

    return (
        <div style={pageStyle}>
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: 6 }}>Portföylerim</h1>
            <p style={{ color: tokens.textMuted, fontSize: '0.875rem', marginBottom: 16 }}>
                Trade + manuel giriş birleşik görünüm ve performans özeti.
            </p>

            <div
                style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                    gap: 12,
                    marginBottom: 16,
                }}
            >
                <div style={{ ...cardStyle, background: tokens.accentGradient, color: '#fff', border: 'none' }}>
                    <div style={{ fontSize: '0.8125rem', opacity: 0.9 }}>Toplam Maliyet</div>
                    <div style={{ fontSize: '1.25rem', fontWeight: 700, marginTop: 6 }}>
                        {fmtMoney(perf?.totalCost ?? 0)}
                    </div>
                </div>
                <div style={cardStyle}>
                    <div style={{ fontSize: '0.8125rem', color: tokens.textMuted }}>Güncel Değer</div>
                    <div style={{ fontSize: '1.25rem', fontWeight: 700, marginTop: 6 }}>
                        {fmtMoney(perf?.totalCurrentValue ?? 0)}
                    </div>
                </div>
                <div style={cardStyle}>
                    <div style={{ fontSize: '0.8125rem', color: tokens.textMuted }}>Toplam PNL</div>
                    <div
                        style={{
                            fontSize: '1.25rem',
                            fontWeight: 700,
                            marginTop: 6,
                            color: (perf?.totalPnl ?? 0) >= 0 ? '#22c55e' : '#ef4444',
                        }}
                    >
                        {fmtMoney(perf?.totalPnl ?? 0)}
                    </div>
                </div>
                <div style={cardStyle}>
                    <div style={{ fontSize: '0.8125rem', color: tokens.textMuted }}>Toplam PNL %</div>
                    <div
                        style={{
                            fontSize: '1.25rem',
                            fontWeight: 700,
                            marginTop: 6,
                            color: (perf?.totalPnlPct ?? 0) >= 0 ? '#22c55e' : '#ef4444',
                        }}
                    >
                        {Number(perf?.totalPnlPct ?? 0).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%
                    </div>
                </div>
            </div>

            <div
                style={{
                    display: 'grid',
                    gridTemplateColumns: 'minmax(0, 1.7fr) minmax(0, 1fr)',
                    gap: 16,
                }}
            >
                <div style={cardStyle}>
                    <h2 style={{ marginTop: 0, marginBottom: 12, fontSize: '1rem' }}>Birleşik Varlık Listesi</h2>

                    <div style={{ overflowX: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                            <thead>
                                <tr>
                                    <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Source</th>
                                    <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Type</th>
                                    <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Symbol</th>
                                    <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Quantity</th>
                                    <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Avg Buy</th>
                                    <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Current Price</th>
                                </tr>
                            </thead>
                            <tbody>
                                {unifiedItems.length === 0 ? (
                                    <tr>
                                        <td colSpan={6} style={{ padding: 10, color: tokens.textMuted, textAlign: 'center' }}>
                                            Portföyde varlık yok.
                                        </td>
                                    </tr>
                                ) : (
                                    unifiedItems.map((row, i) => {
                                        const perfKey = `${row.source}|${row.type}|${row.symbol}`;
                                        const rowPerf = perfItemMap.get(perfKey);
                                        const priceCurrency = rowPerf?.currentPriceCurrency ?? 'TRY';
                                        return (
                                        <tr key={`${row.source}-${row.symbol}-${i}`}>
                                            <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>{row.source}</td>
                                            <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>{row.type}</td>
                                            <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>{row.symbol}</td>
                                            <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}`, textAlign: 'right' }}>
                                                {Number(row.quantity).toLocaleString('tr-TR', { maximumFractionDigits: 8 })}
                                            </td>
                                            <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}`, textAlign: 'right' }}>
                                                {fmtMoney(row.avgBuyPrice)}
                                            </td>
                                            <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}`, textAlign: 'right' }}>
                                                {rowPerf
                                                    ? `${fmtMoney(Number(rowPerf.currentPrice ?? 0))} (${priceCurrency})`
                                                    : '-'}
                                            </td>
                                        </tr>
                                    )})
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>

                <div style={cardStyle}>
                    <h2 style={{ marginTop: 0, marginBottom: 12, fontSize: '1rem' }}>Manuel Pozisyon Ekle</h2>

                    <form onSubmit={saveManual} style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                        <label style={{ fontSize: '0.875rem' }}>
                            Type
                            <select value={type} onChange={(e) => setType(e.target.value as AssetType)} style={inputStyle}>
                                <option value="CRYPTO">CRYPTO</option>
                                <option value="FX">FX</option>
                                <option value="METAL">METAL</option>
                                <option value="FUND">FUND</option>
                                <option value="STOCK">STOCK</option>
                            </select>
                        </label>

                        <label style={{ fontSize: '0.875rem' }}>
                            Symbol
                            {loading || overviewLoading ? (
                                <div style={{ ...inputStyle, color: tokens.textMuted }}>Yükleniyor...</div>
                            ) : symbolOptions.length === 0 ? (
                                <div style={{ ...inputStyle, color: tokens.textMuted }}>Bu tür için sembol yok.</div>
                            ) : (
                                <select value={symbol} onChange={(e) => setSymbol(e.target.value)} style={inputStyle}>
                                    {symbolOptions.map((opt) => (
                                        <option key={opt} value={opt}>
                                            {opt}
                                        </option>
                                    ))}
                                </select>
                            )}
                        </label>

                        <label style={{ fontSize: '0.875rem' }}>
                            Quantity
                            <input type="number" step="0.00000001" value={quantity} onChange={(e) => setQuantity(e.target.value)} style={inputStyle} />
                        </label>

                        <label style={{ fontSize: '0.875rem' }}>
                            Buy Price (TRY)
                            <input type="number" step="0.00000001" value={buyPrice} onChange={(e) => setBuyPrice(e.target.value)} style={inputStyle} />
                        </label>

                        <label style={{ fontSize: '0.875rem' }}>
                            Buy Date
                            <input type="date" value={buyDate} onChange={(e) => setBuyDate(e.target.value)} style={inputStyle} />
                        </label>

                        <label style={{ fontSize: '0.875rem' }}>
                            Note
                            <textarea
                                rows={3}
                                value={note}
                                onChange={(e) => setNote(e.target.value)}
                                style={{ ...inputStyle, resize: 'vertical' }}
                            />
                        </label>

                        <button
                            type="submit"
                            disabled={savingManual}
                            style={{
                                marginTop: 4,
                                padding: '8px 14px',
                                borderRadius: 8,
                                border: 'none',
                                background: tokens.accentGradient,
                                color: '#fff',
                                fontWeight: 600,
                                cursor: savingManual ? 'default' : 'pointer',
                                opacity: savingManual ? 0.7 : 1,
                            }}
                        >
                            {savingManual ? 'Kaydediliyor...' : 'Pozisyon Ekle'}
                        </button>
                    </form>
                </div>
            </div>
        </div>
    );
}