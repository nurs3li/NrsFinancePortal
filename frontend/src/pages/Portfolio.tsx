import { useCallback, useEffect, useMemo, useState, type CSSProperties } from 'react';
import {
    Bar,
    BarChart,
    CartesianGrid,
    Cell,
    Pie,
    PieChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
import { ChevronDown, Info, Pencil, Trash2, TrendingDown, TrendingUp } from 'lucide-react';
import './TerminalPages.css';

type AssetType = 'STOCK' | 'CRYPTO' | 'FX' | 'METAL' | 'FUND';

type UnifiedPortfolioItem = {
    source: 'TRADE' | 'MANUAL' | string;
    type: AssetType | string;
    symbol: string;
    quantity: number;
    avgBuyPrice: number;
    manualPositionId?: number | null;
    manualBuyDate?: string | null;
    manualNote?: string | null;
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
    manualPositionId?: number | null;
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

function rowPerfKey(row: UnifiedPortfolioItem): string {
    if (row.source === 'MANUAL' && row.manualPositionId != null) {
        return `MANUAL-${row.manualPositionId}`;
    }
    return `${row.source}|${row.type}|${row.symbol}`;
}

function perfItemKey(item: PerformanceItem): string {
    if (item.source === 'MANUAL' && item.manualPositionId != null) {
        return `MANUAL-${item.manualPositionId}`;
    }
    return `${item.source}|${item.type}|${item.symbol}`;
}

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

const ASSET_TYPE_LABEL_TR: Record<string, string> = {
    FX: 'Döviz',
    CRYPTO: 'Kripto',
    FUND: 'Fon',
    STOCK: 'Hisse',
    METAL: 'Metal',
};

const PIE_PALETTE = ['#22c55e', '#f59e0b', '#818cf8', '#06b6d4', '#ec4899', '#94a3b8'];
const PNL_POSITIVE = '#22c55e';
const PNL_NEGATIVE = '#ef4444';

type DistMode = 'COMBINED' | 'TRADE' | 'MANUAL';

function aggregateValueByAssetType(
    items: PerformanceItem[] | undefined,
    mode: DistMode
): { name: string; value: number }[] {
    if (!items?.length) return [];
    const filtered = items.filter((it) => {
        const src = String(it.source ?? '').toUpperCase();
        if (mode === 'COMBINED') return true;
        if (mode === 'MANUAL') return src === 'MANUAL';
        return src !== 'MANUAL';
    });
    const map = new Map<string, number>();
    for (const it of filtered) {
        const t = String(it.type ?? 'OTHER').toUpperCase();
        const add = Number(it.currentValue ?? 0);
        if (!Number.isFinite(add) || add <= 0) continue;
        map.set(t, (map.get(t) ?? 0) + add);
    }
    return [...map.entries()]
        .map(([type, value]) => ({
            name: ASSET_TYPE_LABEL_TR[type] ?? type,
            value,
        }))
        .sort((a, b) => b.value - a.value);
}

type PnlMode = 'COMBINED' | 'TRADE' | 'MANUAL';

function aggregatePnlByAsset(
    items: PerformanceItem[] | undefined,
    mode: PnlMode
): { label: string; pnl: number; pnlPct: number }[] {
    if (!items?.length) return [];
    const filtered = items.filter((it) => {
        const src = String(it.source ?? '').toUpperCase();
        if (mode === 'COMBINED') return true;
        if (mode === 'MANUAL') return src === 'MANUAL';
        return src !== 'MANUAL';
    });

    const map = new Map<string, { pnl: number; cost: number }>();
    for (const it of filtered) {
        const key = String(it.symbol ?? '-').toUpperCase();
        const pnl = Number(it.pnl ?? 0);
        const cost = Number(it.cost ?? 0);
        const prev = map.get(key) ?? { pnl: 0, cost: 0 };
        map.set(key, {
            pnl: prev.pnl + (Number.isFinite(pnl) ? pnl : 0),
            cost: prev.cost + (Number.isFinite(cost) ? cost : 0),
        });
    }

    return [...map.entries()]
        .map(([label, v]) => ({
            label,
            pnl: v.pnl,
            pnlPct: v.cost > 0 ? (v.pnl / v.cost) * 100 : 0,
        }))
        .sort((a, b) => Math.abs(b.pnl) - Math.abs(a.pnl))
        .slice(0, 12);
}

export function Portfolio() {
    const { tokens } = useTheme();
    const [infoOpen, setInfoOpen] = useState(false);

    const [unifiedItems, setUnifiedItems] = useState<UnifiedPortfolioItem[]>([]);
    const [perf, setPerf] = useState<PortfolioPerformance | null>(null);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [savingManual, setSavingManual] = useState(false);
    const [editingManualId, setEditingManualId] = useState<number | null>(null);

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
            map.set(perfItemKey(item), item);
        });
        return map;
    }, [perf]);

    const distributionCombined = useMemo(
        () => aggregateValueByAssetType(perf?.items, 'COMBINED'),
        [perf]
    );
    const distributionTrade = useMemo(
        () => aggregateValueByAssetType(perf?.items, 'TRADE'),
        [perf]
    );
    const distributionManual = useMemo(
        () => aggregateValueByAssetType(perf?.items, 'MANUAL'),
        [perf]
    );
    const pnlCombined = useMemo(() => aggregatePnlByAsset(perf?.items, 'COMBINED'), [perf]);
    const pnlTrade = useMemo(() => aggregatePnlByAsset(perf?.items, 'TRADE'), [perf]);
    const pnlManual = useMemo(() => aggregatePnlByAsset(perf?.items, 'MANUAL'), [perf]);

    useEffect(() => {
        if (symbolOptions.length === 0) {
            setSymbol('');
            return;
        }
        if (!symbolOptions.includes(symbol)) {
            setSymbol(symbolOptions[0]);
        }
    }, [symbolOptions, symbol]);

    const resetManualForm = () => {
        setEditingManualId(null);
        setQuantity('0.1');
        setBuyPrice('100000');
        setBuyDate(new Date().toISOString().slice(0, 10));
        setNote('');
    };

    const saveManual = async (e: React.FormEvent) => {
        e.preventDefault();

        const q = Number(quantity);
        const bp = Number(buyPrice);
        if (!q || q <= 0 || !bp || bp <= 0) {
            alert('Miktar ve alış fiyatı sıfırdan büyük olmalı.');
            return;
        }

        const normalizedSymbol = symbol.trim().toUpperCase();
        if (!normalizedSymbol) {
            alert('Lütfen sembol seçin veya girin.');
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
            if (editingManualId != null) {
                await financeClient.put(`/api/portfolio/manual/${editingManualId}`, payload);
            } else {
                await financeClient.post('/api/portfolio/manual', payload);
            }
            resetManualForm();
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

    const startEditManual = (row: UnifiedPortfolioItem) => {
        if (row.source !== 'MANUAL' || row.manualPositionId == null) return;
        setEditingManualId(row.manualPositionId);
        setType((row.type as AssetType) ?? 'CRYPTO');
        setSymbol(String(row.symbol ?? '').trim());
        setQuantity(String(row.quantity ?? ''));
        setBuyPrice(String(row.avgBuyPrice ?? ''));
        const d = row.manualBuyDate;
        setBuyDate(typeof d === 'string' && d.length >= 10 ? d.slice(0, 10) : new Date().toISOString().slice(0, 10));
        setNote(row.manualNote ?? '');
    };

    const deleteManual = async (id: number) => {
        if (!window.confirm('Bu manuel pozisyonu silmek istediğinize emin misiniz?')) return;
        try {
            await financeClient.delete(`/api/portfolio/manual/${id}`);
            if (editingManualId === id) resetManualForm();
            await fetchAll();
        } catch (err: any) {
            alert(
                err?.response?.data?.errors?.error ??
                    err?.response?.data?.message ??
                    err?.message ??
                    'Pozisyon silinemedi'
            );
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
        <div
            style={
                {
                    ...pageStyle,
                    '--tp-bg': '#0a192f',
                    '--tp-card': tokens.bgCard,
                    '--tp-border': tokens.border,
                    '--tp-text': tokens.text,
                    '--tp-muted': tokens.textMuted,
                    '--tp-success': '#22c55e',
                    '--tp-danger': '#ef4444',
                } as CSSProperties
            }
            className="terminal-pages-root"
        >
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: 6 }}>Portföylerim</h1>
            <p style={{ color: tokens.textMuted, fontSize: '0.875rem', marginBottom: 8 }}>
                Trade + manuel giriş birleşik görünüm ve performans özeti.
            </p>
            <div className="tp-info-banner" style={{ marginBottom: 16 }}>
                <div className="tp-info-head" onClick={() => setInfoOpen((v) => !v)}>
                    <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <Info size={16} />
                        <strong style={{ fontSize: '0.86rem' }}>Portföy Okuma Rehberi</strong>
                    </div>
                    <ChevronDown size={16} style={{ transform: infoOpen ? 'rotate(180deg)' : 'rotate(0deg)', transition: 'transform 150ms ease' }} />
                </div>
                {infoOpen ? (
                    <p style={{ margin: '8px 0 0', fontSize: '0.8125rem', color: tokens.textMuted, lineHeight: 1.45 }}>
                        Üç halka grafik, dağılımı birleşik/trade/manuel olarak ayrı gösterir. Bar grafikler sembol bazında PnL etkisini
                        verir; yeşil kar, kırmızı zarar. Tabloda sayısal alanlar sağ hizalı ve monospaced font ile gösterilir.
                    </p>
                ) : null}
            </div>

            <div className="tp-summary-grid" style={{ marginBottom: 16 }}>
                <div className="tp-card tp-summary-card" style={{ ...cardStyle, background: tokens.accentGradient, color: '#fff', border: 'none' }}>
                    <div style={{ fontSize: '0.8125rem', opacity: 0.9 }}>Toplam Maliyet</div>
                    <div className="tp-mono" style={{ fontSize: '1.25rem', fontWeight: 700, marginTop: 6 }}>
                        {fmtMoney(perf?.totalCost ?? 0)}
                    </div>
                </div>
                <div className="tp-card tp-summary-card" style={cardStyle}>
                    <div className="tp-label">Güncel Değer</div>
                    <div className="tp-value tp-mono" style={{ marginTop: 6 }}>
                        {fmtMoney(perf?.totalCurrentValue ?? 0)}
                    </div>
                </div>
                <div className="tp-card tp-summary-card" style={cardStyle}>
                    <div className="tp-label">Toplam kar (TRY)</div>
                    <div
                        style={{
                            fontSize: '1.25rem',
                            fontWeight: 700,
                            marginTop: 6,
                            color: (perf?.totalPnl ?? 0) >= 0 ? '#22c55e' : '#ef4444',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 6,
                        }}
                        className="tp-mono"
                    >
                        {(perf?.totalPnl ?? 0) >= 0 ? <TrendingUp size={16} /> : <TrendingDown size={16} />}
                        {fmtMoney(perf?.totalPnl ?? 0)}
                    </div>
                </div>
                <div className="tp-card tp-summary-card" style={cardStyle}>
                    <div className="tp-label">Toplam PNL %</div>
                    <div
                        style={{
                            fontSize: '1.25rem',
                            fontWeight: 700,
                            marginTop: 6,
                            color: (perf?.totalPnlPct ?? 0) >= 0 ? '#22c55e' : '#ef4444',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 6,
                        }}
                        className="tp-mono"
                    >
                        {(perf?.totalPnlPct ?? 0) >= 0 ? <TrendingUp size={16} /> : <TrendingDown size={16} />}
                        {Number(perf?.totalPnlPct ?? 0).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%
                    </div>
                </div>
            </div>

            <div style={{ ...cardStyle, marginBottom: 16 }}>
                <h2 style={{ margin: '0 0 6px 0', fontSize: '1rem' }}>Portföy dağılımı (TRY)</h2>
                <p style={{ margin: '0 0 14px 0', fontSize: '0.8125rem', color: tokens.textMuted }}>
                    Güncel değer üzerinden varlık sınıfı (döviz, kripto, fon, …) oranları — üç ayrı görünüm.
                </p>
                <div
                    style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
                        gap: 14,
                    }}
                >
                    {(
                        [
                            {
                                key: 'combined',
                                title: 'Birleşik',
                                subtitle: 'Trade + manuel pozisyonlar',
                                data: distributionCombined,
                            },
                            {
                                key: 'trade',
                                title: 'Sadece trade',
                                subtitle: 'Borsa / işlem hesabındaki satırlar',
                                data: distributionTrade,
                            },
                            {
                                key: 'manual',
                                title: 'Sadece manuel',
                                subtitle: 'Manuel eklediğiniz pozisyonlar',
                                data: distributionManual,
                            },
                        ] as const
                    ).map(({ key, title, subtitle, data }) => {
                        const totalVal = data.reduce((s, d) => s + d.value, 0);
                        return (
                            <div
                                key={key}
                                style={{
                                    border: `1px solid ${tokens.border}`,
                                    borderRadius: 10,
                                    padding: 12,
                                    background: tokens.inputBg,
                                }}
                            >
                                <div style={{ fontSize: '0.9375rem', fontWeight: 700, marginBottom: 2 }}>{title}</div>
                                <div style={{ fontSize: '0.72rem', color: tokens.textMuted, marginBottom: 8 }}>
                                    {subtitle}
                                </div>
                                {totalVal <= 0 ? (
                                    <p style={{ margin: 0, fontSize: '0.8125rem', color: tokens.textMuted }}>
                                        Bu görünümde pozisyon yok.
                                    </p>
                                ) : (
                                    <>
                                        <div style={{ position: 'relative', width: '100%', height: 220 }}>
                                            <ResponsiveContainer width="100%" height="100%">
                                                <PieChart margin={{ top: 4, right: 4, left: 4, bottom: 4 }}>
                                                    <Pie
                                                        data={data}
                                                        dataKey="value"
                                                        nameKey="name"
                                                        cx="50%"
                                                        cy="48%"
                                                        innerRadius="46%"
                                                        outerRadius="72%"
                                                        paddingAngle={1.5}
                                                    >
                                                        {data.map((_, i) => (
                                                            <Cell
                                                                key={`${key}-cell-${i}`}
                                                                fill={PIE_PALETTE[i % PIE_PALETTE.length]}
                                                                stroke={tokens.bgCard}
                                                                strokeWidth={1}
                                                            />
                                                        ))}
                                                    </Pie>
                                                    <Tooltip
                                                        formatter={(v: number | undefined) =>
                                                            v == null ? '' : fmtMoney(v)
                                                        }
                                                    />
                                                </PieChart>
                                            </ResponsiveContainer>
                                            <div
                                                style={{
                                                    position: 'absolute',
                                                    left: '50%',
                                                    top: '44%',
                                                    transform: 'translate(-50%, -50%)',
                                                    fontSize: '0.8125rem',
                                                    fontWeight: 700,
                                                    color: tokens.textMuted,
                                                    pointerEvents: 'none',
                                                }}
                                            >
                                                TRY
                                            </div>
                                        </div>
                                        <div
                                            style={{
                                                marginTop: 8,
                                                fontSize: '0.75rem',
                                                color: tokens.textMuted,
                                                display: 'flex',
                                                flexDirection: 'column',
                                                gap: 4,
                                            }}
                                        >
                                            {data.map((d, i) => {
                                                const pct = totalVal > 0 ? (d.value / totalVal) * 100 : 0;
                                                return (
                                                    <div
                                                        key={`${key}-leg-${d.name}`}
                                                        style={{ display: 'flex', alignItems: 'center', gap: 8 }}
                                                    >
                                                        <span
                                                            style={{
                                                                width: 8,
                                                                height: 8,
                                                                borderRadius: 2,
                                                                background: PIE_PALETTE[i % PIE_PALETTE.length],
                                                                flexShrink: 0,
                                                            }}
                                                        />
                                                        <span style={{ flex: 1, color: tokens.text }}>
                                                            {d.name}
                                                        </span>
                                                        <span style={{ fontVariantNumeric: 'tabular-nums' }}>
                                                            {pct.toLocaleString('tr-TR', { maximumFractionDigits: 1 })}%
                                                        </span>
                                                        <span style={{ fontVariantNumeric: 'tabular-nums' }}>
                                                            {fmtMoney(d.value)}
                                                        </span>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                        <div
                                            style={{
                                                marginTop: 8,
                                                paddingTop: 8,
                                                borderTop: `1px solid ${tokens.border}`,
                                                fontSize: '0.75rem',
                                                color: tokens.textMuted,
                                            }}
                                        >
                                            Toplam güncel değer:{' '}
                                            <strong style={{ color: tokens.text }}>{fmtMoney(totalVal)}</strong>
                                        </div>
                                    </>
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>

            <div style={{ ...cardStyle, marginBottom: 16 }}>
                <h2 style={{ margin: '0 0 8px 0', fontSize: '1rem' }}>Varlık bazlı kar / zarar (anlık)</h2>
                <p style={{ margin: '0 0 12px 0', fontSize: '0.75rem', color: tokens.textMuted }}>
                    Sembollerinize göre güncel PnL dağılımı. Yeşil barlar karı, kırmızı barlar zararı gösterir.
                </p>
                <div
                    style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                        gap: 14,
                    }}
                >
                    {(
                        [
                            { key: 'pnl-combined', title: 'Birleşik', data: pnlCombined },
                            { key: 'pnl-trade', title: 'Sadece trade', data: pnlTrade },
                            { key: 'pnl-manual', title: 'Sadece manuel', data: pnlManual },
                        ] as const
                    ).map(({ key, title, data }) => (
                        <div
                            key={key}
                            style={{
                                border: `1px solid ${tokens.border}`,
                                borderRadius: 10,
                                padding: 12,
                                background: tokens.inputBg,
                            }}
                        >
                            <div style={{ fontSize: '0.9375rem', fontWeight: 700, marginBottom: 8 }}>{title}</div>
                            {data.length === 0 ? (
                                <p style={{ margin: 0, fontSize: '0.8125rem', color: tokens.textMuted }}>
                                    Bu görünümde varlık bulunamadı.
                                </p>
                            ) : (
                                <>
                                    <div style={{ width: '100%', height: 240 }}>
                                        <ResponsiveContainer width="100%" height="100%">
                                            <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 24 }}>
                                                <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} />
                                                <XAxis
                                                    dataKey="label"
                                                    tick={{ fontSize: 10, fill: tokens.textMuted }}
                                                    angle={-20}
                                                    textAnchor="end"
                                                    height={46}
                                                />
                                                <YAxis
                                                    tick={{ fontSize: 10, fill: tokens.textMuted }}
                                                    tickFormatter={(v) =>
                                                        Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 0 })
                                                    }
                                                />
                                                <Tooltip
                                                    formatter={(v: number | undefined, _name, payload) => {
                                                        if (v == null) return '';
                                                        const pct = Number(payload?.payload?.pnlPct ?? 0);
                                                        return [
                                                            `${fmtMoney(v)} (${pct.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%)`,
                                                            'PnL',
                                                        ];
                                                    }}
                                                />
                                                <Bar dataKey="pnl" radius={[4, 4, 0, 0]}>
                                                    {data.map((r) => (
                                                        <Cell
                                                            key={`${key}-${r.label}`}
                                                            fill={r.pnl >= 0 ? PNL_POSITIVE : PNL_NEGATIVE}
                                                        />
                                                    ))}
                                                </Bar>
                                            </BarChart>
                                        </ResponsiveContainer>
                                    </div>
                                    <div style={{ marginTop: 8, fontSize: '0.75rem', color: tokens.textMuted }}>
                                        En fazla hareket gösteren ilk {data.length} sembol listelenir.
                                    </div>
                                </>
                            )}
                        </div>
                    ))}
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
                        <table className="tp-table">
                            <thead>
                                <tr>
                                    <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Kaynak</th>
                                    <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Tür</th>
                                    <th style={{ textAlign: 'left', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Sembol</th>
                                    <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Miktar</th>
                                    <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Ort. alış</th>
                                    <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>Güncel fiyat</th>
                                    <th style={{ textAlign: 'right', padding: 8, borderBottom: `2px solid ${tokens.border}` }}>İşlem</th>
                                </tr>
                            </thead>
                            <tbody>
                                {unifiedItems.length === 0 ? (
                                    <tr>
                                        <td colSpan={7} style={{ padding: 10, color: tokens.textMuted, textAlign: 'center' }}>
                                            Portföyde varlık yok.
                                        </td>
                                    </tr>
                                ) : (
                                    unifiedItems.map((row, i) => {
                                        const perfKey = rowPerfKey(row);
                                        const rowPerf = perfItemMap.get(perfKey);
                                        const priceCurrency = rowPerf?.currentPriceCurrency ?? 'TRY';
                                        const btnStyle: CSSProperties = {
                                            padding: '4px 8px',
                                            fontSize: '0.75rem',
                                            borderRadius: 6,
                                            border: `1px solid ${tokens.border}`,
                                            background: tokens.bgCard,
                                            color: tokens.text,
                                            cursor: 'pointer',
                                            marginLeft: 4,
                                        };
                                        return (
                                        <tr key={`${perfKey}-${i}`} className="tp-table-row-hover">
                                            <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>
                                                <span className={`tp-badge ${row.source === 'MANUAL' ? 'tp-badge-manual' : 'tp-badge-trade'}`}>
                                                    {row.source}
                                                </span>
                                            </td>
                                            <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>{row.type}</td>
                                            <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}` }}>{row.symbol}</td>
                                            <td className="tp-mono" style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}`, textAlign: 'right' }}>
                                                {Number(row.quantity).toLocaleString('tr-TR', { maximumFractionDigits: 8 })}
                                            </td>
                                            <td className="tp-mono" style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}`, textAlign: 'right' }}>
                                                {fmtMoney(row.avgBuyPrice)}
                                            </td>
                                            <td className="tp-mono" style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}`, textAlign: 'right' }}>
                                                {rowPerf
                                                    ? `${fmtMoney(Number(rowPerf.currentPrice ?? 0))} (${priceCurrency})`
                                                    : '-'}
                                            </td>
                                            <td style={{ padding: 8, borderBottom: `1px solid ${tokens.tableBorder}`, textAlign: 'right', whiteSpace: 'nowrap' }}>
                                                {row.source === 'MANUAL' && row.manualPositionId != null ? (
                                                    <>
                                                        <button type="button" className="tp-icon-btn" style={btnStyle} onClick={() => startEditManual(row)} title="Düzenle">
                                                            <Pencil size={14} />
                                                        </button>
                                                        <button
                                                            type="button"
                                                            className="tp-icon-btn tp-icon-btn-danger"
                                                            style={{ ...btnStyle, color: '#b91c1c', borderColor: '#fecaca' }}
                                                            onClick={() => deleteManual(row.manualPositionId!)}
                                                            title="Sil"
                                                        >
                                                            <Trash2 size={14} />
                                                        </button>
                                                    </>
                                                ) : (
                                                    <span style={{ color: tokens.textMuted }}>—</span>
                                                )}
                                            </td>
                                        </tr>
                                    )})
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>

                <div style={cardStyle}>
                    <h2 style={{ marginTop: 0, marginBottom: 12, fontSize: '1rem' }}>
                        {editingManualId != null ? 'Manuel pozisyonu düzenle' : 'Manuel pozisyon ekle'}
                    </h2>

                    <form onSubmit={saveManual} style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                        <label style={{ fontSize: '0.875rem' }}>
                            Tür
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
                            Miktar
                            <input type="number" step="0.00000001" value={quantity} onChange={(e) => setQuantity(e.target.value)} style={inputStyle} />
                        </label>

                        <label style={{ fontSize: '0.875rem' }}>
                            Alış fiyatı (TRY)
                            <input type="number" step="0.00000001" value={buyPrice} onChange={(e) => setBuyPrice(e.target.value)} style={inputStyle} />
                        </label>

                        <label style={{ fontSize: '0.875rem' }}>
                            Alış tarihi
                            <input type="date" value={buyDate} onChange={(e) => setBuyDate(e.target.value)} style={inputStyle} />
                        </label>

                        <label style={{ fontSize: '0.875rem' }}>
                            Not
                            <textarea
                                rows={3}
                                value={note}
                                onChange={(e) => setNote(e.target.value)}
                                style={{ ...inputStyle, resize: 'vertical' }}
                            />
                        </label>

                        <div style={{ display: 'flex', gap: 8, marginTop: 4, flexWrap: 'wrap' }}>
                            <button
                                type="submit"
                                disabled={savingManual}
                                style={{
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
                                {savingManual ? 'Kaydediliyor...' : editingManualId != null ? 'Güncelle' : 'Pozisyon ekle'}
                            </button>
                            {editingManualId != null ? (
                                <button
                                    type="button"
                                    onClick={resetManualForm}
                                    disabled={savingManual}
                                    style={{
                                        padding: '8px 14px',
                                        borderRadius: 8,
                                        border: `1px solid ${tokens.border}`,
                                        background: tokens.bgCard,
                                        color: tokens.text,
                                        fontWeight: 600,
                                        cursor: savingManual ? 'default' : 'pointer',
                                    }}
                                >
                                    İptal
                                </button>
                            ) : null}
                        </div>
                    </form>
                </div>
            </div>
        </div>
    );
}