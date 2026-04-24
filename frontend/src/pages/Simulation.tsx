import { useEffect, useMemo, useState } from 'react';
import {
    CartesianGrid,
    Legend,
    Line,
    LineChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';

type AssetType = 'CRYPTO' | 'FX' | 'FUND' | 'METAL' | 'STOCK';

type SimulationPerformancePoint = {
    date: string;
    priceTry: number;
    cumulativeReturnPct: number;
};

type SimulationResponse = {
    type: string;
    symbol: string;
    buyDate: string | null;
    inputAmountTry: number;
    historicalPriceTry: number;
    currentPriceTry: number;
    unitsBought: number;
    currentValueTry: number;
    pnlTry: number;
    pnlPct: number;
    buyPriceSource: 'SYSTEM_HISTORY' | 'USER_INPUT' | string;
    historicalPriceDate: string | null;
    performanceSeries: SimulationPerformancePoint[];
    message: string;
};

type SimulationResultItem = {
    id: string;
    assetName: string;
    assetType: AssetType;
    initialAmount: number;
    buyPrice: number;
    buyDate: string;
    currentPrice: number;
    pnl: number;
    pnlPct: number;
    currentValue: number;
    series: SimulationPerformancePoint[];
    visible: boolean;
    message: string;
};

type SortMode = 'LATEST' | 'PNL_DESC' | 'PNL_ASC' | 'PNL_PCT_DESC' | 'PNL_PCT_ASC' | 'NAME_ASC';
type BuyPriceMode = 'SYSTEM' | 'MANUAL';

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

const LINE_COLORS = ['#c0c0c0', '#d9d9d9', '#aeb4c4', '#94a3b8', '#e2e8f0', '#f8fafc', '#22d3ee', '#7dd3fc'];

export function Simulation() {
    const { tokens } = useTheme();
    const [type, setType] = useState<AssetType>('CRYPTO');
    const [symbol, setSymbol] = useState('BTCUSDT');
    const [amount, setAmount] = useState('5000');
    const [buyDate, setBuyDate] = useState(new Date().toISOString().slice(0, 10));
    const [buyPriceMode, setBuyPriceMode] = useState<BuyPriceMode>('SYSTEM');
    const [manualBuyPrice, setManualBuyPrice] = useState('');

    const [overview, setOverview] = useState<MarketOverview | null>(null);
    const [overviewLoading, setOverviewLoading] = useState(true);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [simulationResults, setSimulationResults] = useState<SimulationResultItem[]>([]);
    const [sortMode, setSortMode] = useState<SortMode>('LATEST');

    useEffect(() => {
        setOverviewLoading(true);
        financeClient
            .get('/api/market/overview')
            .then((res) => setOverview(unwrapData<MarketOverview>(res)))
            .catch(() => setOverview(null))
            .finally(() => setOverviewLoading(false));
    }, []);

    const symbolOptions = useMemo(() => {
        if (!overview) return [] as string[];
        const key = getOverviewKey(type);
        const map = overview[key];
        if (!map || typeof map !== 'object') return [] as string[];
        return Object.keys(map).filter((k) => map[k] != null);
    }, [overview, type]);

    useEffect(() => {
        if (symbolOptions.length > 0 && !symbolOptions.includes(symbol)) {
            setSymbol(symbolOptions[0]);
        } else if (symbolOptions.length === 0) {
            setSymbol('');
        }
    }, [symbolOptions, symbol]);

    const calculateSimulationFromService = async (): Promise<SimulationResultItem> => {
        const parsedAmount = Number(amount);
        if (!parsedAmount || parsedAmount <= 0) {
            throw new Error('Tutar sıfırdan büyük olmalı.');
        }
        if (!symbol.trim()) {
            throw new Error('Lütfen bir sembol seçin.');
        }
        if (!buyDate) {
            throw new Error('Lütfen alım tarihi girin.');
        }

        const parsedManualBuyPrice = Number(manualBuyPrice);
        if (buyPriceMode === 'MANUAL' && (!parsedManualBuyPrice || parsedManualBuyPrice <= 0)) {
            throw new Error('Manuel alış fiyatı sıfırdan büyük olmalı.');
        }

        const res = await financeClient.get('/api/simulation', {
            params: {
                type,
                symbol: symbol.trim().toUpperCase(),
                amount: parsedAmount,
                date: buyDate,
                buyPrice: buyPriceMode === 'MANUAL' ? parsedManualBuyPrice : undefined,
            },
        });
        const dto = unwrapData<SimulationResponse>(res);
        return {
            id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
            assetName: dto.symbol,
            assetType: dto.type as AssetType,
            initialAmount: Number(dto.inputAmountTry ?? 0),
            buyPrice: Number(dto.historicalPriceTry ?? 0),
            buyDate: dto.buyDate ?? buyDate,
            currentPrice: Number(dto.currentPriceTry ?? 0),
            pnl: Number(dto.pnlTry ?? 0),
            pnlPct: Number(dto.pnlPct ?? 0),
            currentValue: Number(dto.currentValueTry ?? 0),
            series: (dto.performanceSeries ?? []).map((p) => ({
                date: p.date,
                priceTry: Number(p.priceTry ?? 0),
                cumulativeReturnPct: Number(p.cumulativeReturnPct ?? 0),
            })),
            visible: true,
            message: dto.message ?? '',
        };
    };

    const addSimulationResult = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);
        try {
            setLoading(true);
            const next = await calculateSimulationFromService();
            setSimulationResults((prev) => [next, ...prev]);
        } catch (err: any) {
            const msg =
                err?.response?.data?.errors?.error ??
                err?.response?.data?.message ??
                err?.message ??
                'Simülasyon hatası';
            setError(msg);
        } finally {
            setLoading(false);
        }
    };

    const visibleResults = useMemo(
        () => simulationResults.filter((r) => r.visible),
        [simulationResults]
    );

    const chartData = useMemo(() => {
        const dateMap = new Map<string, Record<string, number | string>>();
        visibleResults.forEach((res) => {
            const key = `${res.assetType}-${res.assetName}-${res.id.slice(-4)}`;
            res.series.forEach((point) => {
                const row = dateMap.get(point.date) ?? { date: point.date };
                row[key] = point.cumulativeReturnPct;
                dateMap.set(point.date, row);
            });
        });
        return [...dateMap.values()].sort((a, b) => String(a.date).localeCompare(String(b.date)));
    }, [visibleResults]);

    const displayedResults = useMemo(() => {
        const arr = [...simulationResults];
        switch (sortMode) {
            case 'PNL_DESC':
                return arr.sort((a, b) => b.pnl - a.pnl);
            case 'PNL_ASC':
                return arr.sort((a, b) => a.pnl - b.pnl);
            case 'PNL_PCT_DESC':
                return arr.sort((a, b) => b.pnlPct - a.pnlPct);
            case 'PNL_PCT_ASC':
                return arr.sort((a, b) => a.pnlPct - b.pnlPct);
            case 'NAME_ASC':
                return arr.sort((a, b) => a.assetName.localeCompare(b.assetName, 'tr-TR'));
            case 'LATEST':
            default:
                return arr;
        }
    }, [simulationResults, sortMode]);

    const setAllVisible = (visible: boolean) => {
        setSimulationResults((prev) => prev.map((x) => ({ ...x, visible })));
    };

    const exportCsv = () => {
        if (simulationResults.length === 0) return;
        const headers = [
            'id',
            'assetType',
            'assetName',
            'buyDate',
            'initialAmountTRY',
            'buyPriceTRY',
            'currentPriceTRY',
            'currentValueTRY',
            'pnlTRY',
            'pnlPct',
            'visible',
        ];
        const esc = (v: string | number | boolean) => `"${String(v).replaceAll('"', '""')}"`;
        const lines = displayedResults.map((r) =>
            [
                r.id,
                r.assetType,
                r.assetName,
                r.buyDate,
                r.initialAmount,
                r.buyPrice,
                r.currentPrice,
                r.currentValue,
                r.pnl,
                r.pnlPct,
                r.visible,
            ].map(esc).join(',')
        );
        const csv = [headers.join(','), ...lines].join('\n');
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `simulation-results-${new Date().toISOString().slice(0, 19).replaceAll(':', '-')}.csv`;
        a.click();
        URL.revokeObjectURL(url);
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

    return (
        <div style={pageStyle}>
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: 6 }}>Portföy Analiz Aracı</h1>
            <p style={{ color: tokens.textMuted, fontSize: '0.875rem', marginBottom: 16 }}>
                Çoklu simülasyon ekle, varlıkları karşılaştır, kümülatif getiri eğrilerini aynı grafikte takip et.
            </p>

            <div
                style={{
                    ...cardStyle,
                    marginBottom: 16,
                    position: 'sticky',
                    top: 12,
                    zIndex: 3,
                    boxShadow: '0 6px 20px rgba(2, 6, 23, 0.35)',
                    background: 'linear-gradient(180deg, rgba(3,10,30,0.96) 0%, rgba(9,18,40,0.96) 100%)',
                }}
            >
                <form
                    onSubmit={addSimulationResult}
                    style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))',
                        gap: 12,
                        alignItems: 'end',
                    }}
                >
                    <label style={{ fontSize: '0.875rem' }}>
                        Varlık Türü
                        <select value={type} onChange={(e) => setType(e.target.value as AssetType)} style={inputStyle}>
                            <option value="CRYPTO">CRYPTO</option>
                            <option value="FX">FX</option>
                            <option value="METAL">METAL</option>
                            <option value="FUND">FUND</option>
                            <option value="STOCK">STOCK</option>
                        </select>
                    </label>

                    <label style={{ fontSize: '0.875rem' }}>
                        Sembol
                        {overviewLoading ? (
                            <div style={{ ...inputStyle, color: tokens.textMuted }}>Yükleniyor...</div>
                        ) : symbolOptions.length > 0 ? (
                            <select value={symbol} onChange={(e) => setSymbol(e.target.value)} style={inputStyle}>
                                {symbolOptions.map((s) => <option key={s} value={s}>{s}</option>)}
                            </select>
                        ) : (
                            <input value={symbol} onChange={(e) => setSymbol(e.target.value)} style={inputStyle} />
                        )}
                    </label>

                    <label style={{ fontSize: '0.875rem' }}>
                        Başlangıç Tutarı (TRY)
                        <input type="number" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} style={inputStyle} />
                    </label>

                    <label style={{ fontSize: '0.875rem' }}>
                        Alım Tarihi
                        <input type="date" value={buyDate} onChange={(e) => setBuyDate(e.target.value)} style={inputStyle} />
                    </label>

                    <label style={{ fontSize: '0.875rem' }}>
                        Alış Fiyat Kaynağı
                        <select value={buyPriceMode} onChange={(e) => setBuyPriceMode(e.target.value as BuyPriceMode)} style={inputStyle}>
                            <option value="SYSTEM">Sistem Geçmiş Fiyatı</option>
                            <option value="MANUAL">Kullanıcı Manuel Fiyatı</option>
                        </select>
                    </label>

                    {buyPriceMode === 'MANUAL' ? (
                        <label style={{ fontSize: '0.875rem' }}>
                            Manuel Alış Fiyatı (TRY / birim)
                            <input
                                type="number"
                                step="0.00000001"
                                value={manualBuyPrice}
                                onChange={(e) => setManualBuyPrice(e.target.value)}
                                style={inputStyle}
                                placeholder="Örn: 1250.75"
                            />
                        </label>
                    ) : null}

                    <button
                        type="submit"
                        disabled={loading}
                        style={{
                            padding: '10px 16px',
                            borderRadius: 10,
                            border: 'none',
                            background: 'linear-gradient(90deg,#0ea5e9,#2563eb)',
                            color: '#fff',
                            fontWeight: 700,
                            cursor: loading ? 'default' : 'pointer',
                            opacity: loading ? 0.7 : 1,
                            minHeight: 40,
                        }}
                    >
                        {loading ? 'Ekleniyor...' : 'Simüle Et ve Listeye Ekle'}
                    </button>
                </form>
            </div>

            {error && <div style={{ ...cardStyle, borderColor: tokens.error, color: tokens.error, marginBottom: 16 }}>Hata: {error}</div>}

            <div style={{ ...cardStyle, marginBottom: 16 }}>
                <h2 style={{ marginTop: 0, marginBottom: 10, fontSize: '1rem' }}>Karşılaştırmalı Performans Grafiği</h2>
                <div style={{ color: tokens.textMuted, fontSize: '0.8125rem', marginBottom: 12 }}>
                    Kümülatif getiri (%) — parlak lacivert/silver tema
                </div>
                {visibleResults.length === 0 || chartData.length === 0 ? (
                    <p style={{ color: tokens.textMuted, margin: 0 }}>Grafikte göstermek için en az bir simülasyon ekleyip görünür yap.</p>
                ) : (
                    <div style={{ width: '100%', height: 360, background: 'linear-gradient(180deg,#071429 0%,#0b1733 100%)', borderRadius: 10, padding: 8 }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart data={chartData} margin={{ top: 10, right: 16, left: 0, bottom: 8 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                                <XAxis dataKey="date" tick={{ fill: '#cbd5e1', fontSize: 11 }} />
                                <YAxis tick={{ fill: '#cbd5e1', fontSize: 11 }} tickFormatter={(v) => `${Number(v).toFixed(1)}%`} />
                                <Tooltip
                                    formatter={(v: number | string | undefined) =>
                                        v == null ? '' : `${Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%`
                                    }
                                />
                                <Legend />
                                {visibleResults.map((res, i) => {
                                    const key = `${res.assetType}-${res.assetName}-${res.id.slice(-4)}`;
                                    return (
                                        <Line
                                            key={res.id}
                                            type="monotone"
                                            dataKey={key}
                                            name={`${res.assetName} (${res.assetType})`}
                                            stroke={LINE_COLORS[i % LINE_COLORS.length]}
                                            strokeWidth={2}
                                            dot={false}
                                        />
                                    );
                                })}
                            </LineChart>
                        </ResponsiveContainer>
                    </div>
                )}
            </div>

            <div style={cardStyle}>
                <div
                    style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        gap: 8,
                        flexWrap: 'wrap',
                        marginBottom: 12,
                    }}
                >
                    <h2 style={{ margin: 0, fontSize: '1rem' }}>Simülasyon Listesi</h2>
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                        <select
                            value={sortMode}
                            onChange={(e) => setSortMode(e.target.value as SortMode)}
                            style={{ ...inputStyle, width: 210, padding: '6px 8px', fontSize: '0.8125rem' }}
                        >
                            <option value="LATEST">Sıralama: En Yeni</option>
                            <option value="PNL_DESC">Sıralama: En Yüksek Getiri (₺)</option>
                            <option value="PNL_ASC">Sıralama: En Kötü Getiri (₺)</option>
                            <option value="PNL_PCT_DESC">Sıralama: En Yüksek Getiri (%)</option>
                            <option value="PNL_PCT_ASC">Sıralama: En Kötü Getiri (%)</option>
                            <option value="NAME_ASC">Sıralama: Sembol (A-Z)</option>
                        </select>
                        <button
                            type="button"
                            onClick={() => setAllVisible(true)}
                            style={{ borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text, padding: '6px 10px', cursor: 'pointer' }}
                        >
                            Tümünü Göster
                        </button>
                        <button
                            type="button"
                            onClick={() => setAllVisible(false)}
                            style={{ borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text, padding: '6px 10px', cursor: 'pointer' }}
                        >
                            Tümünü Gizle
                        </button>
                        <button
                            type="button"
                            onClick={exportCsv}
                            style={{ borderRadius: 8, border: 'none', background: 'linear-gradient(90deg,#0ea5e9,#2563eb)', color: '#fff', padding: '6px 10px', cursor: 'pointer', fontWeight: 700 }}
                        >
                            CSV Export
                        </button>
                    </div>
                </div>
                <div style={{ overflowX: 'auto' }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                        <thead>
                            <tr>
                                <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Görünür</th>
                                <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Varlık</th>
                                <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Başlangıç</th>
                                <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Alış</th>
                                <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Güncel</th>
                                <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>PNL</th>
                                <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>İşlem</th>
                            </tr>
                        </thead>
                        <tbody>
                            {displayedResults.length === 0 ? (
                                <tr>
                                    <td colSpan={7} style={{ padding: 10, color: tokens.textMuted, textAlign: 'center' }}>
                                        Henüz simülasyon yok.
                                    </td>
                                </tr>
                            ) : (
                                displayedResults.map((r) => (
                                    <tr key={r.id}>
                                        <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                            <button
                                                type="button"
                                                onClick={() => setSimulationResults((prev) => prev.map((x) => x.id === r.id ? { ...x, visible: !x.visible } : x))}
                                                style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: tokens.text, fontSize: '1rem' }}
                                                title={r.visible ? 'Grafikten gizle' : 'Grafikte göster'}
                                            >
                                                {r.visible ? '👁' : '🙈'}
                                            </button>
                                        </td>
                                        <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                            <div>{r.assetName}</div>
                                            <div style={{ color: tokens.textMuted, fontSize: '0.75rem' }}>{r.assetType} • {new Date(r.buyDate).toLocaleDateString('tr-TR')}</div>
                                        </td>
                                        <td style={{ padding: 8, textAlign: 'right', borderBottom: `1px solid ${tokens.tableBorder}` }}>{fmtMoney(r.initialAmount)}</td>
                                        <td style={{ padding: 8, textAlign: 'right', borderBottom: `1px solid ${tokens.tableBorder}` }}>{fmtMoney(r.buyPrice)}</td>
                                        <td style={{ padding: 8, textAlign: 'right', borderBottom: `1px solid ${tokens.tableBorder}` }}>{fmtMoney(r.currentPrice)}</td>
                                        <td style={{ padding: 8, textAlign: 'right', borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                            <span style={{ color: r.pnl >= 0 ? '#22c55e' : '#ef4444', fontWeight: 700 }}>
                                                {fmtMoney(r.pnl)} ({r.pnlPct.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%)
                                            </span>
                                        </td>
                                        <td style={{ padding: 8, textAlign: 'right', borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                            <button
                                                type="button"
                                                onClick={() => setSimulationResults((prev) => prev.filter((x) => x.id !== r.id))}
                                                style={{ borderRadius: 6, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: '#ef4444', padding: '4px 8px', cursor: 'pointer' }}
                                            >
                                                Sil
                                            </button>
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
