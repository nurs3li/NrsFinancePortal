import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties, type MouseEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AxiosResponse } from 'axios';
import { useNavigate } from 'react-router-dom';
import { financeClient, marketClient } from '../api/client';
import type { LatestPriceRow, MarketDashboard } from '../components/market/marketTypes';
import { useTheme } from '../theme/ThemeContext';
import { formatAssetLabel, getDynamicLogoUrl } from '../lib/assetBranding';
import { AssetLogo } from '../components/AssetLogo';
import { MarketFinvizTreemap, type TreemapTile } from '../components/market/MarketFinvizTreemap';
import { MarketTerminalChart } from '../components/market/MarketTerminalChart';
import { BondTerminalChart } from '../components/market/BondTerminalChart';
import { ViopTerminalChart } from '../components/market/ViopTerminalChart';
import { SpotTerminalChart } from '../components/market/SpotTerminalChart';
import { MarketCompareLwChart } from '../components/market/MarketCompareLwChart';
import { usePolling } from '../hooks/usePolling';
import { Star, TrendingUp } from 'lucide-react';
import { extractMaturityDate, formatBondDisplayName, getRemainingDays } from '../utils/bondFormatter';
import { cryptoMeta, etfMeta, fxMeta, getBondMeta, instrumentMeta } from '../utils/instrumentMeta';
import './MarketTerminal.css';

type MarketInstrument = {
    symbol: string;
    category: MarketCategory;
    price: number;
    changePercent: number;
    trend: 'UP' | 'DOWN';
    high?: number;
    low?: number;
    volume?: number | null;
    /** İlk bulunan: API `currency` | `priceCurrency` | `quoteCurrency` */
    currency?: string;
    metrics?: {
        basis?: number;
        yield?: number;
    };
};
type InstrumentType = 'STOCK' | 'BOND' | 'FUTURES';
type InstrumentVm = MarketInstrument & {
    type: InstrumentType;
    displayName: string;
    sparkline: number[];
    maturityDate?: string;
    daysToMaturity?: number;
    couponRate?: number;
    yieldToMaturity?: number;
    contractMonth?: string;
    expiryDate?: string;
    marginRequirement?: number;
    longShort?: 'LONG' | 'SHORT' | 'NÖTR';
};

type IndicatorPoint = { t: string; value: number };
type IndicatorsResponse = {
    close: IndicatorPoint[];
    ma: Record<string, IndicatorPoint[]>;
};
type CandlePoint = { t: string; o: number; h: number; l: number; c: number; v: number };
type MarketHistoryPoint = {
    buyPrice?: number;
    sellPrice?: number;
    timestamp?: string;
    asOf?: string;
};
type BatchHistoryResponse = { series: Record<string, CandlePoint[]> };
type ViopSnapshot = {
    contractCode: string;
    expiryDate?: string;
    contractMonth?: string;
    price: number;
    basis: number;
    annualizedBasisPct: number;
    marginRequirement?: number;
    longShortIndicator?: 'LONG' | 'SHORT' | 'NEUTRAL';
    openInterest?: number;
    asOf?: string;
};
type DebtSnapshot = {
    isin: string;
    dirtyPrice: number;
    yieldPct: number;
    maturityDate?: string;
    daysToMaturity?: number;
    couponRate?: number;
    asOf?: string;
    source?: string;
    quality?: 'EXACT' | 'FALLBACK' | 'STALE' | string;
    synthetic?: boolean;
};
type ViopContract = { contractCode: string; underlying: string; expiry: string; type: string };
type DebtInstrument = { isin: string; name: string; issuer: string; maturityDate: string };
type MarketCategory = 'EQUITY' | 'CRYPTO' | 'FX' | 'METALS' | 'FUNDS' | 'FUTURES' | 'BOND';
type CompareRow = { time: string; values: Record<string, number> };
type LiveTick = { category: MarketCategory; symbol: string; price: number; changePercent: number; volume: number };
type LivePayload = { ts: string; ticks: LiveTick[] };
type DebtHistoryByIsin = Record<string, DebtSnapshot[]>;

const RANGE_TO_DAYS: Record<'1D' | '1W' | '1M' | '1Y', number> = {
    '1D': 1,
    '1W': 7,
    '1M': 30,
    '1Y': 365,
};
const CATEGORY_LABELS: { id: MarketCategory; label: string }[] = [
    { id: 'EQUITY', label: 'Hisse' },
    { id: 'CRYPTO', label: 'Kripto' },
    { id: 'FX', label: 'Döviz' },
    { id: 'METALS', label: 'Altın' },
    { id: 'FUNDS', label: 'Fonlar' },
    { id: 'FUTURES', label: 'VİOP' },
    { id: 'BOND', label: 'Tahvil' },
];

const STARRED_MAX_FALLBACK = 12;

function heatAssetClassForCategory(category: MarketCategory): string {
    if (category === 'EQUITY') return 'STOCK';
    if (category === 'CRYPTO') return 'CRYPTO';
    if (category === 'FX') return 'FX';
    if (category === 'METALS') return 'METAL';
    if (category === 'FUNDS') return 'FUND';
    return '';
}

function marketKindForCategory(category: MarketCategory): 'EQUITY' | 'CRYPTO' | 'FX' | 'METALS' | 'FUNDS' {
    if (category === 'EQUITY') return 'EQUITY';
    if (category === 'CRYPTO') return 'CRYPTO';
    if (category === 'FX') return 'FX';
    if (category === 'METALS') return 'METALS';
    if (category === 'FUNDS') return 'FUNDS';
    return 'FX';
}

type DisplayCurrency = 'USD' | 'TRY';

function normalizeApiCurrencyCode(raw: string | undefined): DisplayCurrency | null {
    if (!raw || typeof raw !== 'string') return null;
    const u = raw.trim().toUpperCase();
    if (u === 'USD' || u === 'USDT') return 'USD';
    if (u === 'TRY' || u === 'TL' || u === 'TRL') return 'TRY';
    return null;
}

function resolveDisplayCurrency(ins: Pick<MarketInstrument, 'category' | 'currency'>): DisplayCurrency {
    const fromApi = normalizeApiCurrencyCode(ins.currency);
    if (fromApi) return fromApi;
    if (ins.category === 'EQUITY' || ins.category === 'CRYPTO') return 'USD';
    return 'TRY';
}

function currencyGlyph(c: DisplayCurrency): string {
    return c === 'USD' ? '$' : '₺';
}

function normalizeMetaKey(symbol: string | undefined): string {
    return String(symbol ?? '')
        .trim()
        .toUpperCase()
        .replace(/[^A-Z0-9]/g, '');
}

type UsdTryRatePayload = { rate: number; available: boolean };

function effectiveUsdTryRate(payload: UsdTryRatePayload | undefined): number | null {
    if (!payload?.available) return null;
    const n = Number(payload.rate);
    return Number.isFinite(n) && n > 0 ? n : null;
}

/** USD kotasyonlu satırlar: toggle açıkken backend USDTRY kuru ile TL; diğerleri aynı kalır. */
function terminalPriceDisplay(
    ins: Pick<MarketInstrument, 'category' | 'currency' | 'price'>,
    opts: { showUsdInTry: boolean; usdTryRate: number | null }
): { title: string; glyph: string; amount: number } {
    const base = resolveDisplayCurrency(ins);
    if (opts.showUsdInTry && base === 'USD' && opts.usdTryRate != null) {
        return { title: 'TRY', glyph: '₺', amount: ins.price * opts.usdTryRate };
    }
    return { title: base, glyph: currencyGlyph(base), amount: ins.price };
}

function findHeatmapTile(dashboard: MarketDashboard, symbol: string, assetClass: string) {
    const key = normalizeSymbolKey(symbol);
    return dashboard.heatmapTiles.find((t) => normalizeSymbolKey(t.symbol) === key && t.assetClass === assetClass);
}

function sparklineClosesFor(dashboard: MarketDashboard | undefined, symbol: string, assetClass: string): number[] {
    if (!dashboard) return [];
    const key = normalizeSymbolKey(symbol);
    const exact = dashboard.sparklines.find((s) => normalizeSymbolKey(s.symbol) === key && s.assetClass === assetClass);
    if (exact?.closes?.length) return exact.closes;
    const loose = dashboard.sparklines.find((s) => normalizeSymbolKey(s.symbol) === key);
    return loose?.closes ?? [];
}

function resolveListVolume(
    dashboard: MarketDashboard,
    symbol: string,
    assetClass: string,
    price: number,
    tile: ReturnType<typeof findHeatmapTile>
): number | null {
    const w = tile?.layoutWeight;
    if (w != null && Number.isFinite(Number(w)) && Number(w) > 0) {
        return Number(w);
    }
    const volRow = dashboard.volatility.find(
        (v) => normalizeSymbolKey(v.symbol) === normalizeSymbolKey(symbol) && v.assetClass === assetClass
    );
    const dv = volRow?.dailyVolatility;
    if (dv != null && Number.isFinite(Number(dv)) && Number(dv) > 0 && price > 0) {
        return Number(dv) * price * 100_000;
    }
    if (price > 0) {
        return Math.sqrt(Math.max(price, 1e-6));
    }
    return null;
}

