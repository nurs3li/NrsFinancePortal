import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type FormEvent } from 'react';
import {
    Area,
    AreaChart,
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
import type { LatestPriceRow, MarketDashboard } from '../components/market/marketTypes';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
import {
    Building2,
    ChevronDown,
    ChevronUp,
    Coins,
    Gem,
    Info,
    Pencil,
    PieChart as PieChartIcon,
    Trash2,
    TrendingDown,
    TrendingUp,
    Wallet,
} from 'lucide-react';
import './TerminalPages.css';
import './Portfolio.css';
import { useLanguage } from '../i18n/LanguageContext';

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

type MarketOverview = {
    doviz?: Record<string, { buyPrice?: number; sellPrice?: number; source?: string }>;
    metals?: Record<string, { buyPrice?: number; source?: string }>;
    crypto?: Record<string, { buyPrice?: number; source?: string }>;
    funds?: Record<string, { buyPrice?: number; source?: string }>;
    stocks?: Record<string, { buyPrice?: number; source?: string }>;
    timestamp?: string;
};

type DistRow = { name: string; typeKey: string; value: number };

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

function unwrapData<T>(res: any): T {
    return (res?.data?.data ?? res?.data) as T;
}

function normalizeSymbolKey(symbol: string): string {
    return String(symbol ?? '')
        .trim()
        .replace(/\s+/g, '')
        .toUpperCase();
}

function dashboardAssetClassForPortfolioType(t: AssetType): string {
    if (t === 'STOCK') return 'STOCK';
    if (t === 'METAL') return 'METAL';
    if (t === 'FUND') return 'FUND';
    if (t === 'FX') return 'FX';
    return 'CRYPTO';
}

function getDashboardLatestBucket(t: AssetType): keyof MarketDashboard['latest'] {
    switch (t) {
        case 'STOCK':
            return 'stocks';
        case 'CRYPTO':
            return 'crypto';
        case 'FX':
            return 'doviz';
        case 'METAL':
            return 'metals';
        case 'FUND':
            return 'funds';
        default:
            return 'crypto';
    }
}

function pickDashboardPrice(row: LatestPriceRow | null | undefined): number {
    if (!row || row.status === 'NO_DATA') return 0;
    return Number(row.buyPrice ?? row.buy ?? row.price ?? row.sellPrice ?? row.sell ?? 0);
}

function findHeatmapTileFor(dashboard: MarketDashboard, symbol: string, assetClass: string) {
    const key = normalizeSymbolKey(symbol);
    return dashboard.heatmapTiles.find((tile) => normalizeSymbolKey(tile.symbol) === key && tile.assetClass === assetClass) ?? null;
}

function sparklineClosesForPortfolio(dashboard: MarketDashboard | null, symbol: string, assetClass: string): number[] {
    if (!dashboard) return [];
    const key = normalizeSymbolKey(symbol);
    const exact = dashboard.sparklines.find((s) => normalizeSymbolKey(s.symbol) === key && s.assetClass === assetClass);
    if (exact?.closes?.length) return exact.closes;
    const loose = dashboard.sparklines.find((s) => normalizeSymbolKey(s.symbol) === key);
    return loose?.closes ?? [];
}

function computeChangePct(closes: number[]): number {
    if (!Array.isArray(closes) || closes.length < 2) return 0;
    const first = Number(closes[0] ?? 0);
    const last = Number(closes[closes.length - 1] ?? 0);
    if (!first) return 0;
    return ((last - first) / first) * 100;
}

function resolveChangePercent(closes: number[], fallback?: number): number {
    const computed = computeChangePct(closes);
    if (Number.isFinite(computed) && Math.abs(computed) > 1e-6) {
        return computed;
    }
    if (fallback != null && Number.isFinite(fallback)) {
        return fallback;
    }
    return 0;
}

function buildFallbackTrendSparkline(price: number, changePercent: number, points = 18): number[] {
    const safePrice = Number.isFinite(price) && price > 0 ? price : 1;
    const ratio = 1 + Number(changePercent ?? 0) / 100;
    const start = ratio > 0 ? safePrice / ratio : safePrice * 0.99;
    return Array.from({ length: points }, (_, i) => {
        const t = i / Math.max(points - 1, 1);
        return start + (safePrice - start) * t;
    });
}

function normalizeTrendSparkline(values: number[], price: number, changePercent: number, points = 18): number[] {
    const clean = (values ?? []).map((v) => Number(v)).filter((v) => Number.isFinite(v) && v > 0);
    if (clean.length >= 2) {
        const slice = clean.slice(-points);
        if (slice.length < points) {
            return [...Array(points - slice.length).fill(slice[0]), ...slice];
        }
        return slice;
    }
    return buildFallbackTrendSparkline(price, changePercent, points);
}

function symbolSyncedWithMarketDashboard(
    dashboard: MarketDashboard | null,
    symbol: string,
    assetClass: string,
    type: AssetType
): boolean {
    if (!dashboard || !symbol) return false;
    const key = normalizeSymbolKey(symbol);
    const bucket = getDashboardLatestBucket(type);
    const latestMap = dashboard.latest[bucket] as Record<string, LatestPriceRow> | undefined;
    if (latestMap) {
        const row = latestMap[symbol] ?? latestMap[key];
        if (row && pickDashboardPrice(row) > 0) return true;
    }
    const inHeat = dashboard.heatmapTiles.some((tile) => normalizeSymbolKey(tile.symbol) === key && tile.assetClass === assetClass);
    const inSpark = dashboard.sparklines.some(
        (s) => normalizeSymbolKey(s.symbol) === key && s.assetClass === assetClass && (s.closes?.length ?? 0) >= 2,
    );
    const inSparkLoose = dashboard.sparklines.some((s) => normalizeSymbolKey(s.symbol) === key && (s.closes?.length ?? 0) >= 2);
    return inHeat || inSpark || inSparkLoose;
}

function getOverviewKey(type: AssetType): keyof MarketOverview {
    switch (type) {
        case 'CRYPTO':
            return 'crypto';
        case 'FX':
            return 'doviz';
        case 'METAL':
            return 'metals';
        case 'FUND':
            return 'funds';
        case 'STOCK':
            return 'stocks';
        default:
            return 'crypto';
    }
}

function getOverviewRowPrice(overview: MarketOverview | null, assetType: AssetType, sym: string): number | null {
    if (!overview || !sym) return null;
    const key = getOverviewKey(assetType);
    const map = overview[key] as Record<string, { buyPrice?: number; sellPrice?: number }> | undefined;
    if (!map || typeof map !== 'object') return null;
    const row = map[sym];
    if (!row) return null;
    const buy = Number(row.buyPrice ?? 0);
    const sell = Number(row.sellPrice ?? 0);
    if (buy > 0 && sell > 0) return (buy + sell) / 2;
    if (buy > 0) return buy;
    if (sell > 0) return sell;
    return null;
}

const ASSET_TYPE_LABEL_TR: Record<string, string> = {
    FX: 'Döviz',
    CRYPTO: 'Kripto',
    FUND: 'Fon',
    STOCK: 'Hisse',
    METAL: 'Altın / Gümüş',
};

/** Doygun palet: Hisse, Kripto, Döviz, Fon, Metal */
const PIE_COLOR_BY_TYPE: Record<string, string> = {
    STOCK: '#3b82f6',
    CRYPTO: '#06b6d4',
    FX: '#f59e0b',
    FUND: '#a855f7',
    METAL: '#eab308',
};

const PNL_POSITIVE = '#10b981';
const PNL_NEGATIVE = '#f43f5e';

type DistMode = 'COMBINED' | 'TRADE' | 'MANUAL';
type PnlMode = 'COMBINED' | 'TRADE' | 'MANUAL';
type ListFilter = 'ALL' | 'TRADE' | 'MANUAL';

const TABLE_PAGE = 10;

function aggregateValueByAssetType(items: PerformanceItem[] | undefined, mode: DistMode): DistRow[] {
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
        .map(([typeKey, value]) => ({
            name: ASSET_TYPE_LABEL_TR[typeKey] ?? typeKey,
            typeKey,
            value,
        }))
        .sort((a, b) => b.value - a.value);
}

function aggregatePnlByAsset(items: PerformanceItem[] | undefined, mode: PnlMode): { label: string; pnl: number; pnlPct: number }[] {
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

function pieColor(typeKey: string): string {
    return PIE_COLOR_BY_TYPE[typeKey.toUpperCase()] ?? '#64748b';
}

function useAnimatedNumber(target: number) {
    const [display, setDisplay] = useState(0);
    const displayRef = useRef(0);

    useEffect(() => {
        const from = displayRef.current;
        let raf = 0;
        const t0 = performance.now();
        const dur = 720;
        const step = (now: number) => {
            const p = Math.min(1, (now - t0) / dur);
            const eased = 1 - (1 - p) ** 3;
            const next = from + (target - from) * eased;
            displayRef.current = next;
            setDisplay(next);
            if (p < 1) raf = requestAnimationFrame(step);
        };
        raf = requestAnimationFrame(step);
        return () => cancelAnimationFrame(raf);
    }, [target]);

    return display;
}

function LegendIcon({ typeKey }: { typeKey: string }) {
    const k = typeKey.toUpperCase();
    const iconProps = { size: 16, strokeWidth: 2 };
    switch (k) {
        case 'STOCK':
            return <Building2 {...iconProps} />;
        case 'CRYPTO':
            return <Coins {...iconProps} />;
        case 'FX':
            return <Wallet {...iconProps} />;
        case 'FUND':
            return <PieChartIcon {...iconProps} />;
        case 'METAL':
            return <Gem {...iconProps} />;
        default:
            return <PieChartIcon {...iconProps} />;
    }
}

function AssetPiePanel({
    title,
    subtitle,
    data,
    chartKey,
    fmtMoney,
    totalLabel,
}: {
    title: string;
    subtitle: string;
    data: DistRow[];
    chartKey: string;
    fmtMoney: (v: number) => string;
    totalLabel: string;
}) {
    const [hoverIdx, setHoverIdx] = useState<number | null>(null);
    const totalVal = data.reduce((s, d) => s + d.value, 0);
    const active = hoverIdx != null && data[hoverIdx!] ? data[hoverIdx!] : null;
    const pct = active && totalVal > 0 ? (active.value / totalVal) * 100 : null;

    return (
        <div className="pf-card-premium portfolio-fade-in">
            <div style={{ fontSize: '0.9rem', fontWeight: 800, marginBottom: 4 }}>{title}</div>
            <div style={{ fontSize: '0.72rem', color: 'rgba(148,163,184,0.95)', marginBottom: 12 }}>{subtitle}</div>
            {totalVal <= 0 ? (
                <p style={{ margin: 0, fontSize: '0.8125rem', color: 'rgba(148,163,184,0.9)' }}>—</p>
            ) : (
                <div className="pf-chart-block">
                    <div className="pf-pie-wrap">
                        <ResponsiveContainer width="100%" height="100%">
                            <PieChart margin={{ top: 4, right: 4, left: 4, bottom: 4 }}>
                                <Pie
                                    data={data as unknown as Record<string, unknown>[]}
                                    dataKey="value"
                                    nameKey="name"
                                    cx="50%"
                                    cy="48%"
                                    innerRadius="46%"
                                    outerRadius="72%"
                                    paddingAngle={2}
                                    onMouseEnter={(_, i: number) => setHoverIdx(i)}
                                    onMouseLeave={() => setHoverIdx(null)}
                                >
                                    {data.map((entry, i) => (
                                        <Cell
                                            key={`${chartKey}-cell-${entry.typeKey}-${i}`}
                                            fill={pieColor(entry.typeKey)}
                                            stroke="rgba(15,23,42,0.88)"
                                            strokeWidth={2}
                                            opacity={hoverIdx === null || hoverIdx === i ? 1 : 0.38}
                                        />
                                    ))}
                                </Pie>
                                <Tooltip
                                    formatter={(v: number | undefined) => (v == null ? '' : fmtMoney(v))}
                                    contentStyle={{
                                        background: 'rgba(16,22,35,0.95)',
                                        border: '1px solid rgba(192,192,192,0.2)',
                                        borderRadius: 10,
                                    }}
                                />
                            </PieChart>
                        </ResponsiveContainer>
                        <div className="pf-pie-center">
                            {active && pct != null ? (
                                <>
                                    <div className="pf-pie-center-main">{pct.toLocaleString('tr-TR', { maximumFractionDigits: 1 })}%</div>
                                    <div className="pf-pie-center-sub">{active.name}</div>
                                    <div className="pf-pie-center-main" style={{ fontSize: '0.78rem', marginTop: 6, opacity: 0.95 }}>
                                        {fmtMoney(active.value)}
                                    </div>
                                </>
                            ) : (
                                <>
                                    <div className="pf-pie-center-main" style={{ fontSize: '0.85rem' }}>
                                        {totalLabel}
                                    </div>
                                    <div className="pf-pie-center-sub" style={{ marginTop: 6 }}>
                                        {fmtMoney(totalVal)}
                                    </div>
                                </>
                            )}
                        </div>
                    </div>
                    <div className="pf-legend">
                        {data.map((d, i) => {
                            const p = totalVal > 0 ? (d.value / totalVal) * 100 : 0;
                            return (
                                <div
                                    key={`${chartKey}-leg-${d.typeKey}`}
                                    className={`pf-legend-item ${hoverIdx === i ? 'pf-legend-item--active' : ''}`}
                                    onMouseEnter={() => setHoverIdx(i)}
                                    onMouseLeave={() => setHoverIdx(null)}
                                >
                                    <span className="pf-legend-swatch" style={{ background: pieColor(d.typeKey) }} />
                                    <LegendIcon typeKey={d.typeKey} />
                                    <span className="pf-legend-text">{d.name}</span>
                                    <span className="pf-legend-meta">
                                        {p.toLocaleString('tr-TR', { maximumFractionDigits: 1 })}%
                                        <br />
                                        {fmtMoney(d.value)}
                                    </span>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}
        </div>
    );
}

function buildPageIndices(current: number, totalPages: number): (number | 'gap')[] {
    if (totalPages <= 1) return [];
    if (totalPages <= 9) return Array.from({ length: totalPages }, (_, i) => i);
    const set = new Set<number>();
    set.add(0);
    set.add(totalPages - 1);
    for (let d = -2; d <= 2; d++) {
        const p = current + d;
        if (p >= 0 && p < totalPages) set.add(p);
    }
    const sorted = [...set].sort((a, b) => a - b);
    const out: (number | 'gap')[] = [];
    for (let i = 0; i < sorted.length; i++) {
        if (i > 0 && sorted[i]! - sorted[i - 1]! > 1) out.push('gap');
        out.push(sorted[i]!);
    }
    return out;
}

export function Portfolio() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
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
    const [marketDashboard, setMarketDashboard] = useState<MarketDashboard | null>(null);

    const [listFilter, setListFilter] = useState<ListFilter>('ALL');
    const [tablePage, setTablePage] = useState(0);

    const fetchAll = useCallback(() => {
        setLoading(true);
        setOverviewLoading(true);
        setError(null);

        const dashboardReq = financeClient
            .get('/api/market/dashboard')
            .then((dRes) => unwrapData<MarketDashboard>(dRes))
            .catch(() => null);

        Promise.all([
            financeClient.get('/api/portfolio/me/unified'),
            financeClient.get('/api/portfolio/performance/me'),
            financeClient.get('/api/market/overview'),
            dashboardReq,
        ])
            .then(([uRes, pRes, oRes, dash]) => {
                setUnifiedItems(unwrapData<UnifiedPortfolioItem[]>(uRes) ?? []);
                setPerf(unwrapData<PortfolioPerformance>(pRes));
                setOverview(unwrapData<MarketOverview>(oRes));
                setMarketDashboard(dash);
            })
            .catch((err) => {
                const msg =
                    err.response?.data?.errors?.error ??
                    err.response?.data?.message ??
                    err.message ??
                    t('portfolio.loadFailed', 'Portföy verisi alınamadı');
                setError(msg);
            })
            .finally(() => {
                setLoading(false);
                setOverviewLoading(false);
            });
    }, [t]);

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

    const distributionCombined = useMemo(() => aggregateValueByAssetType(perf?.items, 'COMBINED'), [perf]);
    const distributionTrade = useMemo(() => aggregateValueByAssetType(perf?.items, 'TRADE'), [perf]);
    const distributionManual = useMemo(() => aggregateValueByAssetType(perf?.items, 'MANUAL'), [perf]);
    const pnlCombined = useMemo(() => aggregatePnlByAsset(perf?.items, 'COMBINED'), [perf]);
    const pnlTrade = useMemo(() => aggregatePnlByAsset(perf?.items, 'TRADE'), [perf]);
    const pnlManual = useMemo(() => aggregatePnlByAsset(perf?.items, 'MANUAL'), [perf]);

    const filteredUnified = useMemo(() => {
        return unifiedItems.filter((row) => {
            const src = String(row.source ?? '').toUpperCase();
            if (listFilter === 'ALL') return true;
            if (listFilter === 'MANUAL') return src === 'MANUAL';
            return src !== 'MANUAL';
        });
    }, [unifiedItems, listFilter]);

    const tableTotalPages = Math.max(1, Math.ceil(filteredUnified.length / TABLE_PAGE));
    const tableSlice = useMemo(() => {
        const start = tablePage * TABLE_PAGE;
        return filteredUnified.slice(start, start + TABLE_PAGE);
    }, [filteredUnified, tablePage]);

    useEffect(() => {
        setTablePage((p) => Math.min(p, tableTotalPages - 1));
    }, [tableTotalPages, filteredUnified.length]);

    useEffect(() => {
        setTablePage(0);
    }, [listFilter]);

    useEffect(() => {
        if (symbolOptions.length === 0) {
            setSymbol('');
            return;
        }
        if (!symbolOptions.includes(symbol)) {
            setSymbol(symbolOptions[0]);
        }
    }, [symbolOptions, symbol]);

    const overviewQuote = useMemo(() => getOverviewRowPrice(overview, type, symbol), [overview, type, symbol]);

    const manualFieldCopy = useMemo(() => {
        switch (type) {
            case 'STOCK':
                return {
                    qty: t('portfolio.fieldQtyStock', 'Miktar (Lot)'),
                    qtyPh: t('portfolio.fieldQtyPhStock', 'Örn. 10 lot'),
                    price: t('portfolio.fieldPriceStock', 'Alış fiyatı (1 lot için)'),
                    pricePh: t('portfolio.fieldPricePhStock', 'TRY / lot'),
                    focus: 'pf-input--asset-stock',
                };
            case 'CRYPTO':
                return {
                    qty: t('portfolio.fieldQtyCrypto', 'Miktar (adet / birim)'),
                    qtyPh: t('portfolio.fieldQtyPhCrypto', 'Örn. 0,25'),
                    price: t('portfolio.fieldPriceCrypto', 'Alış fiyatı (1 birim için)'),
                    pricePh: t('portfolio.fieldPricePhCrypto', 'TRY / birim'),
                    focus: 'pf-input--asset-crypto',
                };
            case 'METAL':
                return {
                    qty: t('portfolio.fieldQtyMetal', 'Miktar (gram)'),
                    qtyPh: t('portfolio.fieldQtyPhMetal', 'Örn. 100'),
                    price: t('portfolio.fieldPriceMetal', 'Alış fiyatı (1 gram için)'),
                    pricePh: t('portfolio.fieldPricePhMetal', 'TRY / gr'),
                    focus: 'pf-input--asset-metal',
                };
            case 'FX':
                return {
                    qty: t('portfolio.fieldQtyFx', 'Miktar (birim)'),
                    qtyPh: t('portfolio.fieldQtyPhFx', 'Örn. 1000'),
                    price: t('portfolio.fieldPriceFx', 'Alış fiyatı (1 birim için)'),
                    pricePh: t('portfolio.fieldPricePhFx', 'TRY karşılığı'),
                    focus: 'pf-input--asset-fx',
                };
            case 'FUND':
            default:
                return {
                    qty: t('portfolio.fieldQtyFund', 'Miktar (adet)'),
                    qtyPh: t('portfolio.fieldQtyPhFund', 'Örn. 150'),
                    price: t('portfolio.fieldPriceFund', 'Alış fiyatı (1 pay için)'),
                    pricePh: t('portfolio.fieldPricePhFund', 'TRY / pay'),
                    focus: 'pf-input--asset-fund',
                };
        }
    }, [type, t]);

    const dashboardAssetClass = useMemo(() => dashboardAssetClassForPortfolioType(type), [type]);

    const heatTile = useMemo(() => {
        if (!marketDashboard || !symbol) return null;
        return findHeatmapTileFor(marketDashboard, symbol, dashboardAssetClass);
    }, [marketDashboard, symbol, dashboardAssetClass]);

    const sparkClosesRaw = useMemo(
        () => sparklineClosesForPortfolio(marketDashboard, symbol, dashboardAssetClass),
        [marketDashboard, symbol, dashboardAssetClass],
    );

    const marketSynced = useMemo(
        () => symbolSyncedWithMarketDashboard(marketDashboard, symbol, dashboardAssetClass, type),
        [marketDashboard, symbol, dashboardAssetClass, type],
    );

    const showMarketSkeleton = Boolean(
        symbol &&
            !overviewLoading &&
            marketDashboard != null &&
            !marketSynced &&
            (overviewQuote == null || !Number.isFinite(overviewQuote) || overviewQuote <= 0),
    );

    const dashLatestRow = useMemo(() => {
        if (!marketDashboard || !symbol) return null;
        const bucket = getDashboardLatestBucket(type);
        const map = marketDashboard.latest[bucket] as Record<string, LatestPriceRow> | undefined;
        return map?.[symbol] ?? null;
    }, [marketDashboard, symbol, type]);

    const dashPrice = useMemo(() => pickDashboardPrice(dashLatestRow), [dashLatestRow]);

    const tileChangeFallback = useMemo(() => {
        const raw = heatTile?.changePercent;
        return raw != null && Number.isFinite(Number(raw)) ? Number(raw) : undefined;
    }, [heatTile]);

    const resolvedChangePct = useMemo(
        () => resolveChangePercent(sparkClosesRaw, tileChangeFallback),
        [sparkClosesRaw, tileChangeFallback],
    );

    const displayQuotePrice = useMemo(() => {
        if (dashPrice > 0 && Number.isFinite(dashPrice)) return dashPrice;
        if (overviewQuote != null && Number.isFinite(overviewQuote) && overviewQuote > 0) return overviewQuote;
        const lastClose = sparkClosesRaw.length ? sparkClosesRaw[sparkClosesRaw.length - 1] : null;
        if (lastClose != null && Number.isFinite(lastClose) && lastClose > 0) return lastClose;
        return null;
    }, [dashPrice, overviewQuote, sparkClosesRaw]);

    const widgetYearlyValues = useMemo(() => {
        const price = displayQuotePrice ?? 1;
        return normalizeTrendSparkline(sparkClosesRaw, price, resolvedChangePct, 252);
    }, [sparkClosesRaw, displayQuotePrice, resolvedChangePct]);

    const [widgetAreaSeries, setWidgetAreaSeries] = useState<Array<{ i: number; v: number }>>([]);
    const [widgetAreaReady, setWidgetAreaReady] = useState(false);
    useEffect(() => {
        setWidgetAreaReady(false);
        let rafId = 0;
        let readyRafId = 0;
        rafId = requestAnimationFrame(() => {
            setWidgetAreaSeries(widgetYearlyValues.map((v, i) => ({ i, v })));
            readyRafId = requestAnimationFrame(() => setWidgetAreaReady(true));
        });
        return () => {
            cancelAnimationFrame(rafId);
            cancelAnimationFrame(readyRafId);
        };
    }, [widgetYearlyValues, symbol, type]);

    const widgetStatusLine = useMemo(() => {
        const raw = marketDashboard?.computedAt;
        if (raw) {
            const d = new Date(raw);
            if (!Number.isNaN(d.getTime())) {
                return {
                    kind: 'time' as const,
                    text: `${t('portfolio.lastUpdatePrefix', 'Son güncelleme:')} ${d.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit', second: '2-digit' })}`,
                };
            }
        }
        return { kind: 'open' as const, text: t('portfolio.marketOpenStatus', 'Piyasa durumu: Açık') };
    }, [marketDashboard?.computedAt, t, locale]);

    const resetManualForm = () => {
        setEditingManualId(null);
        setQuantity('0.1');
        setBuyPrice('100000');
        setBuyDate(new Date().toISOString().slice(0, 10));
        setNote('');
    };

    const saveManual = async (e: FormEvent) => {
        e.preventDefault();

        const q = Number(quantity);
        const bp = Number(buyPrice);
        if (!q || q <= 0 || !bp || bp <= 0) {
            alert(t('portfolio.alertQtyPrice', 'Miktar ve alış fiyatı sıfırdan büyük olmalı.'));
            return;
        }

        const normalizedSymbol = symbol.trim().toUpperCase();
        if (!normalizedSymbol) {
            alert(t('portfolio.alertSymbol', 'Lütfen sembol seçin.'));
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
                    t('portfolio.manualSaveFailed', 'Manuel pozisyon kaydedilemedi'),
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
        if (!window.confirm(t('portfolio.confirmDelete', 'Bu manuel pozisyonu silmek istediğinize emin misiniz?'))) return;
        try {
            await financeClient.delete(`/api/portfolio/manual/${id}`);
            if (editingManualId === id) resetManualForm();
            await fetchAll();
        } catch (err: any) {
            alert(
                err?.response?.data?.errors?.error ??
                    err?.response?.data?.message ??
                    err?.message ??
                    t('portfolio.deleteFailed', 'Pozisyon silinemedi'),
            );
        }
    };

    const fmtMoney = (v: number) => `₺${Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}`;

    const animCost = useAnimatedNumber(perf?.totalCost ?? 0);
    const animValue = useAnimatedNumber(perf?.totalCurrentValue ?? 0);
    const animPnl = useAnimatedNumber(perf?.totalPnl ?? 0);
    const animPnlPct = useAnimatedNumber(perf?.totalPnlPct ?? 0);

    const sourceBadge = (row: UnifiedPortfolioItem): { label: string; cls: string } => {
        const src = String(row.source ?? '').toUpperCase();
        if (src === 'MANUAL') {
            return { label: t('portfolio.sourceManual', 'Manuel kayıt'), cls: 'pf-source-badge--manual' };
        }
        return { label: t('portfolio.sourceTrade', 'Borsa işlemi'), cls: 'pf-source-badge--trade' };
    };

    const pageStyle: CSSProperties = {
        background: tokens.bg,
        color: tokens.text,
    };

    const tablePageItems = buildPageIndices(tablePage, tableTotalPages);

    const liveWidgetGlowClass =
        type === 'CRYPTO' ? 'pf-live-widget--crypto' : type === 'METAL' ? 'pf-live-widget--metal' : 'pf-live-widget--neutral';

    if (loading) {
        return (
            <div className="portfolio-page" style={pageStyle}>
                <h1 style={{ fontSize: '1.75rem', fontWeight: 700 }}>{t('nav.portfolio', 'Portföy')}</h1>
                <p style={{ color: tokens.textMuted }}>{t('common.loading', 'Yükleniyor...')}</p>
            </div>
        );
    }

    if (error) {
        return (
            <div className="portfolio-page" style={pageStyle}>
                <h1 style={{ fontSize: '1.75rem', fontWeight: 700 }}>{t('nav.portfolio', 'Portföy')}</h1>
                <p style={{ color: tokens.error }}>
                    {t('news.errorPrefix', 'Hata')}: {error}
                </p>
            </div>
        );
    }

    return (
        <div
            className="portfolio-page"
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
        >
            <div className="portfolio-fade-in">
                <h1 style={{ fontSize: '1.65rem', fontWeight: 800, marginBottom: 6 }}>{t('portfolio.myPortfolios', 'Portföy analizi')}</h1>
                <p style={{ color: tokens.textMuted, fontSize: '0.875rem', marginBottom: 12 }}>
                    {t('portfolio.heroSubtitle', 'Trade ve manuel kayıtlar birleşik görünüm; dağılım ve performans özeti.')}
                </p>
            </div>

            <div className="pf-list-tabs pf-segment portfolio-fade-in portfolio-fade-in--delay-1" role="tablist" aria-label={t('portfolio.listFilterAria', 'Varlık listesi kaynağı')}>
                {(
                    [
                        ['ALL', t('portfolio.tabAll', 'Tüm varlıklar')],
                        ['TRADE', t('portfolio.tabTrade', 'Borsa işlemleri')],
                        ['MANUAL', t('portfolio.tabManual', 'Manuel kayıtlar')],
                    ] as const
                ).map(([k, label]) => (
                    <button
                        key={k}
                        type="button"
                        role="tab"
                        aria-selected={listFilter === k}
                        className={listFilter === k ? 'pf-segment--on' : ''}
                        onClick={() => setListFilter(k)}
                    >
                        {label}
                    </button>
                ))}
            </div>

            <details
                className="pf-reading-accordion pf-card-premium portfolio-fade-in portfolio-fade-in--delay-1 pf-reading-accordion--spaced"
                open={infoOpen}
                onToggle={(e) => setInfoOpen(e.currentTarget.open)}
            >
                <summary className="pf-reading-accordion-summary">
                    <span className="pf-reading-accordion-title">
                        <Info size={16} aria-hidden />
                        {t('portfolio.readingGuide', 'Portföy okuma rehberi')}
                    </span>
                    <ChevronDown size={16} className="pf-reading-accordion-chevron" aria-hidden />
                </summary>
                <div className="pf-reading-accordion-body">
                    <p style={{ margin: 0, fontSize: '0.8125rem', color: tokens.textMuted, lineHeight: 1.45 }}>
                        {t('portfolio.readingGuideBody', 'Halka grafiklerde üzerine gelerek dilim oranı ve tutarı görün. Tabloda kaynak filtresi ve sayfalama kullanın.')}
                    </p>
                </div>
            </details>

            <div className="pf-dash-stack portfolio-fade-in portfolio-fade-in--delay-1">
                <div className="pf-summary-grid pf-summary-grid--full">
                    <div className="pf-stat-card">
                        <div className="pf-stat-label">{t('portfolio.totalCost', 'Toplam maliyet')}</div>
                        <div className="pf-stat-value">{fmtMoney(animCost)}</div>
                    </div>
                    <div className="pf-stat-card">
                        <div className="pf-stat-label">{t('portfolio.currentValue', 'Güncel değer')}</div>
                        <div className="pf-stat-value">{fmtMoney(animValue)}</div>
                    </div>
                    <div className={`pf-stat-card ${(perf?.totalPnl ?? 0) >= 0 ? 'pf-stat-card--pnl-pos' : 'pf-stat-card--pnl-neg'}`}>
                        <div className="pf-stat-label">{t('portfolio.totalPnlTry', 'Kar / zarar (TRY)')}</div>
                        <div className="pf-stat-value" style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                            {(perf?.totalPnl ?? 0) >= 0 ? <TrendingUp size={18} /> : <TrendingDown size={18} />}
                            {fmtMoney(animPnl)}
                        </div>
                    </div>
                    <div className={`pf-stat-card ${(perf?.totalPnlPct ?? 0) >= 0 ? 'pf-stat-card--pnl-pos' : 'pf-stat-card--pnl-neg'}`}>
                        <div className="pf-stat-label">{t('portfolio.totalPnlPct', 'Toplam getiri %')}</div>
                        <div className="pf-stat-value" style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                            {(perf?.totalPnlPct ?? 0) >= 0 ? <TrendingUp size={18} /> : <TrendingDown size={18} />}
                            {animPnlPct.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%
                        </div>
                    </div>
                </div>

                <div className="pf-form-widget-row">
                    <aside className="pf-manual-panel pf-card-premium">
                        <h2>{editingManualId != null ? t('portfolio.editManualPosition', 'Manuel pozisyonu düzenle') : t('portfolio.addManualPosition', 'Manuel pozisyon ekle')}</h2>
                        <form onSubmit={saveManual} className="pf-manual-form">
                            <label className="pf-field-label">
                                {t('portfolio.assetType', 'Varlık türü')}
                                <select
                                    className={`pf-input ${manualFieldCopy.focus}`}
                                    value={type}
                                    onChange={(e) => setType(e.target.value as AssetType)}
                                >
                                    {(Object.keys(ASSET_TYPE_LABEL_TR) as AssetType[]).map((k) => (
                                        <option key={k} value={k}>
                                            {ASSET_TYPE_LABEL_TR[k]}
                                        </option>
                                    ))}
                                </select>
                            </label>

                            <label className="pf-field-label">
                                {t('portfolio.symbol', 'Sembol')}
                                {loading || overviewLoading ? (
                                    <div className="pf-input" style={{ color: tokens.textMuted }}>
                                        {t('common.loading', 'Yükleniyor...')}
                                    </div>
                                ) : symbolOptions.length === 0 ? (
                                    <div className="pf-input" style={{ color: tokens.textMuted }}>
                                        {t('portfolio.noSymbolsType', 'Bu tür için sembol yok.')}
                                    </div>
                                ) : (
                                    <select className={`pf-input ${manualFieldCopy.focus}`} value={symbol} onChange={(e) => setSymbol(e.target.value)}>
                                        {symbolOptions.map((opt) => (
                                            <option key={opt} value={opt}>
                                                {opt}
                                            </option>
                                        ))}
                                    </select>
                                )}
                            </label>

                            <label className="pf-field-label">
                                {manualFieldCopy.qty}
                                <input
                                    className={`pf-input ${manualFieldCopy.focus}`}
                                    type="number"
                                    step="0.00000001"
                                    placeholder={manualFieldCopy.qtyPh}
                                    value={quantity}
                                    onChange={(e) => setQuantity(e.target.value)}
                                />
                            </label>

                            <label className="pf-field-label">
                                {manualFieldCopy.price}
                                <input
                                    className={`pf-input ${manualFieldCopy.focus}`}
                                    type="number"
                                    step="0.00000001"
                                    placeholder={manualFieldCopy.pricePh}
                                    value={buyPrice}
                                    onChange={(e) => setBuyPrice(e.target.value)}
                                />
                            </label>

                            <label className="pf-field-label">
                                {t('portfolio.buyDate', 'Alış tarihi')}
                                <input className={`pf-input ${manualFieldCopy.focus}`} type="date" value={buyDate} onChange={(e) => setBuyDate(e.target.value)} />
                            </label>

                            <label className="pf-field-label">
                                {t('portfolio.note', 'Not')}
                                <textarea
                                    className={`pf-input ${manualFieldCopy.focus}`}
                                    rows={3}
                                    value={note}
                                    onChange={(e) => setNote(e.target.value)}
                                    style={{ resize: 'vertical' }}
                                />
                            </label>

                            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                                <button type="submit" disabled={savingManual} className="pf-btn-submit-silver">
                                    {savingManual ? t('portfolio.saving', 'Kaydediliyor...') : editingManualId != null ? t('common.update', 'Güncelle') : t('portfolio.addPosition', 'Pozisyon ekle')}
                                </button>
                                {editingManualId != null ? (
                                    <button
                                        type="button"
                                        onClick={resetManualForm}
                                        disabled={savingManual}
                                        style={{
                                            padding: '10px 16px',
                                            borderRadius: 10,
                                            border: `1px solid ${tokens.border}`,
                                            background: tokens.bgCard,
                                            color: tokens.text,
                                            fontWeight: 600,
                                            cursor: savingManual ? 'default' : 'pointer',
                                        }}
                                    >
                                        {t('common.cancel', 'İptal')}
                                    </button>
                                ) : null}
                            </div>
                        </form>
                    </aside>

                    <div className={`pf-live-widget pf-card-premium ${liveWidgetGlowClass}`}>
                        <div className="pf-live-widget-head">
                            <span className="pf-live-widget-kicker">{t('portfolio.liveWidgetTitle', 'Piyasa özeti')}</span>
                            <span className="pf-live-widget-symbol">{symbol || '—'}</span>
                        </div>
                        <div key={`${type}-${symbol}`} className="pf-live-widget-body-inner">
                            {overviewLoading ? (
                                <p className="pf-live-widget-muted">{t('common.loading', 'Yükleniyor...')}</p>
                            ) : showMarketSkeleton ? (
                                <div className="pf-widget-skeleton" aria-busy="true">
                                    <p className="pf-widget-skeleton-title">{t('portfolio.widgetDataPending', 'Veri bekleniyor…')}</p>
                                    <div className="pf-widget-skeleton-line pf-widget-skeleton-line--wide" />
                                    <div className="pf-widget-skeleton-line pf-widget-skeleton-line--narrow" />
                                    <div className="pf-widget-skeleton-chart" />
                                </div>
                            ) : displayQuotePrice != null && Number.isFinite(displayQuotePrice) ? (
                                <>
                                    <div className="pf-widget-overlay-metrics">
                                        <div className="pf-live-widget-price pf-live-widget-price-glow">{fmtMoney(displayQuotePrice)}</div>
                                        <div
                                            className={`pf-widget-delta ${resolvedChangePct > 0 ? 'pf-delta-up-strong' : resolvedChangePct < 0 ? 'pf-delta-down-strong' : 'pf-delta-flat'}`}
                                        >
                                            {resolvedChangePct > 0
                                                ? `▲ +${Math.abs(resolvedChangePct).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%`
                                                : resolvedChangePct < 0
                                                  ? `▼ -${Math.abs(resolvedChangePct).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%`
                                                  : `▲ ${Math.abs(resolvedChangePct).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%`}
                                        </div>
                                    </div>
                                    <p className="pf-live-widget-hint">{t('portfolio.liveSparklineHint')}</p>
                                    <div className={`pf-sparkline-chart pf-sparkline-chart--area${widgetAreaReady ? ' is-ready' : ''}`}>
                                        <ResponsiveContainer width="100%" height="100%">
                                            <AreaChart data={widgetAreaSeries} margin={{ top: 6, right: 4, left: 4, bottom: 2 }}>
                                                <defs>
                                                    <linearGradient id="portfolioWidgetAreaFill" x1="0" y1="0" x2="0" y2="1">
                                                        <stop offset="0%" stopColor="#93c5fd" stopOpacity={0.46} />
                                                        <stop offset="58%" stopColor="#60a5fa" stopOpacity={0.18} />
                                                        <stop offset="100%" stopColor="#cbd5e1" stopOpacity={0.03} />
                                                    </linearGradient>
                                                </defs>
                                                <XAxis dataKey="i" hide />
                                                <YAxis hide domain={['dataMin', 'dataMax']} />
                                                <Tooltip
                                                    formatter={(v: number | undefined) =>
                                                        v == null ? '' : Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 6 })
                                                    }
                                                    labelFormatter={() => ''}
                                                    contentStyle={{
                                                        background: 'rgba(16,22,35,0.95)',
                                                        border: '1px solid rgba(192,192,192,0.2)',
                                                        borderRadius: 10,
                                                        fontSize: 12,
                                                    }}
                                                />
                                                <Area
                                                    type="monotone"
                                                    dataKey="v"
                                                    stroke={resolvedChangePct >= 0 ? '#60a5fa' : '#f87171'}
                                                    strokeWidth={2.2}
                                                    fill="url(#portfolioWidgetAreaFill)"
                                                    dot={false}
                                                    activeDot={{ r: 2.8 }}
                                                    isAnimationActive={false}
                                                />
                                            </AreaChart>
                                        </ResponsiveContainer>
                                    </div>
                                </>
                            ) : (
                                <p className="pf-live-widget-muted">{t('portfolio.liveWidgetNoData', 'Bu sembol için anlık fiyat bulunamadı.')}</p>
                            )}
                        </div>
                        <p className="pf-live-widget-status">{widgetStatusLine.text}</p>
                    </div>
                </div>

                <div className="pf-card-premium pf-asset-list-block portfolio-fade-in portfolio-fade-in--delay-2">
                    <h2 style={{ marginTop: 0, marginBottom: 10, fontSize: '1rem', fontWeight: 800 }}>{t('portfolio.combinedAssetList', 'Varlık listesi')}</h2>

                    <div className="pf-table-scroll">
                        <table className="pf-table">
                            <thead>
                                <tr>
                                    <th>{t('portfolio.colSource', 'Kaynak')}</th>
                                    <th>{t('portfolio.colType', 'Tür')}</th>
                                    <th>{t('portfolio.colSymbol', 'Sembol')}</th>
                                    <th className="pf-th-num">{t('portfolio.colQty', 'Miktar')}</th>
                                    <th className="pf-th-num">{t('portfolio.colAvgBuy', 'Ort. alış')}</th>
                                    <th className="pf-th-num">{t('portfolio.colCurrent', 'Güncel fiyat')}</th>
                                    <th className="pf-th-num">{t('portfolio.colPerf', 'Performans')}</th>
                                    <th className="pf-th-num">{t('portfolio.colActions', 'İşlem')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredUnified.length === 0 ? (
                                    <tr>
                                        <td colSpan={8} style={{ padding: 16, color: tokens.textMuted, textAlign: 'center' }}>
                                            {t('portfolio.tableEmpty', 'Bu görünümde varlık yok.')}
                                        </td>
                                    </tr>
                                ) : (
                                    tableSlice.map((row, i) => {
                                        const perfKey = rowPerfKey(row);
                                        const rowPerf = perfItemMap.get(perfKey);
                                        const priceCurrency = rowPerf?.currentPriceCurrency ?? 'TRY';
                                        const badge = sourceBadge(row);
                                        const pnlPct = rowPerf != null ? Number(rowPerf.pnlPct) : null;
                                        return (
                                            <tr key={`${perfKey}-${i}`}>
                                                <td>
                                                    <span className={`pf-source-badge ${badge.cls}`}>{badge.label}</span>
                                                </td>
                                                <td>{ASSET_TYPE_LABEL_TR[String(row.type).toUpperCase()] ?? row.type}</td>
                                                <td className="pf-num-strong">{row.symbol}</td>
                                                <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                                    {Number(row.quantity).toLocaleString(locale, { maximumFractionDigits: 8 })}
                                                </td>
                                                <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                                    {fmtMoney(row.avgBuyPrice)}
                                                </td>
                                                <td className="pf-num-strong" style={{ textAlign: 'right' }}>
                                                    {rowPerf ? `${fmtMoney(Number(rowPerf.currentPrice ?? 0))} (${priceCurrency})` : '—'}
                                                </td>
                                                <td style={{ textAlign: 'right' }}>
                                                    {pnlPct != null && Number.isFinite(pnlPct) ? (
                                                        <span className={pnlPct >= 0 ? 'pf-delta-up' : 'pf-delta-down'}>
                                                            {pnlPct >= 0 ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                                                            {pnlPct.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%
                                                        </span>
                                                    ) : (
                                                        '—'
                                                    )}
                                                </td>
                                                <td style={{ textAlign: 'right' }}>
                                                    {row.source === 'MANUAL' && row.manualPositionId != null ? (
                                                        <span className="pf-row-actions">
                                                            <button type="button" className="pf-icon-ghost" onClick={() => startEditManual(row)} title={t('common.update', 'Düzenle')}>
                                                                <Pencil size={16} />
                                                            </button>
                                                            <button
                                                                type="button"
                                                                className="pf-icon-ghost pf-icon-ghost--danger"
                                                                onClick={() => deleteManual(row.manualPositionId!)}
                                                                title={t('portfolio.delete', 'Sil')}
                                                            >
                                                                <Trash2 size={16} />
                                                            </button>
                                                        </span>
                                                    ) : (
                                                        <span style={{ color: tokens.textMuted }}>—</span>
                                                    )}
                                                </td>
                                            </tr>
                                        );
                                    })
                                )}
                            </tbody>
                        </table>
                    </div>

                    {tableTotalPages > 1 && (
                        <div className="pf-pagination">
                            <button type="button" className="pf-page-btn" disabled={tablePage <= 0} onClick={() => setTablePage((p) => Math.max(0, p - 1))}>
                                {t('portfolio.prev', 'Önceki')}
                            </button>
                            {tablePageItems.map((item, idx) =>
                                item === 'gap' ? (
                                    <span key={`g-${idx}`} style={{ color: tokens.textMuted }}>
                                        …
                                    </span>
                                ) : (
                                    <button
                                        key={item}
                                        type="button"
                                        className={`pf-page-btn ${item === tablePage ? 'pf-page-btn--active' : ''}`}
                                        onClick={() => setTablePage(item)}
                                    >
                                        {item + 1}
                                    </button>
                                ),
                            )}
                            <button
                                type="button"
                                className="pf-page-btn"
                                disabled={tablePage >= tableTotalPages - 1}
                                onClick={() => setTablePage((p) => Math.min(tableTotalPages - 1, p + 1))}
                            >
                                {t('portfolio.next', 'Sonraki')}
                            </button>
                            <span style={{ fontSize: '0.78rem', color: tokens.textMuted, marginLeft: 8 }}>
                                {filteredUnified.length} {t('portfolio.records', 'kayıt')}
                            </span>
                        </div>
                    )}
                </div>
            </div>

            <div className="pf-card-premium portfolio-fade-in portfolio-fade-in--delay-2 pf-section-card">
                <h2 style={{ margin: '0 0 6px', fontSize: '1rem', fontWeight: 800 }}>{t('portfolio.distributionTry', 'Portföy dağılımı (TRY)')}</h2>
                <p style={{ margin: '0 0 16px', fontSize: '0.8rem', color: tokens.textMuted }}>{t('portfolio.distributionHint', 'Güncel değer üzerinden varlık sınıfı kırılımı.')}</p>
                <div className="pf-dist-grid">
                    <AssetPiePanel
                        title={t('portfolio.distCombined', 'Birleşik')}
                        subtitle={t('portfolio.distCombinedSub', 'Tüm kaynaklar')}
                        data={distributionCombined}
                        chartKey="combined"
                        fmtMoney={fmtMoney}
                        totalLabel={t('portfolio.pieCenterTry', 'TRY')}
                    />
                    <AssetPiePanel
                        title={t('portfolio.distTrade', 'Borsa işlemleri')}
                        subtitle={t('portfolio.distTradeSub', 'Otomatik kayıtlar')}
                        data={distributionTrade}
                        chartKey="trade"
                        fmtMoney={fmtMoney}
                        totalLabel={t('portfolio.pieCenterTry', 'TRY')}
                    />
                    <AssetPiePanel
                        title={t('portfolio.distManual', 'Manuel')}
                        subtitle={t('portfolio.distManualSub', 'El ile eklenenler')}
                        data={distributionManual}
                        chartKey="manual"
                        fmtMoney={fmtMoney}
                        totalLabel={t('portfolio.pieCenterTry', 'TRY')}
                    />
                </div>
            </div>

            <div className="pf-card-premium portfolio-fade-in portfolio-fade-in--delay-2 pf-section-card">
                <h2 style={{ margin: '0 0 8px', fontSize: '1rem', fontWeight: 800 }}>{t('portfolio.pnlBarsTitle', 'Varlık bazlı kar / zarar')}</h2>
                <p style={{ margin: '0 0 14px', fontSize: '0.78rem', color: tokens.textMuted }}>{t('portfolio.pnlBarsHint', 'Sembol bazında anlık PnL; doygun yeşil / kırmızı çubuklar.')}</p>
                <div className="pf-dist-grid">
                    {(
                        [
                            { key: 'pnl-combined', title: t('portfolio.distCombined', 'Birleşik'), data: pnlCombined },
                            { key: 'pnl-trade', title: t('portfolio.distTrade', 'Borsa işlemleri'), data: pnlTrade },
                            { key: 'pnl-manual', title: t('portfolio.distManual', 'Manuel'), data: pnlManual },
                        ] as const
                    ).map(({ key, title, data }) => (
                        <div key={key} className="pf-card-premium" style={{ padding: 14 }}>
                            <div style={{ fontSize: '0.9rem', fontWeight: 800, marginBottom: 10 }}>{title}</div>
                            {data.length === 0 ? (
                                <p style={{ margin: 0, fontSize: '0.8125rem', color: tokens.textMuted }}>—</p>
                            ) : (
                                <div className="pf-bar-wrap">
                                    <ResponsiveContainer width="100%" height="100%">
                                        <BarChart data={data} margin={{ top: 10, right: 10, left: 4, bottom: 28 }} barCategoryGap="14%">
                                            <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} opacity={0.5} />
                                            <XAxis dataKey="label" tick={{ fontSize: 11, fill: tokens.textMuted }} angle={-18} textAnchor="end" height={50} />
                                            <YAxis
                                                tick={{ fontSize: 10, fill: tokens.textMuted }}
                                                tickFormatter={(v) => Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 0 })}
                                            />
                                            <Tooltip
                                                formatter={(v: number | undefined, _n, payload) => {
                                                    if (v == null) return '';
                                                    const pct = Number(payload?.payload?.pnlPct ?? 0);
                                                    return [
                                                        `${fmtMoney(v)} (${pct.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%)`,
                                                        'PnL',
                                                    ];
                                                }}
                                                contentStyle={{
                                                    background: 'rgba(16,22,35,0.95)',
                                                    border: '1px solid rgba(192,192,192,0.2)',
                                                    borderRadius: 10,
                                                }}
                                            />
                                            <Bar dataKey="pnl" radius={[8, 8, 0, 0]} barSize={44} maxBarSize={56}>
                                                {data.map((r) => (
                                                    <Cell key={`${key}-${r.label}`} fill={r.pnl >= 0 ? PNL_POSITIVE : PNL_NEGATIVE} />
                                                ))}
                                            </Bar>
                                        </BarChart>
                                    </ResponsiveContainer>
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}