function pickPrice(row: LatestPriceRow): number {
    if (!row || row.status === 'NO_DATA') return 0;
    return Number(row.buyPrice ?? row.buy ?? row.price ?? row.sellPrice ?? row.sell ?? 0);
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

function buildFallbackTrendSparkline(price: number, changePercent: number, points = 14): number[] {
    const safePrice = Number.isFinite(price) && price > 0 ? price : 1;
    const ratio = 1 + Number(changePercent ?? 0) / 100;
    const start = ratio > 0 ? safePrice / ratio : safePrice * 0.99;
    return Array.from({ length: points }, (_, i) => {
        const t = i / Math.max(points - 1, 1);
        return start + (safePrice - start) * t;
    });
}

function normalizeTrendSparkline(values: number[], price: number, changePercent: number, points = 14): number[] {
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

function historyRowsToSyntheticCandles(rows: MarketHistoryPoint[]): CandlePoint[] {
    const sorted = [...(rows ?? [])]
        .filter((row) => row && (row.timestamp || row.asOf))
        .sort((a, b) => new Date(a.timestamp ?? a.asOf ?? 0).getTime() - new Date(b.timestamp ?? b.asOf ?? 0).getTime());
    if (!sorted.length) return [];
    return sorted.map((row, idx) => {
        const buy = Number(row.buyPrice ?? Number.NaN);
        const sell = Number(row.sellPrice ?? Number.NaN);
        const closeCandidates = [buy, sell].filter((x) => Number.isFinite(x) && x > 0);
        const close = closeCandidates.length
            ? closeCandidates.reduce((acc, x) => acc + x, 0) / closeCandidates.length
            : 0;
        const prevClose = idx > 0 ? Number(sorted[idx - 1].buyPrice ?? sorted[idx - 1].sellPrice ?? close) : close;
        const safePrev = Number.isFinite(prevClose) && prevClose > 0 ? prevClose : close;
        return {
            t: row.timestamp ?? row.asOf ?? new Date().toISOString(),
            o: safePrev,
            h: Math.max(safePrev, close),
            l: Math.min(safePrev, close),
            c: close,
            v: 0,
        };
    });
}

function movingAverage(candles: { time: string; close: number }[], window: number): { time: string; value: number }[] {
    if (!candles.length || window <= 1) {
        return candles.map((c) => ({ time: c.time, value: c.close }));
    }
    const out: { time: string; value: number }[] = [];
    let sum = 0;
    for (let i = 0; i < candles.length; i += 1) {
        sum += candles[i].close;
        if (i >= window) {
            sum -= candles[i - window].close;
        }
        if (i >= window - 1) {
            out.push({
                time: candles[i].time,
                value: Number((sum / window).toFixed(6)),
            });
        }
    }
    return out;
}

function parseViopContractLabel(contractCode: string): string {
    const m = contractCode.match(/^([A-Z0-9_]+?)(\d{2})(\d{2})$/);
    if (!m) return contractCode;
    const [, rawUnderlying, mm, yy] = m;
    const monthMap: Record<string, string> = {
        '01': 'Oca',
        '02': 'Şub',
        '03': 'Mar',
        '04': 'Nis',
        '05': 'May',
        '06': 'Haz',
        '07': 'Tem',
        '08': 'Ağu',
        '09': 'Eyl',
        '10': 'Eki',
        '11': 'Kas',
        '12': 'Ara',
    };
    const underlyingMap: Record<string, string> = {
        XU030: 'BIST30',
        USDTRY: 'USD/TRY',
        EURTRY: 'EUR/TRY',
        ALTIN: 'Altın',
    };
    const underlying = underlyingMap[rawUnderlying] ?? rawUnderlying;
    const month = monthMap[mm] ?? mm;
    return `${underlying} Vadeli (${month} 20${yy})`;
}

function buildRsi(points: { time: string; close: number }[], period = 14): { time: string; value: number }[] {
    if (points.length <= period) return [];
    const out: { time: string; value: number }[] = [];
    let gains = 0;
    let losses = 0;

    for (let i = 1; i <= period; i += 1) {
        const diff = points[i].close - points[i - 1].close;
        if (diff >= 0) gains += diff;
        else losses -= diff;
    }
    let avgGain = gains / period;
    let avgLoss = losses / period;
    const firstRs = avgLoss === 0 ? 100 : 100 - 100 / (1 + avgGain / avgLoss);
    out.push({ time: points[period].time, value: Number(firstRs.toFixed(2)) });

    for (let i = period + 1; i < points.length; i += 1) {
        const diff = points[i].close - points[i - 1].close;
        const gain = diff > 0 ? diff : 0;
        const loss = diff < 0 ? -diff : 0;
        avgGain = (avgGain * (period - 1) + gain) / period;
        avgLoss = (avgLoss * (period - 1) + loss) / period;
        const rs = avgLoss === 0 ? 100 : 100 - 100 / (1 + avgGain / avgLoss);
        out.push({ time: points[i].time, value: Number(rs.toFixed(2)) });
    }
    return out;
}

function normalizeSymbolKey(symbol: string): string {
    return String(symbol ?? '')
        .trim()
        .replace(/\s+/g, '')
        .toUpperCase();
}

function toContractMonth(label: string): string {
    const m = label.match(/\((.+)\)/);
    return m?.[1] ?? '—';
}

function formatDateTr(value: string | Date | null | undefined): string {
    if (!value) return '—';
    const d = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(d.getTime())) return '—';
    return d.toLocaleDateString('tr-TR');
}

function bondRemainingTone(days: number): { bg: string; color: string } {
    if (!Number.isFinite(days)) return { bg: 'rgba(148,163,184,.18)', color: '#94a3b8' };
    if (days < 90) return { bg: 'rgba(239,68,68,.18)', color: '#ef4444' };
    if (days <= 365) return { bg: 'rgba(249,115,22,.18)', color: '#f97316' };
    return { bg: 'rgba(34,197,94,.18)', color: '#22c55e' };
}

function unwrapData<T>(res: AxiosResponse<T>): T {
    const body = res.data as unknown;
    if (body && typeof body === 'object' && 'data' in (body as object)) {
        return (body as { data: T }).data;
    }
    return body as T;
}

type StarredAssetsApiResponse = {
    maxItems: number;
    selected: { marketType: string; symbol: string; position: number }[];
    resolved: { marketType: string; symbol: string; position: number; defaultFilled: boolean }[];
};

/** Dashboard /api/me/starred-assets ile uyumlu marketType + symbol */
function rowToStarredApiKey(activeCategory: MarketCategory, symbol: string): { marketType: string; symbol: string } | null {
    const sym = normalizeSymbolKey(symbol);
    if (!sym) return null;
    if (activeCategory === 'EQUITY') return { marketType: 'EQUITY', symbol: sym };
    if (activeCategory === 'CRYPTO') return { marketType: 'CRYPTO', symbol: sym };
    if (activeCategory === 'FX') return { marketType: 'FX', symbol: sym };
    if (activeCategory === 'METALS') {
        if (sym === 'ALTIN_TRY') return { marketType: 'METALS', symbol: 'XAU_TRY' };
        return { marketType: 'METALS', symbol: sym };
    }
    if (activeCategory === 'FUNDS') return { marketType: 'FUNDS', symbol: sym };
    return null;
}

function isStarredResolved(data: StarredAssetsApiResponse | undefined, mt: string, sym: string): boolean {
    if (!data?.resolved?.length) return false;
    return data.resolved.some((r) => r.marketType === mt && r.symbol === sym);
}

export function Market() {
    const { theme, tokens } = useTheme();
    const navigate = useNavigate();
    const [activeCategory, setActiveCategory] = useState<MarketCategory>('EQUITY');
    const [showUsdInTry, setShowUsdInTry] = useState(false);
    const [selectedSymbol, setSelectedSymbol] = useState<string>('');
    const [searchTerm, setSearchTerm] = useState('');
    const [isDetailPanelOpen, setIsDetailPanelOpen] = useState(false);
    const [liveOverrides, setLiveOverrides] = useState<Record<string, LiveTick>>({});
    const [range, setRange] = useState<'1D' | '1W' | '1M' | '1Y'>('1M');
    const [showMa, setShowMa] = useState(true);
    const [showRsi, setShowRsi] = useState(true);
    const [bondChartMode, setBondChartMode] = useState<'DUAL' | 'CANDLE'>('DUAL');
    const [viopChartMode, setViopChartMode] = useState<'LINE' | 'CANDLE'>('LINE');
    const [spotChartMode, setSpotChartMode] = useState<'ANALYSIS' | 'CANDLE'>('ANALYSIS');
    const [hoverTile, setHoverTile] = useState<TreemapTile | null>(null);
    const hoverTileRafRef = useRef<number | null>(null);
    const hoverTilePendingRef = useRef<TreemapTile | null>(null);
    const lastHoverTileKeyRef = useRef<string>('');
    const [priceFlash, setPriceFlash] = useState<Record<string, 'up' | 'down'>>({});
    const prevPricesRef = useRef<Record<string, number>>({});
    const [compareSymbols, setCompareSymbols] = useState<string[]>([]);
    const [compareRows, setCompareRows] = useState<CompareRow[]>([]);
    const [loadingCompare, setLoadingCompare] = useState(false);

    const queryClient = useQueryClient();
    const { data: starredAssets } = useQuery({
        queryKey: ['me', 'starred-assets'],
        queryFn: () => financeClient.get<StarredAssetsApiResponse>('/api/me/starred-assets').then((r) => unwrapData(r)),
    });

    const starMutation = useMutation({
        mutationFn: (nextSelected: { marketType: string; symbol: string }[]) =>
            financeClient.put('/api/me/starred-assets', {
                selected: nextSelected.map((s) => ({ marketType: s.marketType, symbol: s.symbol })),
            }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['me', 'starred-assets'] });
        },
        onError: (err: unknown) => {
            const msg = err instanceof Error ? err.message : 'Yıldız güncellenemedi.';
            window.alert(msg);
        },
    });

    const handleStarToggle = useCallback(
        (e: MouseEvent<HTMLButtonElement>, rowSymbol: string) => {
            e.stopPropagation();
            const key = rowToStarredApiKey(activeCategory, rowSymbol);
            if (!key) return;
            const pair = `${key.marketType}:${key.symbol}`;
            const sel = starredAssets?.selected ?? [];
            const maxItems = starredAssets?.maxItems ?? STARRED_MAX_FALLBACK;
            const exists = sel.some((s) => `${s.marketType}:${s.symbol}` === pair);
            let next: { marketType: string; symbol: string }[];
            if (exists) {
                next = sel.filter((s) => `${s.marketType}:${s.symbol}` !== pair);
            } else {
                if (sel.length >= maxItems) {
                    window.alert(`En fazla ${maxItems} varlık yıldızlanabilir.`);
                    return;
                }
                // Yeni yıldızlanan varlığı en üste al: dashboard'da kullanıcı seçimi önce görünsün.
                next = [{ marketType: key.marketType, symbol: key.symbol }, ...sel];
            }
            starMutation.mutate(next);
        },
        [activeCategory, starredAssets, starMutation]
    );

    const {
        data: dashboard,
        isLoading: loadingDashboard,
        error: dashboardError,
        refetch: refetchDashboard,
    } = useQuery({
        queryKey: ['market', 'dashboard', 'terminal'],
        queryFn: () => financeClient.get<MarketDashboard>('/api/market/dashboard').then((r) => unwrapData(r)),
        refetchInterval: 12_000,
    });

    const usdTryRateQueryEnabled =
        showUsdInTry && (activeCategory === 'EQUITY' || activeCategory === 'CRYPTO');
    const { data: usdTryRatePayload } = useQuery({
        queryKey: ['market', 'terminal', 'usd-try-rate'],
        queryFn: () =>
            financeClient.get<UsdTryRatePayload>('/api/market/terminal/usd-try-rate').then((r) => unwrapData(r)),
        enabled: usdTryRateQueryEnabled,
        staleTime: 15_000,
        refetchInterval: usdTryRateQueryEnabled ? 25_000 : false,
    });
    const usdTryRate = usdTryRateQueryEnabled ? effectiveUsdTryRate(usdTryRatePayload) : null;

    /** Orta kolon (grafik + karşılaştırma) yüksekliği — sol/sağ paneller buna göre uzar/kısalır; taşan liste içeride kayar */
    const centerStackRef = useRef<HTMLDivElement | null>(null);
    const [sideRailPx, setSideRailPx] = useState<number | null>(null);
    const sideRailRafRef = useRef<number | null>(null);
    const lastSideRailPxRef = useRef<number | null>(null);
    useLayoutEffect(() => {
        if (activeCategory === 'BOND') {
            setSideRailPx(null);
            lastSideRailPxRef.current = null;
            return;
        }
        if (loadingDashboard) {
            setSideRailPx(null);
            lastSideRailPxRef.current = null;
            return;
        }
        const el = centerStackRef.current;
        if (!el || typeof ResizeObserver === 'undefined') {
            setSideRailPx(null);
            lastSideRailPxRef.current = null;
            return;
        }
        const sync = () => {
            const h = Math.round(el.getBoundingClientRect().height);
            const next = h > 0 ? h : null;
            const prev = lastSideRailPxRef.current;
            // 1-3px oynama/scrollbar jitter'ında state güncelleyip layout döngüsüne girmesin.
            if (prev != null && next != null && Math.abs(prev - next) < 8) return;
            lastSideRailPxRef.current = next;
            if (sideRailRafRef.current != null) cancelAnimationFrame(sideRailRafRef.current);
            sideRailRafRef.current = requestAnimationFrame(() => {
                setSideRailPx(next);
                sideRailRafRef.current = null;
            });
        };
        const ro = new ResizeObserver(sync);
        ro.observe(el);
        sync();
        return () => {
            ro.disconnect();
            if (sideRailRafRef.current != null) {
                cancelAnimationFrame(sideRailRafRef.current);
                sideRailRafRef.current = null;
            }
        };
    }, [loadingDashboard, activeCategory]);

    const [threeColRailSync, setThreeColRailSync] = useState(
        () => typeof window !== 'undefined' && window.matchMedia('(min-width: 981px)').matches
    );
    useEffect(() => {
        if (typeof window === 'undefined') return;
        const mq = window.matchMedia('(min-width: 981px)');
        const apply = () => setThreeColRailSync(mq.matches);
        apply();
        mq.addEventListener('change', apply);
        return () => mq.removeEventListener('change', apply);
    }, []);

    const enableSideRailSync = threeColRailSync && activeCategory !== 'BOND' && activeCategory !== 'FUTURES';
    const sideRailBoxStyle: CSSProperties | undefined =
        enableSideRailSync && sideRailPx != null && sideRailPx > 0
            ? {
                  height: sideRailPx,
                  maxHeight: sideRailPx,
                  minHeight: 0,
                  overflow: 'hidden',
                  boxSizing: 'border-box',
              }
            : undefined;

    useEffect(() => {
        return () => {
            if (hoverTileRafRef.current != null) {
                cancelAnimationFrame(hoverTileRafRef.current);
                hoverTileRafRef.current = null;
            }
        };
    }, []);

    const handleTreemapHover = useCallback((tile: TreemapTile | null) => {
        hoverTilePendingRef.current = tile;
        if (hoverTileRafRef.current != null) return;
        hoverTileRafRef.current = requestAnimationFrame(() => {
            const next = hoverTilePendingRef.current;
            const key = next ? `${next.assetClass}:${next.symbol}` : '';
            if (key !== lastHoverTileKeyRef.current) {
                lastHoverTileKeyRef.current = key;
                setHoverTile(next);
            }
            hoverTileRafRef.current = null;
        });
    }, []);

    const { data: viopLatest = [] } = useQuery({
        queryKey: ['market', 'viop', 'latest', 'terminal'],
        queryFn: () => marketClient.get<ViopSnapshot[]>('/api/market/viop/latest').then((r) => r.data),
        refetchInterval: 15_000,
    });
    const { data: viopContracts = [] } = useQuery({
        queryKey: ['market', 'viop', 'contracts', 'terminal'],
        queryFn: () => marketClient.get<ViopContract[]>('/api/market/viop/contracts').then((r) => r.data),
        refetchInterval: 60_000,
    });
    const { data: viopLatestFromHistory = {} } = useQuery({
        queryKey: ['market', 'viop', 'history-latest', 'terminal', range, viopContracts.length, viopLatest.length],
        enabled: activeCategory === 'FUTURES' && viopLatest.length === 0 && viopContracts.length > 0,
        queryFn: async () => {
            const contracts = [...new Set(viopContracts.map((c) => normalizeSymbolKey(c.contractCode)).filter(Boolean))].slice(0, 120);
            const entries = await Promise.all(
                contracts.map(async (contract) => {
                    try {
                        const rows = await marketClient
                            .get<ViopSnapshot[]>('/api/market/viop/history', {
                                params: { contract, days: Math.max(RANGE_TO_DAYS[range], 365) },
                            })
                            .then((r) => r.data);
                        const latest = [...(rows ?? [])]
                            .filter((x) => Number(x?.price ?? 0) > 0)
                            .sort((a, b) => new Date(a.asOf ?? 0).getTime() - new Date(b.asOf ?? 0).getTime())
                            .at(-1);
                        return latest ? [contract, latest] : null;
                    } catch {
                        return null;
                    }
                })
            );
            return Object.fromEntries(entries.filter((e): e is [string, ViopSnapshot] => Array.isArray(e)));
        },
        refetchInterval: false,
        refetchOnWindowFocus: false,
        staleTime: 10 * 60 * 1000,
    });

    const { data: debtLatest = [] } = useQuery({
        queryKey: ['market', 'debt', 'latest', 'terminal'],
        queryFn: () => marketClient.get<DebtSnapshot[]>('/api/market/debt/latest').then((r) => r.data),
        refetchInterval: 15_000,
    });
    const { data: debtCatalog = [] } = useQuery({
        queryKey: ['market', 'debt', 'catalog', 'terminal'],
        queryFn: () => marketClient.get<DebtInstrument[]>('/api/market/debt/catalog').then((r) => r.data),
        refetchInterval: 60_000,
    });
    const { data: debtHistoryByIsin = {} } = useQuery({
        queryKey: ['market', 'debt', 'history-by-isin', 'terminal', range, debtCatalog.length],
        enabled: activeCategory === 'BOND' && debtCatalog.length > 0,
        queryFn: async () => {
            const uniq = [...new Set(debtCatalog.map((d) => normalizeSymbolKey(d.isin)).filter(Boolean))];
            const entries = await Promise.all(
                uniq.map(async (isin) => {
                    const rows = await marketClient
                        .get<DebtSnapshot[]>('/api/market/debt/history', {
                            params: { isin, days: Math.max(180, RANGE_TO_DAYS[range]) },
                        })
                        .then((r) => r.data);
                    return [isin, rows] as const;
                })
            );
            return Object.fromEntries(entries) as DebtHistoryByIsin;
        },
        refetchInterval: 60_000,
    });


    const fxSpreadMap = useMemo(() => {
        const out: Record<string, number> = {};
        if (!dashboard) return out;
        Object.entries(dashboard.latest.doviz ?? {}).forEach(([symbol, row]) => {
            const buy = Number(row.buyPrice ?? row.buy ?? row.price ?? 0);
            const sell = Number(row.sellPrice ?? row.sell ?? row.price ?? buy);
            out[symbol] = Math.max(0, sell - buy);
        });
        return out;
    }, [dashboard]);

    const debtNameMap = useMemo(() => {
        const m: Record<string, string> = {};
        debtCatalog.forEach((x) => {
            m[normalizeSymbolKey(x.isin)] = x.name;
        });
        return m;
    }, [debtCatalog]);
    const debtMetaMap = useMemo(() => {
        const m: Record<string, DebtInstrument> = {};
        debtCatalog.forEach((x) => {
            m[normalizeSymbolKey(x.isin)] = x;
        });
        return m;
    }, [debtCatalog]);
    const debtLatestMap = useMemo(() => {
        const m: Record<string, DebtSnapshot> = {};
        debtLatest.forEach((x) => {
            m[normalizeSymbolKey(x.isin)] = x;
        });
        return m;
    }, [debtLatest]);
    const viopLatestMap = useMemo(() => {
        const m: Record<string, ViopSnapshot> = {};
        viopLatest.forEach((x) => {
            m[normalizeSymbolKey(x.contractCode)] = x;
        });
        Object.entries(viopLatestFromHistory).forEach(([key, value]) => {
            if (!m[key]) m[key] = value;
        });
        return m;
    }, [viopLatest, viopLatestFromHistory]);
    const volatilityByKey = useMemo(() => {
        const m: Record<string, number> = {};
        (dashboard?.volatility ?? []).forEach((v) => {
            m[`${v.assetClass}:${normalizeSymbolKey(v.symbol)}`] = v.dailyVolatility;
        });
        return m;
    }, [dashboard]);

    const instruments = useMemo<MarketInstrument[]>(() => {
        if (activeCategory === 'FUTURES') {
            const sourceRows = viopLatest.length > 0 ? viopLatest : Object.values(viopLatestFromHistory);
            const merged = new Map<string, ViopSnapshot>();
            sourceRows.forEach((v) => {
                const key = normalizeSymbolKey(v?.contractCode);
                if (!key) return;
                const prev = merged.get(key);
                if (!prev) {
                    merged.set(key, v);
                    return;
                }
                const prevTs = new Date(prev.asOf ?? 0).getTime();
                const nextTs = new Date(v.asOf ?? 0).getTime();
                if (nextTs >= prevTs) merged.set(key, v);
            });
            return [...merged.values()]
                .map((v) => {
                    const pct = v.price ? (Number(v.basis ?? 0) / Number(v.price)) * 100 : 0;
                    return {
                        symbol: normalizeSymbolKey(v.contractCode),
                        category: 'FUTURES' as const,
                        price: Number(v.price ?? 0),
                        changePercent: Number.isFinite(pct) ? pct : 0,
                        trend: pct >= 0 ? ('UP' as const) : ('DOWN' as const),
                        metrics: {
                            basis: Number(v.basis ?? 0),
                            yield: Number(v.annualizedBasisPct ?? 0),
                        },
                        volume: Number(v.openInterest ?? 0),
                    } satisfies MarketInstrument;
                })
                .filter((row) => Number.isFinite(row.price) && row.price > 0)
                .sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent));
        }
        if (activeCategory === 'BOND') {
            const merged = new Map<string, DebtSnapshot>();
            debtLatest.forEach((d) => {
                const key = normalizeSymbolKey(d?.isin);
                if (!key) return;
                const prev = merged.get(key);
                if (!prev) {
                    merged.set(key, d);
                    return;
                }
                const prevTs = new Date(prev.asOf ?? 0).getTime();
                const nextTs = new Date(d.asOf ?? 0).getTime();
                if (nextTs >= prevTs) merged.set(key, d);
            });
            return [...merged.values()]
                .map((d) => {
                    const symbol = normalizeSymbolKey(d.isin);
                    const history = [...(debtHistoryByIsin[symbol] ?? [])].sort(
                        (a, b) => new Date(a.asOf ?? 0).getTime() - new Date(b.asOf ?? 0).getTime()
                    );
                    const validHistoryPrices = history
                        .map((row) => Number(row?.dirtyPrice))
                        .filter((price) => Number.isFinite(price) && price > 0);
                    const currentFromLatest = Number(d.dirtyPrice ?? Number.NaN);
                    const current =
                        Number.isFinite(currentFromLatest) && currentFromLatest > 0
                            ? currentFromLatest
                            : (validHistoryPrices.at(-1) ?? 0);
                    const prev = validHistoryPrices.length > 1 ? validHistoryPrices[validHistoryPrices.length - 2] : 0;
                    const changePercent = prev > 0 && current > 0 ? ((current - prev) / prev) * 100 : 0;
                    return {
                        symbol,
                        category: 'BOND' as const,
                        price: current,
                        changePercent,
                        trend: changePercent >= 0 ? ('UP' as const) : ('DOWN' as const),
                        metrics: { yield: Number(d.yieldPct ?? 0) },
                        volume: d.synthetic || current <= 0 ? null : current * 100,
                    };
                })
                .sort((a, b) => b.price - a.price);
        }
        if (!dashboard) return [];
        const latestMap =
            activeCategory === 'EQUITY'
                ? dashboard.latest.stocks
                : activeCategory === 'CRYPTO'
                ? dashboard.latest.crypto
                : activeCategory === 'FX'
                ? dashboard.latest.doviz
                : activeCategory === 'METALS'
                ? dashboard.latest.metals
                : dashboard.latest.funds;
        const assetClass = heatAssetClassForCategory(activeCategory);
        if (!assetClass) return [];
        const unique = new Map<string, MarketInstrument>();
        Object.entries(latestMap ?? {})
            .filter(([, row]) => row && row.status !== 'NO_DATA')
            .forEach(([rawSymbol, row]) => {
                const symbol = normalizeSymbolKey(rawSymbol);
                if (!symbol) return;
                const spark = sparklineClosesFor(dashboard, symbol, assetClass);
                const tile = findHeatmapTile(dashboard, symbol, assetClass);
                const price = pickPrice(row);
                // Piyasa listesinde veri üretmeyen/0 fiyatlı satırları (örn. EEM-) gizle.
                if (!Number.isFinite(price) || price <= 0) return;
                const volume = resolveListVolume(dashboard, symbol, assetClass, price, tile);
                const tileChange = tile?.changePercent;
                const changePercent = resolveChangePercent(spark, tileChange);
                const r = row as Record<string, unknown>;
                const apiCurrency = [r.currency, r.priceCurrency, r.quoteCurrency].find(
                    (x): x is string => typeof x === 'string' && x.trim().length > 0
                );
                unique.set(symbol, {
                    symbol,
                    category: activeCategory,
                    price,
                    changePercent,
                    trend: changePercent >= 0 ? 'UP' : 'DOWN',
                    volume,
                    metrics: activeCategory === 'FX' ? { basis: fxSpreadMap[symbol] ?? 0 } : undefined,
                    ...(apiCurrency ? { currency: apiCurrency.trim() } : {}),
                });
            });
        return [...unique.values()].sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent));
    }, [activeCategory, dashboard, viopLatest, viopLatestFromHistory, viopContracts, debtLatest, debtHistoryByIsin, fxSpreadMap]);

    useEffect(() => {
        if (!instruments.length) {
            setSelectedSymbol('');
            return;
        }
        const stillExists = instruments.some((i) => i.symbol === selectedSymbol);
        if (!selectedSymbol || !stillExists) setSelectedSymbol(instruments[0].symbol);
    }, [instruments, selectedSymbol]);

    useEffect(() => {
        setCompareSymbols([]);
        setCompareRows([]);
        setSearchTerm('');
        setIsDetailPanelOpen(false);
        setShowUsdInTry(false);
    }, [activeCategory]);
    useEffect(() => {
        if (!isDetailPanelOpen) return;
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') setIsDetailPanelOpen(false);
        };
        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, [isDetailPanelOpen]);

    useEffect(() => {
        if (activeCategory === 'FUTURES') {
            setPriceFlash({});
            return;
        }
        if (!instruments.length) return;
        const nextFlash: Record<string, 'up' | 'down'> = {};
        for (const row of instruments) {
            const prev = prevPricesRef.current[row.symbol];
            if (prev != null && prev !== row.price) {
                nextFlash[row.symbol] = row.price > prev ? 'up' : 'down';
            }
            prevPricesRef.current[row.symbol] = row.price;
        }
        if (Object.keys(nextFlash).length > 0) {
            setPriceFlash(nextFlash);
            const timer = setTimeout(() => setPriceFlash({}), 260);
            return () => clearTimeout(timer);
        }
    }, [instruments, activeCategory]);

    const days = RANGE_TO_DAYS[range];
    const marketType =
        activeCategory === 'EQUITY'
            ? 'EQUITY'
            : activeCategory === 'CRYPTO'
            ? 'CRYPTO'
            : activeCategory === 'FX'
            ? 'FX'
            : activeCategory === 'METALS'
            ? 'METALS'
            : activeCategory === 'FUNDS'
            ? 'FUNDS'
            : null;

    const { data: indicatorData, isLoading: loadingIndicators } = useQuery({
        queryKey: ['market', 'indicators', 'terminal', activeCategory, selectedSymbol, days],
        enabled: Boolean(selectedSymbol && marketType),
        queryFn: () =>
            marketClient
                .get<IndicatorsResponse>('/api/market/indicators', {
                    params: { type: marketType, symbol: selectedSymbol, days, ma: '7,21' },
                })
                .then((r) => r.data),
        refetchInterval: 15_000,
    });

    const { data: batchData, isLoading: loadingCandles } = useQuery({
        queryKey: ['market', 'candles', 'terminal', activeCategory, selectedSymbol, days],
        enabled: Boolean(selectedSymbol && marketType),
        queryFn: async () => {
            if (activeCategory === 'METALS') {
                const symbolCandidates = [selectedSymbol, selectedSymbol === 'ALTIN_TRY' ? 'XAU_TRY' : 'ALTIN_TRY']
                    .map((s) => normalizeSymbolKey(s))
                    .filter(Boolean);
                for (const symbol of symbolCandidates) {
                    try {
                        const rows = await marketClient
                            .get<MarketHistoryPoint[]>('/api/market/metals/history', { params: { symbol, days } })
                            .then((r) => r.data);
                        const candlesFromHistory = historyRowsToSyntheticCandles(rows ?? []);
                        if (candlesFromHistory.length > 0) {
                            return {
                                series: {
                                    [selectedSymbol]: candlesFromHistory,
                                },
                            } satisfies BatchHistoryResponse;
                        }
                    } catch {
                        // try next alias
                    }
                }
            }
            const batch = await marketClient
                .get<BatchHistoryResponse>('/api/market/history/batch', {
                    params: { type: marketType, symbols: selectedSymbol, days },
                })
                .then((r) => r.data);
            const existing = batch?.series?.[selectedSymbol] ?? [];
            const shouldTryAltHistory =
                Boolean(selectedSymbol) && (activeCategory === 'METALS' || activeCategory === 'FUNDS');
            if (!shouldTryAltHistory) {
                return batch;
            }
            const historyPath = activeCategory === 'METALS' ? '/api/market/metals/history' : '/api/market/funds/history';
            const historySymbol =
                activeCategory === 'METALS'
                    ? (selectedSymbol === 'ALTIN_TRY' ? 'XAU_TRY' : selectedSymbol)
                    : selectedSymbol;
            try {
                const rows = await marketClient
                    .get<MarketHistoryPoint[]>(historyPath, { params: { symbol: historySymbol, days } })
                    .then((r) => r.data);
                const fallbackCandles = historyRowsToSyntheticCandles(rows ?? []);
                const existingFlat =
                    existing.length > 0 &&
                    existing.every((c) => Number(c.o) === Number(c.h) && Number(c.h) === Number(c.l) && Number(c.l) === Number(c.c));
                const shouldReplaceWithFallback =
                    fallbackCandles.length > 0 &&
                    (
                        fallbackCandles.length > existing.length ||
                        existing.length < 2 ||
                        (activeCategory === 'METALS' && existingFlat)
                    );
                if (!shouldReplaceWithFallback) {
                    return batch;
                }
                return {
                    series: {
                        ...(batch?.series ?? {}),
                        [selectedSymbol]: fallbackCandles,
                    },
                } satisfies BatchHistoryResponse;
            } catch {
                return batch;
            }
        },
        refetchInterval: 15_000,
    });

    const { data: viopHistory = [], isLoading: loadingViopHistory } = useQuery({
        queryKey: ['market', 'viop-history', selectedSymbol, days],
        enabled: activeCategory === 'FUTURES' && Boolean(selectedSymbol),
        queryFn: async () => {
            const primary = await marketClient
                .get<ViopSnapshot[]>('/api/market/viop/history', {
                    params: { contract: selectedSymbol, days },
                })
                .then((r) => r.data);
            if (primary.length > 0 || days >= 365) {
                return primary;
            }
            return marketClient
                .get<ViopSnapshot[]>('/api/market/viop/history', {
                    params: { contract: selectedSymbol, days: 365 },
                })
                .then((r) => r.data);
        },
        refetchInterval: 15_000,
    });
    const { data: viopOiHistory = [] } = useQuery({
        queryKey: ['market', 'viop-oi-history', selectedSymbol, days],
        enabled: activeCategory === 'FUTURES' && Boolean(selectedSymbol),
        queryFn: () =>
            marketClient
                .get<ViopSnapshot[]>('/api/market/viop/oi-history', {
                    params: { contract: selectedSymbol, days },
                })
                .then((r) => r.data),
        refetchInterval: 15_000,
    });

    const { data: debtHistory = [], isLoading: loadingDebtHistory } = useQuery({
        queryKey: ['market', 'debt-history', selectedSymbol, days],
        enabled: activeCategory === 'BOND' && Boolean(selectedSymbol),
        queryFn: () =>
            marketClient
                .get<DebtSnapshot[]>('/api/market/debt/history', {
                    params: { isin: selectedSymbol, days },
                })
                .then((r) => r.data),
        refetchInterval: 15_000,
    });

    const candles = useMemo(() => {
        if (activeCategory === 'FUTURES') {
            const sorted = [...viopHistory]
                .filter((x) => Number(x.price ?? 0) > 0)
                .sort((a, b) => new Date(a.asOf ?? 0).getTime() - new Date(b.asOf ?? 0).getTime());
            return sorted.map((x, idx) => {
                const close = Number(x.price ?? 0);
                const prev = idx > 0 ? Number(sorted[idx - 1]?.price ?? close) : close;
                return {
                    time: x.asOf ?? new Date().toISOString(),
                    open: prev,
                    high: Math.max(prev, close),
                    low: Math.min(prev, close),
                    close,
                    volume: Number(x.openInterest ?? 0),
                };
            });
        }
        if (activeCategory === 'BOND') {
            const sorted = [...debtHistory].sort((a, b) => new Date(a.asOf ?? 0).getTime() - new Date(b.asOf ?? 0).getTime());
            return sorted.map((x, idx) => {
                const close = Number(x.dirtyPrice ?? 0);
                const prev = idx > 0 ? Number(sorted[idx - 1]?.dirtyPrice ?? close) : close;
                return {
                    time: x.asOf ?? new Date().toISOString(),
                    open: prev,
                    high: Math.max(prev, close),
                    low: Math.min(prev, close),
                    close,
                    volume: close > 0 ? close * 100 : 0,
                };
            });
        }
        const series = batchData?.series?.[selectedSymbol] ?? [];
        if (series.length > 0) {
            if (activeCategory === 'FUNDS') {
                const sorted = [...series].sort((a, b) => new Date(a.t).getTime() - new Date(b.t).getTime());
                return sorted.map((x, idx) => {
                    const close = Number(x.c ?? x.o ?? x.h ?? x.l ?? 0);
                    const prevClose = idx > 0 ? Number(sorted[idx - 1]?.c ?? close) : Number(x.o ?? close);
                    const open = Number.isFinite(prevClose) && prevClose > 0 ? prevClose : close;
                    const high = Number(x.h ?? Math.max(open, close));
                    const low = Number(x.l ?? Math.min(open, close));
                    return {
                        time: x.t,
                        open,
                        high: Number.isFinite(high) && high > 0 ? high : Math.max(open, close),
                        low: Number.isFinite(low) && low > 0 ? low : Math.min(open, close),
                        close,
                        volume: Number(x.v ?? 0),
                    };
                });
            }
            return series.map((x) => ({
                time: x.t,
                open: Number(x.o),
                high: Number(x.h),
                low: Number(x.l),
                close: Number(x.c),
                volume: Number(x.v ?? 0),
            }));
        }
        const closes = indicatorData?.close ?? [];
        return closes.map((p, idx) => {
            const close = Number(p.value);
            const prev = idx > 0 ? Number(closes[idx - 1]?.value ?? close) : close;
            return { time: p.t, open: prev, high: Math.max(prev, close), low: Math.min(prev, close), close, volume: 0 };
        });
    }, [activeCategory, viopHistory, debtHistory, batchData, indicatorData, selectedSymbol]);
    const bondDualPoints = useMemo(() => {
        if (activeCategory !== 'BOND') return [];
        const sorted = [...debtHistory].sort((a, b) => new Date(a.asOf ?? 0).getTime() - new Date(b.asOf ?? 0).getTime());
        return sorted
            .map((x) => ({
                time: x.asOf ?? new Date().toISOString(),
                price: Number(x.dirtyPrice ?? 0),
                yieldPct: Number(x.yieldPct ?? 0),
                volume: Number(x.dirtyPrice ?? 0) > 0 ? Number(x.dirtyPrice ?? 0) * 100 : 0,
            }))
            .filter((x) => Number.isFinite(x.price) && x.price > 0);
    }, [activeCategory, debtHistory]);
    const viopLinePoints = useMemo(() => {
        if (activeCategory !== 'FUTURES') return [];
        const sorted = [...viopHistory]
            .filter((x) => Number(x.price ?? 0) > 0)
            .sort((a, b) => new Date(a.asOf ?? 0).getTime() - new Date(b.asOf ?? 0).getTime());
        const dailyLast = new Map<string, ViopSnapshot>();
        sorted.forEach((x) => {
            const dayKey = String(x.asOf ?? '').slice(0, 10);
            if (!dayKey) return;
            dailyLast.set(dayKey, x);
        });
        return [...dailyLast.values()].map((x) => ({
            time: x.asOf ?? new Date().toISOString(),
            price: Number(x.price ?? 0),
            basis: Number(x.basis ?? 0),
            annualizedBasisPct: Number(x.annualizedBasisPct ?? 0),
            openInterest: Number(x.openInterest ?? 0),
        }));
    }, [activeCategory, viopHistory]);

    const ma7 = useMemo(() => {
        if (indicatorData?.ma?.['7']?.length) {
            return indicatorData.ma['7'].map((p) => ({ time: p.t, value: Number(p.value) }));
        }
        return movingAverage(candles.map((c) => ({ time: c.time, close: c.close })), 7);
    }, [indicatorData, candles]);
    const ma21 = useMemo(() => {
        if (indicatorData?.ma?.['21']?.length) {
            return indicatorData.ma['21'].map((p) => ({ time: p.t, value: Number(p.value) }));
        }
        return movingAverage(candles.map((c) => ({ time: c.time, close: c.close })), 21);
    }, [indicatorData, candles]);

    const rsi14 = useMemo(
        () => buildRsi(candles.map((c) => ({ time: c.time, close: c.close }))),
        [candles]
    );

    const instrumentVms = useMemo<InstrumentVm[]>(() => {
        return instruments.map((ins) => {
            const symbolKey = normalizeSymbolKey(ins.symbol);
            const live = ins.category === 'FUTURES' ? null : liveOverrides[`${ins.category}:${symbolKey}`];
            const livePrice = live && live.price > 0 ? live.price : ins.price;
            const liveChange =
                live && Number.isFinite(Number(live.changePercent)) && Math.abs(Number(live.changePercent)) > 1e-9
                    ? Number(live.changePercent)
                    : ins.changePercent;
            const baseVolume = ins.volume != null && Number.isFinite(Number(ins.volume)) ? Number(ins.volume) : null;
            const liveVolume =
                live && Number.isFinite(Number(live.volume)) && Number(live.volume) > 0
                    ? Number(live.volume)
                    : baseVolume;
            if (ins.category === 'BOND') {
                const debtMeta = debtMetaMap[symbolKey];
                const latestDebt = debtLatestMap[symbolKey];
                const fromIsin = extractMaturityDate(symbolKey);
                const fromApi = latestDebt?.maturityDate ?? debtMeta?.maturityDate;
                const remainingFromIsin = getRemainingDays(symbolKey);
                const remainingDays =
                    latestDebt?.daysToMaturity ??
                    (Number.isFinite(remainingFromIsin) ? remainingFromIsin : undefined);
                return {
                    ...ins,
                    price: livePrice,
                    changePercent: liveChange,
                    volume: liveVolume,
                    type: 'BOND',
                    displayName: formatBondDisplayName(symbolKey),
                    sparkline: normalizeTrendSparkline([], livePrice, liveChange),
                    maturityDate: fromApi ?? (fromIsin ? fromIsin.toISOString() : undefined),
                    daysToMaturity: remainingDays,
                    couponRate: Number(latestDebt?.couponRate ?? 0),
                    yieldToMaturity: Number(latestDebt?.yieldPct ?? ins.metrics?.yield ?? 0),
                    longShort: 'NÖTR',
                };
            }
            if (ins.category === 'FUTURES') {
                const contractLabel = parseViopContractLabel(symbolKey);
                const latestViop = viopLatestMap[symbolKey];
                const basis = Number(ins.metrics?.basis ?? 0);
                return {
                    ...ins,
                    price: livePrice,
                    changePercent: liveChange,
                    volume: liveVolume,
                    type: 'FUTURES',
                    displayName: contractLabel,
                    sparkline: normalizeTrendSparkline([], livePrice, liveChange),
                    contractMonth: latestViop?.contractMonth ?? toContractMonth(contractLabel),
                    expiryDate: latestViop?.expiryDate,
                    marginRequirement: Number(latestViop?.marginRequirement ?? (ins.price * 0.12).toFixed(2)),
                    longShort:
                        latestViop?.longShortIndicator === 'LONG'
                            ? 'LONG'
                            : latestViop?.longShortIndicator === 'SHORT'
                            ? 'SHORT'
                            : basis > 0
                            ? 'LONG'
                            : basis < 0
                            ? 'SHORT'
                            : 'NÖTR',
                };
            }
            const ac = heatAssetClassForCategory(ins.category);
            const spark = dashboard && ac ? sparklineClosesFor(dashboard, symbolKey, ac) : [];
            return {
                ...ins,
                price: livePrice,
                changePercent: liveChange,
                volume: liveVolume,
                type: 'STOCK',
                displayName: formatAssetLabel(symbolKey, marketKindForCategory(ins.category)),
                sparkline: normalizeTrendSparkline(spark, livePrice, liveChange),
                longShort: ins.trend === 'UP' ? 'LONG' : 'SHORT',
            };
        });
    }, [instruments, dashboard, debtMetaMap, debtLatestMap, viopLatestMap, debtNameMap, activeCategory, liveOverrides]);

    const filteredInstruments = useMemo(() => {
        const q = searchTerm.trim().toUpperCase();
        if (!q) return instrumentVms;
        return instrumentVms.filter(
            (x) =>
                x.symbol.includes(q) ||
                x.displayName.toUpperCase().includes(q) ||
                String(x.type).includes(q)
        );
    }, [instrumentVms, searchTerm]);
    const selectedInstrumentVm = useMemo(
        () => instrumentVms.find((x) => x.symbol === selectedSymbol) ?? instrumentVms[0] ?? null,
        [instrumentVms, selectedSymbol]
    );
    const virtualRows = useMemo(() => {
        return {
            rowHeight: 52,
            viewport: 500,
            start: 0,
            end: filteredInstruments.length,
            totalHeight: filteredInstruments.length * 52,
            topSpacer: 0,
            rows: filteredInstruments,
        };
    }, [filteredInstruments]);
    const sparklinePath = useCallback((values: number[]) => {
        if (!values.length) return '';
        const min = Math.min(...values);
        const max = Math.max(...values);
        const range = Math.max(max - min, 1e-6);
        const topPad = 2;
        const bottomPad = 2;
        const chartHeight = 24 - topPad - bottomPad;
        return values
            .map((v, i) => {
                const x = (i / Math.max(values.length - 1, 1)) * 100;
                const y = topPad + (1 - (v - min) / range) * chartHeight;
                return `${x},${y}`;
            })
            .join(' ');
    }, []);

    const hero = useMemo(() => {
        const current = instruments.find((x) => x.symbol === selectedSymbol) ?? instruments[0];
        if (!current) return null;
        const highs = candles.map((c) => c.high);
        const lows = candles.map((c) => c.low);
        return {
            ...current,
            high: highs.length ? Math.max(...highs) : current.price,
            low: lows.length ? Math.min(...lows) : current.price,
        };
    }, [instruments, selectedSymbol, candles]);
    const heroScaled = useMemo(() => {
        if (!hero) return null;
        const pd = terminalPriceDisplay(hero, { showUsdInTry, usdTryRate });
        const isUsdConv = resolveDisplayCurrency(hero) === 'USD' && showUsdInTry && usdTryRate != null;
        const mult = isUsdConv ? usdTryRate : 1;
        return { ...pd, high: hero.high * mult, low: hero.low * mult };
    }, [hero, showUsdInTry, usdTryRate]);
    const drawerInstrumentPrice = useMemo(
        () =>
            selectedInstrumentVm
                ? terminalPriceDisplay(selectedInstrumentVm, { showUsdInTry, usdTryRate })
                : null,
        [selectedInstrumentVm, showUsdInTry, usdTryRate]
    );
    const selectedInstrumentMeta = useMemo(() => {
        if (!selectedInstrumentVm) return null;
        const key = normalizeMetaKey(selectedInstrumentVm.symbol);

        if (selectedInstrumentVm.category === 'EQUITY') {
            const meta = instrumentMeta[key];
            return meta
                ? {
                      title: 'Hisse Bilgisi',
                      rows: [
                          ['Şirket', meta.name],
                          ['Sektör', meta.sector],
                          ['Borsa', meta.exchange],
                          ['Ülke', meta.country ?? '—'],
                      ],
                      description: meta.description,
                  }
                : null;
        }
        if (selectedInstrumentVm.category === 'CRYPTO') {
            const meta = cryptoMeta[key];
            return meta
                ? {
                      title: 'Kripto Bilgisi',
                      rows: [
                          ['Ad', meta.name],
                          ['Sembol', meta.symbol],
                          ['Kategori', meta.category],
                      ],
                      description: meta.description,
                  }
                : null;
        }
        if (selectedInstrumentVm.category === 'FX') {
            const meta = fxMeta[key];
            return meta
                ? {
                      title: 'Döviz Paritesi',
                      rows: [
                          ['Baz Varlık', `${meta.baseName} (${meta.baseCountry})`],
                          ['Karşılık', `${meta.quoteName} (${meta.quoteCountry})`],
                      ],
                      description: meta.description,
                  }
                : null;
        }
        if (selectedInstrumentVm.category === 'FUNDS') {
            const meta = etfMeta[key];
            return meta
                ? {
                      title: 'Fon / ETF Bilgisi',
                      rows: [
                          ['Fon', meta.name],
                          ['Kategori', meta.category],
                          ['Sağlayıcı', meta.provider],
                      ],
                      description: meta.description,
                  }
                : null;
        }
        if (selectedInstrumentVm.category === 'BOND') {
            const days = selectedInstrumentVm.daysToMaturity ?? getRemainingDays(selectedInstrumentVm.symbol);
            const meta = getBondMeta(Number.isFinite(days) ? days : 365);
            return {
                title: 'Tahvil Bilgisi',
                rows: [
                    ['İhraççı', meta.issuer],
                    ['Tür', meta.type],
                    ['Kategori', meta.category],
                    ['Risk', meta.risk],
                ],
                description: meta.description,
            };
        }
        return null;
    }, [selectedInstrumentVm]);
    const futuresHeaderMetrics = useMemo(() => {
        if (activeCategory !== 'FUTURES' || !selectedSymbol) return null;
        const latest = viopLatest.find((x) => x.contractCode === selectedSymbol);
        const oiSeries = [...viopOiHistory].sort((a, b) => new Date(a.asOf ?? 0).getTime() - new Date(b.asOf ?? 0).getTime());
        const lastOi = oiSeries.length
            ? Number(oiSeries[oiSeries.length - 1]?.openInterest ?? latest?.openInterest ?? 0)
            : Number(latest?.openInterest ?? 0);
        const prevOi = oiSeries.length > 1 ? Number(oiSeries[oiSeries.length - 2]?.openInterest ?? 0) : lastOi;
        const oiChangePct = prevOi > 0 ? ((lastOi - prevOi) / prevOi) * 100 : 0;
        return {
            basis: Number(latest?.basis ?? 0),
            carry: Number(latest?.annualizedBasisPct ?? 0),
            oiChangePct,
            oi: lastOi,
        };
    }, [activeCategory, selectedSymbol, viopLatest, viopOiHistory]);

    const markers = useMemo(() => [], []);

    const topGainers = useMemo(
        () => [...instruments].sort((a, b) => b.changePercent - a.changePercent).slice(0, 5),
        [instruments]
    );
    const topLosers = useMemo(
        () => [...instruments].sort((a, b) => a.changePercent - b.changePercent).slice(0, 5),
        [instruments]
    );
    const futuresTopMovers = useMemo(
        () => [...instruments].sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent)).slice(0, 5),
        [instruments]
    );
    const futuresHighestCarry = useMemo(
        () => [...instruments].sort((a, b) => Number(b.metrics?.yield ?? 0) - Number(a.metrics?.yield ?? 0)).slice(0, 5),
        [instruments]
    );

    const cryptoDominance = useMemo(() => {
        if (activeCategory !== 'CRYPTO' || !dashboard) return [] as { symbol: string; dominancePct: number }[];
        const cryptoTiles = dashboard.heatmapTiles.filter((t) => t.assetClass === 'CRYPTO');
        const total = cryptoTiles.reduce((acc, t) => acc + Number(t.layoutWeight ?? 0), 0);
        if (total <= 0) return [] as { symbol: string; dominancePct: number }[];
        return cryptoTiles
            .map((t) => ({
                symbol: t.symbol,
                dominancePct: (Number(t.layoutWeight ?? 0) / total) * 100,
            }))
            .sort((a, b) => b.dominancePct - a.dominancePct)
            .slice(0, 5);
    }, [activeCategory, dashboard]);

    const lineWidthBySymbol = useMemo(() => {
        const out: Record<string, number> = {};
        const slice = compareSymbols.slice(0, 4);
        const vols = slice.map((s) => Math.abs(instruments.find((x) => x.symbol === s)?.changePercent ?? 0));
        const max = Math.max(...vols, 0.0001);
        for (const sym of slice) {
            const v = Math.abs(instruments.find((x) => x.symbol === sym)?.changePercent ?? 0);
            out[sym] = 2 + Math.min(2.5, (v / max) * 2.5);
        }
        return out;
    }, [compareSymbols, instruments]);

    const loadCompare = useCallback(() => {
        if ((activeCategory !== 'FUTURES' && !marketType) || compareSymbols.length < 2) {
            setCompareRows([]);
            return;
        }
        setLoadingCompare(true);
        const symbols = compareSymbols.slice(0, 4);
        const request =
            activeCategory === 'FUTURES'
                ? Promise.all(
                      symbols.map((sym) =>
                          marketClient
                              .get<ViopSnapshot[]>('/api/market/viop/history', {
                                  params: { contract: sym, days },
                              })
                              .then((res) => [sym, res.data] as const)
                      )
                  ).then((rows) => {
                      const series: Record<string, CandlePoint[]> = {};
                      rows.forEach(([sym, data]) => {
                          series[sym] = (data ?? []).map((x) => ({
                              t: x.asOf ?? new Date().toISOString(),
                              o: Number(x.price ?? 0),
                              h: Number(x.price ?? 0),
                              l: Number(x.price ?? 0),
                              c: Number(x.price ?? 0),
                              v: Number(x.openInterest ?? 0),
                          }));
                      });
                      return { series } satisfies BatchHistoryResponse;
                  })
                : marketClient
                      .get<BatchHistoryResponse>('/api/market/history/batch', {
                          params: {
                              type: marketType,
                              symbols: symbols.join(','),
                              days,
                          },
                      })
                      .then((res) => res.data);

        request
            .then((batch) => {
                const byDate: Record<string, Record<string, number>> = {};
                Object.entries(batch.series ?? {}).forEach(([sym, candlesBySym]) => {
                    candlesBySym.forEach((c) => {
                        const date = new Date(c.t).toISOString().slice(0, 10);
                        if (!byDate[date]) byDate[date] = {};
                        byDate[date][sym] = Number(c.c);
                    });
                });
                const dates = Object.keys(byDate).sort();
                if (!dates.length) {
                    setCompareRows([]);
                    return;
                }
                const first: Record<string, number> = {};
                symbols.forEach((sym) => {
                    const d = dates.find((dt) => byDate[dt][sym] != null);
                    if (d != null) first[sym] = byDate[d][sym];
                });
                const rows = dates.map((date) => {
                    const values: Record<string, number> = {};
                    symbols.forEach((sym) => {
                        const v = byDate[date][sym];
                        const base = first[sym];
                        values[sym] = base && v != null ? Math.round((v / base) * 1000) / 10 : Number.NaN;
                    });
                    return { time: date, values };
                });
                setCompareRows(rows);
            })
            .catch(() => setCompareRows([]))
            .finally(() => setLoadingCompare(false));
    }, [compareSymbols, days, marketType, activeCategory]);

    useEffect(() => {
        if (compareSymbols.length >= 2) {
            loadCompare();
        } else {
            setCompareRows([]);
        }
    }, [compareSymbols, loadCompare]);

    usePolling(() => {
        void refetchDashboard();
    }, 12_000);
    useEffect(() => {
        const base = (import.meta.env.VITE_MARKET_API_URL || 'http://localhost:8083').replace(/^http/i, 'ws');
        const wsUrl = `${base}/ws/market`;
        let socket: WebSocket | null = null;
        let isCancelled = false;
        const connect = () => {
            if (isCancelled) return;
            socket = new WebSocket(wsUrl);
            socket.onmessage = (event) => {
                try {
                    const payload = JSON.parse(String(event.data)) as LivePayload;
                    const next: Record<string, LiveTick> = {};
                    (payload?.ticks ?? []).forEach((t) => {
                        const cat = t.category as MarketCategory;
                        next[`${cat}:${normalizeSymbolKey(t.symbol)}`] = {
                            category: cat,
                            symbol: normalizeSymbolKey(t.symbol),
                            price: Number(t.price ?? 0),
                            changePercent: Number(t.changePercent ?? 0),
                            volume: Number(t.volume ?? 0),
                        };
                    });
                    if (Object.keys(next).length) {
                        setLiveOverrides((prev) => ({ ...prev, ...next }));
                    }
                } catch {
                    // swallow malformed WS payloads, polling remains fallback
                }
            };
            socket.onclose = () => {
                if (!isCancelled) {
                    window.setTimeout(connect, 1500);
                }
            };
        };
        connect();
        return () => {
            isCancelled = true;
            socket?.close();
        };
    }, []);

    const errMsg = dashboardError instanceof Error ? dashboardError.message : '';
    const terminalVars = useMemo(
        () =>
            ({
                '--terminal-bg': tokens.bg,
                '--terminal-bg-accent': theme === 'dark' ? 'rgba(37, 99, 235, 0.16)' : 'rgba(59, 130, 246, 0.1)',
                '--terminal-card-bg': tokens.bgCard,
                '--terminal-border': tokens.border,
                '--terminal-text': tokens.text,
                '--terminal-muted': tokens.textMuted,
                '--terminal-btn-bg': tokens.inputBg,
                '--terminal-btn-active-bg': theme === 'dark' ? 'rgba(8, 47, 73, 0.8)' : 'rgba(29, 78, 216, 0.14)',
                '--terminal-btn-active-text': tokens.text,
                '--terminal-table-head-bg': theme === 'dark' ? 'rgba(15, 23, 42, 0.98)' : '#f8fafc',
                '--terminal-hover-bg': theme === 'dark' ? 'rgba(30, 41, 59, 0.72)' : 'rgba(148, 163, 184, 0.16)',
                '--terminal-active-row-bg': theme === 'dark' ? 'rgba(30, 58, 138, 0.28)' : 'rgba(59, 130, 246, 0.14)',
            }) as CSSProperties,
        [theme, tokens]
    );

    if (errMsg) {
        return <div className="terminal-page">Piyasa terminali hatası: {errMsg}</div>;
    }

    return (
        <div className="terminal-page" style={terminalVars}>
            <div className="terminal-hero">
                <div>
                    <div style={{ fontSize: 12, color: tokens.textMuted, marginBottom: 2 }}>Seçili Enstrüman</div>
                    <div style={{ fontSize: 22, fontWeight: 700 }}>
                        {hero
                            ? activeCategory === 'FUTURES'
                                ? parseViopContractLabel(hero.symbol)
                                : activeCategory === 'BOND'
                                ? debtNameMap[hero.symbol] ?? hero.symbol
                                : formatAssetLabel(hero.symbol, marketKindForCategory(activeCategory))
                            : '—'}
                    </div>
                    <div style={{ fontSize: 12, color: tokens.textMuted }}>{hero?.symbol ?? ''}</div>
                </div>
                <div className="terminal-hero-price" title={heroScaled?.title}>
                    {heroScaled ? (
                        <>
                            <span style={{ opacity: 0.8 }}>{heroScaled.glyph}</span>{' '}
                            {heroScaled.amount.toLocaleString('tr-TR', { maximumFractionDigits: 4 })}
                        </>
                    ) : (
                        '—'
                    )}
                </div>
                <div className={`terminal-hero-change ${hero?.trend === 'UP' ? 'up' : 'down'}`}>
                    {(hero?.changePercent ?? 0) >= 0 ? '+' : ''}
                    {(hero?.changePercent ?? 0).toFixed(2)}% {(hero?.trend === 'UP' ? '↑' : '↓')}
                </div>
                <div style={{ textAlign: 'right', fontSize: 12, color: tokens.textMuted }}>
                    <div title={heroScaled?.title}>
                        H:{' '}
                        <span style={{ opacity: 0.8 }}>{heroScaled?.glyph ?? ''}</span>
                        {heroScaled ? ' ' : ''}
                        {(heroScaled?.high ?? hero?.high ?? 0).toLocaleString('tr-TR', { maximumFractionDigits: 4 })}
                    </div>
                    <div title={heroScaled?.title}>
                        L:{' '}
                        <span style={{ opacity: 0.8 }}>{heroScaled?.glyph ?? ''}</span>
                        {heroScaled ? ' ' : ''}
                        {(heroScaled?.low ?? hero?.low ?? 0).toLocaleString('tr-TR', { maximumFractionDigits: 4 })}
                    </div>
                    {activeCategory === 'FUTURES' && futuresHeaderMetrics ? (
                        <>
                            <div>Baz: {futuresHeaderMetrics.basis.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}</div>
                            <div>Yıllık Taşıma: %{futuresHeaderMetrics.carry.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}</div>
                            <div>
                                OI Değişim: {futuresHeaderMetrics.oiChangePct >= 0 ? '+' : ''}
                                {futuresHeaderMetrics.oiChangePct.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%
                            </div>
                        </>
                    ) : null}
                </div>
            </div>

            {loadingDashboard ? (
                <div className="terminal-card">Piyasa terminali yükleniyor...</div>
            ) : (
                <>
                    <div className="terminal-grid">
                        <div className="terminal-card terminal-left-panel" style={sideRailBoxStyle}>
                            <div
                                style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between',
                                    gap: 8,
                                    marginBottom: 10,
                                }}
                            >
                                <div style={{ fontWeight: 700 }}>Piyasa Listesi</div>
                                {activeCategory === 'EQUITY' || activeCategory === 'CRYPTO' ? (
                                    <button
                                        type="button"
                                        className={`terminal-btn ${showUsdInTry ? 'active' : ''}`}
                                        onClick={() => setShowUsdInTry((v) => !v)}
                                    >
                                        {showUsdInTry ? 'USD göster' : 'TL’ye çevir'}
                                    </button>
                                ) : null}
                            </div>
                            <input
                                className="terminal-search"
                                placeholder="Sembol / enstrüman ara"
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                            />
                            <div
                                className="terminal-table-wrap"
                            >
                                <table className="terminal-data-table">
                                    <thead>
                                        <tr>
                                            <th aria-label="Yıldız" style={{ width: 36 }}>
                                                ★
                                            </th>
                                            <th>Enstrüman</th>
                                            <th>Fiyat</th>
                                            <th>%</th>
                                            <th>Trend</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {virtualRows.topSpacer > 0 ? (
                                            <tr style={{ height: virtualRows.topSpacer }}>
                                                <td colSpan={5} />
                                            </tr>
                                        ) : null}
                                        {virtualRows.rows.map((row) => {
                                            const sk = rowToStarredApiKey(activeCategory, row.symbol);
                                            const starFilled = sk
                                                ? isStarredResolved(starredAssets, sk.marketType, sk.symbol)
                                                : false;
                                            const rowPriceDisp = terminalPriceDisplay(row, { showUsdInTry, usdTryRate });
                                            const bondDays = row.category === 'BOND' ? getRemainingDays(row.symbol) : NaN;
                                            const daysValue = row.category === 'BOND'
                                                ? (row.daysToMaturity ?? (Number.isFinite(bondDays) ? bondDays : undefined))
                                                : undefined;
                                            const bondTone = bondRemainingTone(Number(daysValue));
                                            const bondTooltip =
                                                row.category === 'BOND'
                                                    ? `ISIN: ${row.symbol}\nVade: ${formatDateTr(row.maturityDate ?? extractMaturityDate(row.symbol))}\nKalan Gün: ${
                                                          daysValue != null ? daysValue : '—'
                                                      }\nFiyat: ${rowPriceDisp.glyph} ${rowPriceDisp.amount.toLocaleString('tr-TR', {
                                                          maximumFractionDigits: 4,
                                                      })}`
                                                    : undefined;
                                            return (
                                            <tr
                                                key={row.symbol}
                                                className={`${selectedSymbol === row.symbol ? 'active' : ''} ${
                                                    priceFlash[row.symbol] === 'up'
                                                        ? 'terminal-flash-up'
                                                        : priceFlash[row.symbol] === 'down'
                                                        ? 'terminal-flash-down'
                                                        : ''
                                                }`}
                                                onClick={() => {
                                                    setSelectedSymbol(row.symbol);
                                                    setIsDetailPanelOpen(true);
                                                }}
                                                title={bondTooltip}
                                            >
                                                <td style={{ textAlign: 'center', width: 36 }} onClick={(e) => e.stopPropagation()}>
                                                    {sk ? (
                                                        <button
                                                            type="button"
                                                            className="terminal-star-btn"
                                                            aria-label={starFilled ? 'Yıldızı kaldır' : 'Dashboard’da göster'}
                                                            disabled={starMutation.isPending}
                                                            onClick={(e) => handleStarToggle(e, row.symbol)}
                                                        >
                                                            <Star
                                                                size={14}
                                                                fill={starFilled ? tokens.accent : 'transparent'}
                                                                color={starFilled ? tokens.accent : tokens.textMuted}
                                                            />
                                                        </button>
                                                    ) : (
                                                        <span style={{ color: tokens.textMuted, fontSize: 11 }}>—</span>
                                                    )}
                                                </td>
                                                <td className="terminal-instrument-cell">
                                                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', rowGap: 4 }}>
                                                        <AssetLogo
                                                            src={getDynamicLogoUrl(
                                                                row.symbol,
                                                                row.category === 'BOND' || row.category === 'FUTURES'
                                                                    ? 'EQUITY'
                                                                    : marketKindForCategory(row.category)
                                                            )}
                                                            alt={`${row.symbol} logo`}
                                                            fallbackIcon={TrendingUp}
                                                            fallbackColor={tokens.textMuted}
                                                            size={20}
                                                        />
                                                        <div style={{ fontWeight: 700, minWidth: 0, lineHeight: 1.2, wordBreak: 'break-word' }}>
                                                            {row.category === 'BOND' ? row.displayName : row.symbol}
                                                        </div>
                                                    </div>
                                                    {row.category === 'BOND' ? (
                                                        <>
                                                            <div style={{ fontSize: 11, color: tokens.textMuted, lineHeight: 1.25, marginTop: 2, wordBreak: 'break-word' }}>
                                                                ISIN: {row.symbol}
                                                            </div>
                                                            <div style={{ marginTop: 4 }}>
                                                                <span
                                                                    className="instrument-highlight-chip"
                                                                    style={{ background: bondTone.bg, color: bondTone.color }}
                                                                >
                                                                    {daysValue != null ? `Vade: ${daysValue} gün` : `Vade: ${formatDateTr(row.maturityDate)}`}
                                                                </span>
                                                            </div>
                                                        </>
                                                    ) : (
                                                        <div style={{ fontSize: 11, color: tokens.textMuted, lineHeight: 1.25, marginTop: 2, wordBreak: 'break-word' }}>
                                                            {row.displayName}
                                                        </div>
                                                    )}
                                                </td>
                                                <td title={rowPriceDisp.title}>
                                                    <span style={{ opacity: 0.8 }}>{rowPriceDisp.glyph}</span>{' '}
                                                    {rowPriceDisp.amount.toLocaleString('tr-TR', {
                                                        maximumFractionDigits: 4,
                                                    })}
                                                </td>
                                                <td style={{ color: row.changePercent >= 0 ? '#22c55e' : '#ef4444' }}>
                                                    {row.changePercent >= 0 ? '+' : ''}
                                                    {row.changePercent.toFixed(2)}%
                                                </td>
                                                <td>
                                                    <svg className="inline-spark" viewBox="0 0 100 24" preserveAspectRatio="none" aria-hidden="true">
                                                        <polyline
                                                            points={sparklinePath(row.sparkline.slice(-14))}
                                                            fill="none"
                                                            stroke={row.changePercent >= 0 ? '#22c55e' : '#ef4444'}
                                                            strokeWidth="2"
                                                        />
                                                    </svg>
                                                </td>
                                            </tr>
                                            );
                                        })}
                                        {Math.max(0, virtualRows.totalHeight - virtualRows.topSpacer - virtualRows.rows.length * virtualRows.rowHeight) > 0 ? (
                                            <tr
                                                style={{
                                                    height: Math.max(
                                                        0,
                                                        virtualRows.totalHeight -
                                                            virtualRows.topSpacer -
                                                            virtualRows.rows.length * virtualRows.rowHeight
                                                    ),
                                                }}
                                            >
                                                <td colSpan={5} />
                                            </tr>
                                        ) : null}
                                    </tbody>
                                </table>
                            </div>
                        </div>

                        <div ref={centerStackRef} className="terminal-center-stack">
                        <div className="terminal-card terminal-center-panel">
                            <div className="terminal-btn-row terminal-category-row" style={{ marginBottom: 10 }}>
                                {CATEGORY_LABELS.map((cat) => (
                                    <button
                                        key={cat.id}
                                        type="button"
                                        className={`terminal-btn ${activeCategory === cat.id ? 'active' : ''}`}
                                        onClick={() => setActiveCategory(cat.id)}
                                    >
                                        {cat.label}
                                    </button>
                                ))}
                            </div>
                            <div className="terminal-controls">
                                <div className="terminal-btn-row">
                                    {(['1D', '1W', '1M', '1Y'] as const).map((r) => (
                                        <button key={r} type="button" className={`terminal-btn ${range === r ? 'active' : ''}`} onClick={() => setRange(r)}>
                                            {r}
                                        </button>
                                    ))}
                                </div>
                                {activeCategory === 'BOND' ? (
                                    <div className="terminal-btn-row">
                                        <button
                                            type="button"
                                            className={`terminal-btn ${bondChartMode === 'DUAL' ? 'active' : ''}`}
                                            onClick={() => setBondChartMode('DUAL')}
                                        >
                                            Tahvil Analiz
                                        </button>
                                        <button
                                            type="button"
                                            className={`terminal-btn ${bondChartMode === 'CANDLE' ? 'active' : ''}`}
                                            onClick={() => setBondChartMode('CANDLE')}
                                        >
                                            Detaylı Mum
                                        </button>
                                    </div>
                                ) : null}
                                {activeCategory === 'FUTURES' ? (
                                    <div className="terminal-btn-row">
                                        <button
                                            type="button"
                                            className={`terminal-btn ${viopChartMode === 'LINE' ? 'active' : ''}`}
                                            onClick={() => setViopChartMode('LINE')}
                                        >
                                            VİOP Analiz
                                        </button>
                                        <button
                                            type="button"
                                            className={`terminal-btn ${viopChartMode === 'CANDLE' ? 'active' : ''}`}
                                            onClick={() => setViopChartMode('CANDLE')}
                                        >
                                            Detaylı Mum
                                        </button>
                                    </div>
                                ) : null}
                                {activeCategory !== 'FUTURES' && activeCategory !== 'BOND' ? (
                                    <div className="terminal-btn-row">
                                        <button
                                            type="button"
                                            className={`terminal-btn ${spotChartMode === 'ANALYSIS' ? 'active' : ''}`}
                                            onClick={() => setSpotChartMode('ANALYSIS')}
                                        >
                                            Piyasa Analiz
                                        </button>
                                        <button
                                            type="button"
                                            className={`terminal-btn ${spotChartMode === 'CANDLE' ? 'active' : ''}`}
                                            onClick={() => setSpotChartMode('CANDLE')}
                                        >
                                            Detaylı Mum
                                        </button>
                                    </div>
                                ) : null}
                                <div className="terminal-btn-row">
                                    <button type="button" className={`terminal-btn ${showMa ? 'active' : ''}`} onClick={() => setShowMa((v) => !v)}>
                                        MA
                                    </button>
                                    <button type="button" className={`terminal-btn ${showRsi ? 'active' : ''}`} onClick={() => setShowRsi((v) => !v)}>
                                        RSI
                                    </button>
                                </div>
                            </div>
                            {activeCategory === 'BOND' && bondChartMode === 'DUAL' ? (
                                <BondTerminalChart
                                    points={bondDualPoints}
                                    ma7={ma7}
                                    ma21={ma21}
                                    showMa={showMa}
                                    loading={loadingDebtHistory}
                                    trendLabel={hero?.trend}
                                    timeframeLabel={range}
                                    tokens={{
                                        bgCard: tokens.bgCard,
                                        border: tokens.border,
                                        text: tokens.text,
                                        textMuted: tokens.textMuted,
                                    }}
                                />
                            ) : activeCategory === 'FUTURES' && viopChartMode === 'LINE' ? (
                                <ViopTerminalChart
                                    points={viopLinePoints}
                                    ma7={ma7}
                                    ma21={ma21}
                                    showMa={showMa}
                                    loading={loadingViopHistory}
                                    trendLabel={hero?.trend}
                                    timeframeLabel={range}
                                    tokens={{
                                        bgCard: tokens.bgCard,
                                        border: tokens.border,
                                        text: tokens.text,
                                        textMuted: tokens.textMuted,
                                    }}
                                />
                            ) : activeCategory !== 'FUTURES' && activeCategory !== 'BOND' && spotChartMode === 'ANALYSIS' ? (
                                <SpotTerminalChart
                                    title={`${CATEGORY_LABELS.find((c) => c.id === activeCategory)?.label ?? 'Piyasa'} Analiz`}
                                    candles={candles}
                                    ma7={ma7}
                                    ma21={ma21}
                                    showMa={showMa}
                                    loading={loadingCandles || loadingIndicators}
                                    trendLabel={hero?.trend}
                                    timeframeLabel={range}
                                    tokens={{
                                        bgCard: tokens.bgCard,
                                        border: tokens.border,
                                        text: tokens.text,
                                        textMuted: tokens.textMuted,
                                    }}
                                />
                            ) : (
                                <MarketTerminalChart
                                    candles={candles}
                                    ma7={ma7}
                                    ma21={ma21}
                                    rsi14={rsi14}
                                    showMa={showMa}
                                    showRsi={showRsi}
                                    markers={markers}
                                    loading={loadingCandles || loadingIndicators || loadingViopHistory || loadingDebtHistory}
                                    symbol={selectedSymbol}
                                    trendLabel={hero?.trend}
                                    timeframeLabel={range}
                                    tokens={{
                                        bg: tokens.bg,
                                        bgCard: tokens.bgCard,
                                        border: tokens.border,
                                        text: tokens.text,
                                        textMuted: tokens.textMuted,
                                    }}
                                />
                            )}
                        </div>
                        <div className="terminal-card terminal-center-comparison">
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                                <strong style={{ fontSize: 14 }}>Karşılaştırma Grafiği (Baz 100)</strong>
                                <div style={{ fontSize: 12, color: '#94a3b8' }}>Aynı kategoriden 2-4 sembol seç</div>
                            </div>
                            {marketType ? <div className="terminal-compare-selector">
                                {instruments.slice(0, 12).map((ins) => {
                                    const checked = compareSymbols.includes(ins.symbol);
                                    return (
                                        <label key={`cmp-${ins.symbol}`} className="terminal-chip">
                                            <input
                                                type="checkbox"
                                                checked={checked}
                                                onChange={(e) => {
                                                    if (e.target.checked) {
                                                        setCompareSymbols((prev) => (prev.length >= 4 ? prev : [...prev, ins.symbol]));
                                                    } else {
                                                        setCompareSymbols((prev) => prev.filter((s) => s !== ins.symbol));
                                                    }
                                                }}
                                            />
                                            {ins.symbol}
                                        </label>
                                    );
                                })}
                            </div> : null}
                            {!marketType && activeCategory !== 'FUTURES' ? (
                                <div className="terminal-chart-empty" style={{ marginTop: 8 }}>
                                    Tahvil kategorisinde karşılaştırma grafiği yerine üstteki ana chart kullanılır.
                                </div>
                            ) : null}
                            {loadingCompare ? (
                                <div className="terminal-chart-empty" style={{ marginTop: 8 }}>
                                    Karşılaştırma yükleniyor...
                                </div>
                            ) : compareRows.length === 0 ? (
                                <div className="terminal-chart-empty" style={{ marginTop: 8 }}>
                                    Karşılaştırma için en az iki sembol seçin.
                                </div>
                            ) : (
                                <div style={{ marginTop: 10 }}>
                                    <MarketCompareLwChart
                                        rows={compareRows}
                                        symbols={compareSymbols.slice(0, 4)}
                                        colors={['#38bdf8', '#22c55e', '#eab308', '#f87171']}
                                        lineWidthBySymbol={lineWidthBySymbol}
                                        timeframeLabel={range}
                                        tokens={{
                                            bgCard: tokens.bgCard,
                                            border: tokens.border,
                                            text: tokens.text,
                                            textMuted: tokens.textMuted,
                                        }}
                                        height={250}
                                    />
                                </div>
                            )}
                        </div>
                        </div>

                        <div className="terminal-card terminal-right-panel" style={sideRailBoxStyle}>
                            <div style={{ fontWeight: 700, marginBottom: 8, flexShrink: 0 }}>Piyasa İçgörü</div>
                            <div className="terminal-right-panel-scroll">
                            {activeCategory === 'EQUITY' ||
                            activeCategory === 'CRYPTO' ||
                            activeCategory === 'FX' ||
                            activeCategory === 'METALS' ||
                            activeCategory === 'FUNDS' ? (
                                <>
                                    <div className="terminal-right-treemap-slot">
                                        <MarketFinvizTreemap
                                            tiles={(dashboard?.heatmapTiles ?? []).filter((tile) => {
                                                if (activeCategory === 'EQUITY') return tile.assetClass === 'STOCK';
                                                if (activeCategory === 'CRYPTO') return tile.assetClass === 'CRYPTO';
                                                if (activeCategory === 'FX') return tile.assetClass === 'FX';
                                                if (activeCategory === 'METALS') return tile.assetClass === 'METAL';
                                                if (activeCategory === 'FUNDS') return tile.assetClass === 'FUND';
                                                return false;
                                            })}
                                            borderColor="rgba(71, 85, 105, 0.55)"
                                            panelBg={tokens.bgCard}
                                            onTileHover={handleTreemapHover}
                                            onTileLeave={() => handleTreemapHover(null)}
                                        />
                                    </div>
                                    <div style={{ marginTop: 8 }}>
                                        <button type="button" className="terminal-btn" onClick={() => navigate('/market/heatmap')}>
                                            Detaylı ısı haritası
                                        </button>
                                    </div>
                                    <div className="terminal-heatmap-detail">
                                        {hoverTile ? (
                                            <>
                                                <div>
                                                    <strong>{hoverTile.symbol}</strong> · {hoverTile.assetClass}
                                                </div>
                                                <div>
                                                    Değişim: {hoverTile.changePercent >= 0 ? '+' : ''}
                                                    {hoverTile.changePercent.toFixed(2)}%
                                                </div>
                                                <div>Sektör: {hoverTile.sector}</div>
                                            </>
                                        ) : (
                                            <div>Detay için ısı haritasında bir alana gelin.</div>
                                        )}
                                    </div>
                                </>
                            ) : null}

                            {activeCategory === 'FUTURES' ? (
                                <div className="terminal-mini-list">
                                    <strong style={{ fontSize: 13 }}>Top Futures Movers</strong>
                                    {futuresTopMovers.map((v) => (
                                        <div key={`mv-${v.symbol}`} className="terminal-mini-item">
                                            <span>{parseViopContractLabel(v.symbol)}</span>
                                            <span>
                                                {v.changePercent >= 0 ? '+' : ''}
                                                {v.changePercent.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%
                                            </span>
                                        </div>
                                    ))}
                                    <strong style={{ fontSize: 13, marginTop: 4 }}>Highest Carry</strong>
                                    {futuresHighestCarry.map((v) => (
                                        <div key={`carry-${v.symbol}`} className="terminal-mini-item">
                                            <span>{parseViopContractLabel(v.symbol)}</span>
                                            <span>%{Number(v.metrics?.yield ?? 0).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}</span>
                                        </div>
                                    ))}
                                    {futuresHeaderMetrics ? (
                                        <div className="terminal-mini-item">
                                            <span>Spot vs Vadeli Farkı</span>
                                            <span>{futuresHeaderMetrics.basis.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}</span>
                                        </div>
                                    ) : null}
                                    {futuresHeaderMetrics ? (
                                        <div className="terminal-mini-item">
                                            <span>Yön</span>
                                            <span>{futuresHeaderMetrics.basis >= 0 ? 'UP' : 'DOWN'}</span>
                                        </div>
                                    ) : null}
                                </div>
                            ) : null}

                            {activeCategory === 'FX' ? (
                                <div className="terminal-mini-list">
                                    <strong style={{ fontSize: 13 }}>Makas / Volatilite</strong>
                                    {instruments.slice(0, 8).map((ins) => {
                                        const vk = `FX:${normalizeSymbolKey(ins.symbol)}`;
                                        const dv = volatilityByKey[vk];
                                        const volPct =
                                            dv != null && Number.isFinite(dv) && dv > 0
                                                ? (dv * 100).toLocaleString('tr-TR', { maximumFractionDigits: 2 })
                                                : Math.abs(ins.changePercent).toLocaleString('tr-TR', { maximumFractionDigits: 2 });
                                        return (
                                            <div key={`fx-metric-${ins.symbol}`} className="terminal-mini-item">
                                                <span>{ins.symbol}</span>
                                                <span>
                                                    Makas {(ins.metrics?.basis ?? 0).toLocaleString('tr-TR', { maximumFractionDigits: 4 })} · Vol {volPct}%
                                                </span>
                                            </div>
                                        );
                                    })}
                                </div>
                            ) : null}

                            {activeCategory !== 'FUTURES' ? <div className="terminal-mini-list">
                                <strong style={{ fontSize: 13 }}>En Çok Yükselenler</strong>
                                {topGainers.map((r) => (
                                    <div key={`g-${r.symbol}`} className="terminal-mini-item">
                                        <span>{r.symbol}</span>
                                        <span style={{ color: '#22c55e' }}>+{r.changePercent.toFixed(2)}%</span>
                                    </div>
                                ))}
                                <strong style={{ fontSize: 13, marginTop: 4 }}>En Çok Düşenler</strong>
                                {topLosers.map((r) => (
                                    <div key={`l-${r.symbol}`} className="terminal-mini-item">
                                        <span>{r.symbol}</span>
                                        <span style={{ color: '#ef4444' }}>{r.changePercent.toFixed(2)}%</span>
                                    </div>
                                ))}
                                {activeCategory === 'CRYPTO' ? <><strong style={{ fontSize: 13, marginTop: 4 }}>Dominans</strong>
                                {cryptoDominance.map((r) => (
                                    <div key={`d-${r.symbol}`} className="terminal-mini-item">
                                        <span>{r.symbol}</span>
                                        <span>%{r.dominancePct.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}</span>
                                    </div>
                                ))}</> : null}
                            </div> : null}
                            </div>
                        </div>
                    </div>

                    <div style={{ marginTop: 8, display: 'flex', gap: 12, fontSize: 11, color: '#94a3b8' }}>
                        <span>VİOP kontrat: {viopContracts.length}</span>
                        <span>Tahvil enstrüman: {debtCatalog.length}</span>
                    </div>
                </>
            )}
            {isDetailPanelOpen && selectedInstrumentVm ? (
                <>
                <button
                    type="button"
                    className="instrument-drawer-backdrop"
                    aria-label="Detay panelini kapat"
                    onClick={() => setIsDetailPanelOpen(false)}
                />
                <aside className="instrument-drawer">
                    <div className="instrument-drawer-head">
                        <strong>Enstrüman Detayı</strong>
                        <button type="button" className="terminal-btn" onClick={() => setIsDetailPanelOpen(false)}>
                            Kapat
                        </button>
                    </div>
                    <div className="instrument-drawer-body">
                        <div className="instrument-drawer-symbol">
                            <div>
                                <div style={{ fontWeight: 700 }}>
                                    {selectedInstrumentVm.type === 'BOND'
                                        ? formatBondDisplayName(selectedInstrumentVm.symbol)
                                        : selectedInstrumentVm.symbol}
                                </div>
                                <div style={{ fontSize: 12, color: '#94a3b8' }}>
                                    {selectedInstrumentVm.type === 'BOND'
                                        ? `ISIN: ${selectedInstrumentVm.symbol}`
                                        : selectedInstrumentVm.displayName}
                                </div>
                            </div>
                        </div>
                        <div className="instrument-drawer-grid">
                            <div>Fiyat</div>
                            <div title={drawerInstrumentPrice?.title}>
                                {drawerInstrumentPrice ? (
                                    <>
                                        <span style={{ opacity: 0.8 }}>{drawerInstrumentPrice.glyph}</span>{' '}
                                        {drawerInstrumentPrice.amount.toLocaleString('tr-TR', {
                                            maximumFractionDigits: 4,
                                        })}
                                    </>
                                ) : (
                                    '—'
                                )}
                            </div>
                            <div>Değişim</div>
                            <div style={{ color: selectedInstrumentVm.changePercent >= 0 ? '#22c55e' : '#ef4444' }}>
                                {selectedInstrumentVm.changePercent >= 0 ? '+' : ''}
                                {selectedInstrumentVm.changePercent.toFixed(2)}%
                            </div>
                            {selectedInstrumentVm.type === 'BOND' ? (
                                <>
                                    <div>ISIN</div>
                                    <div>{selectedInstrumentVm.symbol}</div>
                                    <div>Vade Tarihi</div>
                                    <div>{formatDateTr(selectedInstrumentVm.maturityDate ?? extractMaturityDate(selectedInstrumentVm.symbol))}</div>
                                    <div>Vadeye Kalan Gün</div>
                                    <div>
                                        {(() => {
                                            const days = selectedInstrumentVm.daysToMaturity ?? getRemainingDays(selectedInstrumentVm.symbol);
                                            return (
                                        <span className="instrument-highlight-chip">
                                                    {Number.isFinite(days)
                                                ? `${days} gün kaldı`
                                                : '—'}
                                        </span>
                                            );
                                        })()}
                                    </div>
                                    <div>Kupon Oranı</div>
                                    <div>%{Number(selectedInstrumentVm.couponRate ?? 0).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}</div>
                                    <div>YTM</div>
                                    <div>%{Number(selectedInstrumentVm.yieldToMaturity ?? 0).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}</div>
                                </>
                            ) : null}
                            {selectedInstrumentVm.type === 'FUTURES' ? (
                                <>
                                    <div>Kontrat Ayı</div>
                                    <div>{selectedInstrumentVm.contractMonth ?? '—'}</div>
                                    <div>Son İşlem (Expiry)</div>
                                    <div>
                                        <span className="instrument-highlight-chip">
                                            {selectedInstrumentVm.expiryDate ?? '—'}
                                        </span>
                                    </div>
                                    <div>Teminat Gereksinimi</div>
                                    <div>{Number(selectedInstrumentVm.marginRequirement ?? 0).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}</div>
                                    <div>Long/Short</div>
                                    <div>{selectedInstrumentVm.longShort ?? 'NÖTR'}</div>
                                </>
                            ) : null}
                        </div>
                        <div style={{ marginTop: 10 }}>
                            <button
                                type="button"
                                className="terminal-btn active"
                                onClick={() =>
                                    navigate(
                                        `/trade?symbol=${encodeURIComponent(selectedInstrumentVm.symbol)}&kind=${encodeURIComponent(
                                            selectedInstrumentVm.type
                                        )}`
                                    )
                                }
                            >
                                Yeni Emir Gir
                            </button>
                        </div>
                        <div style={{ marginTop: 12 }}>
                            <div className="instrument-meta-card">
                                <div className="instrument-meta-title">{selectedInstrumentMeta?.title ?? 'Enstrüman Bilgisi'}</div>
                                {selectedInstrumentMeta ? (
                                    <>
                                        <div className="instrument-meta-grid">
                                            {selectedInstrumentMeta.rows.map(([label, value]) => (
                                                <div key={label}>
                                                    <div className="instrument-meta-label">{label}</div>
                                                    <div className="instrument-meta-value">{value}</div>
                                                </div>
                                            ))}
                                        </div>
                                        <div className="instrument-meta-description">{selectedInstrumentMeta.description}</div>
                                    </>
                                ) : (
                                    <div className="instrument-meta-empty">
                                        Bu enstrüman için özel açıklama yakında eklenecek.
                                    </div>
                                )}
                            </div>
                        </div>
                    </div>
                </aside>
                </>
            ) : null}
        </div>
    );
}
