import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { AxiosResponse } from 'axios';
import { useNavigate } from 'react-router-dom';
import { financeClient, marketClient } from '../api/client';
import type { LatestPriceRow, MarketDashboard } from '../components/market/marketTypes';
import { useTheme } from '../theme/ThemeContext';
import { formatAssetLabel } from '../lib/assetBranding';
import { MarketFinvizTreemap, type TreemapTile } from '../components/market/MarketFinvizTreemap';
import { MarketTerminalChart } from '../components/market/MarketTerminalChart';
import { MarketCompareLwChart } from '../components/market/MarketCompareLwChart';
import { usePolling } from '../hooks/usePolling';
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
    metrics?: {
        basis?: number;
        yield?: number;
    };
};
type InstrumentType = 'STOCK' | 'BOND' | 'FUTURES';
type InstrumentVm = MarketInstrument & {
    type: InstrumentType;
    typeBadge: 'S' | 'B' | 'V';
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
type BatchHistoryResponse = { series: Record<string, CandlePoint[]> };
type NewsItem = {
    id: number;
    title: string;
    source?: string | null;
    publishedAt: string;
    summary?: string | null;
};
type NewsPage = { content: NewsItem[] };
type WhaleSummary = { triggeredAt?: string; impactScore?: number };
type DashboardSummaryResponse = { whale?: WhaleSummary };
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
type MarketCategory = 'EQUITY' | 'CRYPTO' | 'FX' | 'FUTURES' | 'BOND';
type CompareRow = { time: string; values: Record<string, number> };
type NewsTone = 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';
type NewsMarkerVm = {
    id: string;
    time: string;
    tone: NewsTone;
    count: number;
    items: NewsItem[];
    matchedSymbols: string[];
    relevanceScore: number;
    impact: 'HIGH' | 'MEDIUM' | 'LOW';
};
type LiveTick = { category: MarketCategory; symbol: string; price: number; changePercent: number; volume: number };
type LivePayload = { ts: string; ticks: LiveTick[] };

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
    { id: 'FUTURES', label: 'VİOP' },
    { id: 'BOND', label: 'Tahvil' },
];

function heatAssetClassForCategory(category: MarketCategory): string {
    if (category === 'EQUITY') return 'STOCK';
    if (category === 'CRYPTO') return 'CRYPTO';
    if (category === 'FX') return 'FX';
    return '';
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

function toDayKey(value: string): string {
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return value.slice(0, 10);
    return d.toISOString().slice(0, 10);
}

function classifyNewsTone(title: string): NewsTone {
    const t = title.toLocaleLowerCase('tr-TR');
    const negative = ['düşüş', 'kriz', 'zarar', 'iflas', 'gerile', 'savaş', 'enflasyon artt', 'risk'];
    const positive = ['yükseliş', 'rekor', 'büyüme', 'kazanç', 'artış', 'anlaşma', 'güçlü', 'olumlu'];
    if (negative.some((k) => t.includes(k))) return 'NEGATIVE';
    if (positive.some((k) => t.includes(k))) return 'POSITIVE';
    return 'NEUTRAL';
}

function impactLevel(impact: 'HIGH' | 'MEDIUM' | 'LOW'): 'Yüksek' | 'Orta' | 'Düşük' {
    if (impact === 'HIGH') return 'Yüksek';
    if (impact === 'MEDIUM') return 'Orta';
    return 'Düşük';
}

function selectedNewsKey(category: MarketCategory, symbol: string): string {
    if (category !== 'FUTURES') return symbol;
    const m = symbol.match(/^([A-Z0-9_]+?)(\d{2})(\d{2})$/);
    return m?.[1] ?? symbol;
}

function symbolAliases(symbol: string, category: MarketCategory): string[] {
    const key = selectedNewsKey(category, symbol).toUpperCase();
    const base = [key];
    const map: Record<string, string[]> = {
        MSFT: ['MICROSOFT'],
        AAPL: ['APPLE'],
        AMZN: ['AMAZON'],
        GOOGL: ['GOOGLE', 'ALPHABET'],
        NVDA: ['NVIDIA'],
        TSLA: ['TESLA'],
        META: ['META', 'FACEBOOK'],
        JPM: ['JPMORGAN'],
        USDTRY: ['USD/TRY', 'DOLAR'],
        EURTRY: ['EUR/TRY', 'EURO'],
        XU030: ['BIST30'],
        ALTIN: ['GOLD', 'ONS'],
        BTCUSDT: ['BITCOIN', 'BTC'],
        ETHUSDT: ['ETHEREUM', 'ETH'],
        ATOMUSDT: ['ATOM'],
    };
    return [...new Set([...base, ...(map[key] ?? [])])];
}

function matchesSelectedSymbol(selected: string, category: MarketCategory, matchedSymbols: string[]): boolean {
    if (!selected) return false;
    const aliases = symbolAliases(selected, category).map((x) => x.toUpperCase());
    return matchedSymbols.some((m) => {
        const upper = m.toUpperCase();
        return aliases.some((a) => a.includes(upper) || upper.includes(a));
    });
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

function inferMatchedSymbols(item: NewsItem): string[] {
    const text = `${item.title ?? ''} ${item.summary ?? ''}`.toUpperCase();
    const matches = new Set<string>();
    const directSymbols = ['AAPL', 'AMZN', 'MSFT', 'GOOGL', 'NVDA', 'TSLA', 'USDTRY', 'EURTRY', 'XU030', 'ALTIN', 'BTC', 'ETH', 'BIST'];
    directSymbols.forEach((s) => {
        if (text.includes(s)) matches.add(s);
    });
    if (/(NASDAQ|WALL STREET|ABD BORSA|TEKNOLOJİ HİSSE)/.test(text)) matches.add('TECH');
    if (/(FAİZ|ENFLASYON|TCMB|FED|ECB|FOMC|DOLAR|EURO|MAKRO|NFP|İŞSİZLİK|CPI|PPI)/.test(text)) matches.add('MACRO');
    if (/(VIOP|VADELİ|KONTRAT|AÇIK POZİSYON|OPEN INTEREST)/.test(text)) matches.add('FUTURES');
    if (/(BONO|TAHVİL|GETİRİ EĞRİSİ|YIELD)/.test(text)) matches.add('BOND');
    return [...matches];
}

function calcRelevanceScore(item: NewsItem, key: string, category: MarketCategory, matched: string[]): number {
    const text = `${item.title ?? ''} ${item.summary ?? ''}`.toUpperCase();
    const selected = key.toUpperCase();
    const aliases = symbolAliases(selected, category);
    if (aliases.some((a) => text.includes(a))) return 0.95;
    if (category === 'FUTURES' && matched.includes(selected)) return 0.9;
    if (category === 'EQUITY' && matched.includes('TECH')) return 0.66;
    if (category === 'FX' && matched.includes('MACRO')) return 0.74;
    if (category === 'FUTURES' && matched.includes('FUTURES')) return 0.7;
    if (category === 'BOND' && matched.includes('BOND')) return 0.7;
    return 0.35;
}

function calcImpact(score: number, clusterCount: number): 'HIGH' | 'MEDIUM' | 'LOW' {
    if (score >= 0.82 || clusterCount >= 3) return 'HIGH';
    if (score >= 0.6 || clusterCount >= 2) return 'MEDIUM';
    return 'LOW';
}

function newsCategoryForMarket(category: MarketCategory): string {
    if (category === 'EQUITY') return 'STOCK';
    if (category === 'CRYPTO') return 'CRYPTO';
    if (category === 'FX') return 'FOREX';
    return 'GENERAL';
}

function pickBestNewsForSymbol(marker: NewsMarkerVm, category: MarketCategory, symbol: string): NewsItem | null {
    if (!marker?.items?.length) return null;
    const key = selectedNewsKey(category, symbol);
    const scored = marker.items.map((item) => {
        const matched = inferMatchedSymbols(item);
        const score = calcRelevanceScore(item, key, category, matched);
        return { item, score };
    });
    scored.sort((a, b) => b.score - a.score);
    return scored[0]?.item ?? marker.items[0] ?? null;
}

function unwrapData<T>(res: AxiosResponse<T>): T {
    const body = res.data as unknown;
    if (body && typeof body === 'object' && 'data' in (body as object)) {
        return (body as { data: T }).data;
    }
    return body as T;
}

export function Market() {
    const { theme, tokens } = useTheme();
    const navigate = useNavigate();
    const [activeCategory, setActiveCategory] = useState<MarketCategory>('EQUITY');
    const [selectedSymbol, setSelectedSymbol] = useState<string>('');
    const [searchTerm, setSearchTerm] = useState('');
    const [isDetailPanelOpen, setIsDetailPanelOpen] = useState(false);
    const [tableScrollTop, setTableScrollTop] = useState(0);
    const [liveOverrides, setLiveOverrides] = useState<Record<string, LiveTick>>({});
    const [range, setRange] = useState<'1D' | '1W' | '1M' | '1Y'>('1M');
    const [showMa, setShowMa] = useState(true);
    const [showRsi, setShowRsi] = useState(true);
    const [hoverTile, setHoverTile] = useState<TreemapTile | null>(null);
    const [priceFlash, setPriceFlash] = useState<Record<string, 'up' | 'down'>>({});
    const prevPricesRef = useRef<Record<string, number>>({});
    const [compareSymbols, setCompareSymbols] = useState<string[]>([]);
    const [compareRows, setCompareRows] = useState<CompareRow[]>([]);
    const [loadingCompare, setLoadingCompare] = useState(false);
    const [selectedNewsMarker, setSelectedNewsMarker] = useState<NewsMarkerVm | null>(null);
    const onNewsMarkerSelect = useCallback(
        (marker: NewsMarkerVm | null) => {
            setSelectedNewsMarker(marker);
            if (!marker || marker.items.length === 0) return;
            const best = pickBestNewsForSymbol(marker, activeCategory, selectedSymbol);
            if (!best) return;
            const qs = new URLSearchParams({
                focus: String(best.id),
                category: newsCategoryForMarket(activeCategory),
                from: 'chart',
            });
            navigate(`/news?${qs.toString()}`);
        },
        [activeCategory, selectedSymbol, navigate]
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

    const { data: newsPage } = useQuery({
        queryKey: ['market', 'news', 'terminal'],
        queryFn: () => marketClient.get<NewsPage>('/api/news', { params: { page: 0, size: 20 } }).then((r) => r.data),
        refetchInterval: 60_000,
    });

    const { data: summary } = useQuery({
        queryKey: ['market', 'summary', 'terminal'],
        queryFn: () => financeClient.get<DashboardSummaryResponse>('/api/dashboard/summary').then((r) => unwrapData(r)),
        refetchInterval: 30_000,
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
        return m;
    }, [viopLatest]);
    const sparklineMap = useMemo(() => {
        const m: Record<string, number[]> = {};
        (dashboard?.sparklines ?? []).forEach((row) => {
            m[normalizeSymbolKey(row.symbol)] = row.closes ?? [];
        });
        return m;
    }, [dashboard]);

    const instruments = useMemo<MarketInstrument[]>(() => {
        if (activeCategory === 'FUTURES') {
            const defaults = ['XU0300626', 'USDTRY0626', 'EURTRY0626', 'ALTIN0626'];
            const merged = new Map<string, ViopSnapshot>();
            viopLatest.forEach((v) => {
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
            defaults.forEach((code) => {
                const key = normalizeSymbolKey(code);
                if (!merged.has(key)) {
                    merged.set(key, {
                        contractCode: code,
                        price: 0,
                        basis: 0,
                        annualizedBasisPct: 0,
                        openInterest: 0,
                    });
                }
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
                .sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent));
        }
        if (activeCategory === 'BOND') {
            const merged = new Map<string, DebtSnapshot>();
            const historyByIsin = new Map<string, DebtSnapshot[]>();
            debtLatest.forEach((d) => {
                const key = normalizeSymbolKey(d?.isin);
                if (!key) return;
                if (!historyByIsin.has(key)) historyByIsin.set(key, []);
                historyByIsin.get(key)!.push(d);
                const prev = merged.get(key);
                if (!prev) {
                    merged.set(key, d);
                    return;
                }
                const prevTs = new Date(prev.asOf ?? 0).getTime();
                const nextTs = new Date(d.asOf ?? 0).getTime();
                if (nextTs >= prevTs) merged.set(key, d);
            });
            historyByIsin.forEach((list) => {
                list.sort((a, b) => new Date(a.asOf ?? 0).getTime() - new Date(b.asOf ?? 0).getTime());
            });
            return [...merged.values()]
                .map((d) => {
                    const symbol = normalizeSymbolKey(d.isin);
                    const history = historyByIsin.get(symbol) ?? [];
                    const prev = history.length > 1 ? Number(history[history.length - 2]?.dirtyPrice ?? 0) : 0;
                    const current = Number(d.dirtyPrice ?? 0);
                    const changePercent = prev > 0 && Number.isFinite(current) ? ((current - prev) / prev) * 100 : 0;
                    return {
                        symbol,
                        category: 'BOND' as const,
                        price: current,
                        changePercent,
                        trend: changePercent >= 0 ? ('UP' as const) : ('DOWN' as const),
                        metrics: { yield: Number(d.yieldPct ?? 0) },
                        volume: d.synthetic ? null : current * 100,
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
                : dashboard.latest.doviz;
        const unique = new Map<string, MarketInstrument>();
        Object.entries(latestMap ?? {})
            .filter(([, row]) => row && row.status !== 'NO_DATA')
            .forEach(([rawSymbol, row]) => {
                const symbol = normalizeSymbolKey(rawSymbol);
                if (!symbol) return;
                const spark = dashboard.sparklines.find((s) => normalizeSymbolKey(s.symbol) === symbol)?.closes ?? [];
                const volume = dashboard.heatmapTiles.find((t) => normalizeSymbolKey(t.symbol) === symbol)?.layoutWeight;
                const tileChange = dashboard.heatmapTiles.find((t) => normalizeSymbolKey(t.symbol) === symbol)?.changePercent;
                const changePercent = resolveChangePercent(spark, tileChange);
                unique.set(symbol, {
                    symbol,
                    category: activeCategory,
                    price: pickPrice(row),
                    changePercent,
                    trend: changePercent >= 0 ? 'UP' : 'DOWN',
                    volume: volume != null && Number.isFinite(volume) ? Number(volume) : null,
                    metrics: activeCategory === 'FX' ? { basis: fxSpreadMap[symbol] ?? 0 } : undefined,
                });
            });
        return [...unique.values()].sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent));
    }, [activeCategory, dashboard, viopLatest, debtLatest, fxSpreadMap]);

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
    }, [instruments]);

    const days = RANGE_TO_DAYS[range];
    const marketType =
        activeCategory === 'EQUITY'
            ? 'EQUITY'
            : activeCategory === 'CRYPTO'
            ? 'CRYPTO'
            : activeCategory === 'FX'
            ? 'FX'
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
        queryFn: () =>
            marketClient
                .get<BatchHistoryResponse>('/api/market/history/batch', {
                    params: { type: marketType, symbols: selectedSymbol, days },
                })
                .then((r) => r.data),
        refetchInterval: 15_000,
    });

    const { data: viopHistory = [], isLoading: loadingViopHistory } = useQuery({
        queryKey: ['market', 'viop-history', selectedSymbol, days],
        enabled: activeCategory === 'FUTURES' && Boolean(selectedSymbol),
        queryFn: () =>
            marketClient
                .get<ViopSnapshot[]>('/api/market/viop/history', {
                    params: { contract: selectedSymbol, days },
                })
                .then((r) => r.data),
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
            const sorted = [...viopHistory].sort((a, b) => new Date(a.asOf ?? 0).getTime() - new Date(b.asOf ?? 0).getTime());
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
                    volume: 0,
                };
            });
        }
        const series = batchData?.series?.[selectedSymbol] ?? [];
        if (series.length > 0) {
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
            const live = liveOverrides[`${ins.category}:${symbolKey}`];
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
            const spark = sparklineMap[symbolKey] ?? [];
            if (ins.category === 'BOND') {
                const debtMeta = debtMetaMap[symbolKey];
                const latestDebt = debtLatestMap[symbolKey];
                return {
                    ...ins,
                    price: livePrice,
                    changePercent: liveChange,
                    volume: liveVolume,
                    type: 'BOND',
                    typeBadge: 'B',
                    displayName: debtNameMap[symbolKey] ?? symbolKey,
                    sparkline: spark.length ? spark : [livePrice, livePrice, livePrice],
                    maturityDate: latestDebt?.maturityDate ?? debtMeta?.maturityDate,
                    daysToMaturity: latestDebt?.daysToMaturity,
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
                    typeBadge: 'V',
                    displayName: contractLabel,
                    sparkline: spark.length ? spark : [livePrice * 0.99, livePrice, livePrice * 1.01],
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
            return {
                ...ins,
                price: livePrice,
                changePercent: liveChange,
                volume: liveVolume,
                type: 'STOCK',
                typeBadge: 'S',
                displayName:
                    activeCategory === 'EQUITY'
                        ? formatAssetLabel(symbolKey, 'EQUITY')
                        : activeCategory === 'CRYPTO'
                        ? formatAssetLabel(symbolKey, 'CRYPTO')
                        : formatAssetLabel(symbolKey, 'FX'),
                sparkline: spark.length ? spark : [livePrice * 0.995, livePrice, livePrice * 1.005],
                longShort: ins.trend === 'UP' ? 'LONG' : 'SHORT',
            };
        });
    }, [instruments, sparklineMap, debtMetaMap, debtLatestMap, viopLatestMap, debtNameMap, activeCategory, liveOverrides]);

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
        const rowHeight = 52;
        const viewport = 500;
        const overscan = 8;
        const start = Math.max(0, Math.floor(tableScrollTop / rowHeight) - overscan);
        const visibleCount = Math.ceil(viewport / rowHeight) + overscan * 2;
        const end = Math.min(filteredInstruments.length, start + visibleCount);
        return {
            rowHeight,
            viewport,
            start,
            end,
            totalHeight: filteredInstruments.length * rowHeight,
            topSpacer: start * rowHeight,
            rows: filteredInstruments.slice(start, end),
        };
    }, [filteredInstruments, tableScrollTop]);
    const sparklinePath = useCallback((values: number[]) => {
        if (!values.length) return '';
        const min = Math.min(...values);
        const max = Math.max(...values);
        const range = Math.max(max - min, 1e-6);
        return values
            .map((v, i) => {
                const x = (i / Math.max(values.length - 1, 1)) * 100;
                const y = 100 - ((v - min) / range) * 100;
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

    const newsMarkers = useMemo<NewsMarkerVm[]>(() => {
        const selectedKey = selectedNewsKey(activeCategory, selectedSymbol);
        const enriched = (newsPage?.content ?? []).slice(0, 32).map((item) => {
            const matchedSymbols = inferMatchedSymbols(item);
            const relevanceScore = calcRelevanceScore(item, selectedKey, activeCategory, matchedSymbols);
            return { item, matchedSymbols, relevanceScore };
        });

        let filtered = enriched.filter((x) => {
            if (!selectedKey) return false;
            const hasDirect = matchesSelectedSymbol(selectedKey, activeCategory, x.matchedSymbols);
            if (hasDirect) return true;
            if (activeCategory === 'FX') {
                return x.matchedSymbols.includes('MACRO');
            }
            if (activeCategory === 'FUTURES') {
                return x.matchedSymbols.includes('FUTURES');
            }
            if (activeCategory === 'BOND') {
                return x.matchedSymbols.includes('BOND');
            }
            return false;
        });

        if (activeCategory === 'FX') {
            const hasSpecific = filtered.some((x) => matchesSelectedSymbol(selectedKey, activeCategory, x.matchedSymbols));
            if (!hasSpecific) {
                filtered = enriched.filter((x) => x.matchedSymbols.includes('MACRO'));
            }
        }

        filtered = filtered.filter((x) => x.relevanceScore >= 0.6);
        const grouped = new Map<string, typeof filtered>();
        filtered.forEach((row) => {
            const day = toDayKey(row.item.publishedAt);
            if (!grouped.has(day)) grouped.set(day, []);
            grouped.get(day)!.push(row);
        });

        return [...grouped.entries()]
            .map(([day, rows]) => {
                const items = rows.map((r) => r.item);
                const avgScore = rows.reduce((acc, r) => acc + r.relevanceScore, 0) / rows.length;
                const tones = items.map((x) => classifyNewsTone(x.title));
                const toneScore = tones.reduce((acc, tone) => acc + (tone === 'POSITIVE' ? 1 : tone === 'NEGATIVE' ? -1 : 0), 0);
                const tone: NewsTone = toneScore > 0 ? 'POSITIVE' : toneScore < 0 ? 'NEGATIVE' : 'NEUTRAL';
                const matched = [...new Set(rows.flatMap((r) => r.matchedSymbols))];
                const impact = calcImpact(avgScore, rows.length);
                return {
                    id: `news-${day}`,
                    time: items[0].publishedAt,
                    tone,
                    count: items.length,
                    items,
                    matchedSymbols: matched,
                    relevanceScore: Number(avgScore.toFixed(2)),
                    impact,
                };
            })
            .filter((m) => m.impact !== 'LOW')
            .sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime());
    }, [newsPage, activeCategory, selectedSymbol]);

    const markers = useMemo(() => {
        const fromNews = newsMarkers.map((n) => ({
            time: n.time,
            position: 'belowBar' as const,
            color: n.impact === 'HIGH' ? '#ef4444' : n.impact === 'MEDIUM' ? '#eab308' : '#38bdf8',
            shape: 'circle' as const,
            text: '',
        }));
        const whale = summary?.whale?.triggeredAt
            ? [
                  {
                      time: summary.whale.triggeredAt,
                      position: 'aboveBar' as const,
                      color: '#a78bfa',
                      shape: 'square' as const,
                        text: '',
                  },
              ]
            : [];
        return [...fromNews, ...whale];
    }, [newsMarkers, summary]);

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

    const volumeLeaders = useMemo(() => {
        if (activeCategory === 'FUTURES') {
            return [...instruments]
                .sort((a, b) => Number(b.volume ?? 0) - Number(a.volume ?? 0))
                .slice(0, 5);
        }
        if (!dashboard) return [] as MarketInstrument[];
        const target = heatAssetClassForCategory(activeCategory);
        if (!target) return [] as MarketInstrument[];
        const byVolume = dashboard.heatmapTiles
            .filter((t) => t.assetClass === target)
            .sort((a, b) => b.layoutWeight - a.layoutWeight)
            .slice(0, 5);
        return byVolume.map((tile) => {
            const base = instruments.find((x) => x.symbol === tile.symbol);
            return (
                base ?? {
                    symbol: tile.symbol,
                    category: activeCategory,
                    price: 0,
                    changePercent: tile.changePercent,
                    trend: tile.changePercent >= 0 ? 'UP' : 'DOWN',
                    volume: tile.layoutWeight,
                }
            );
        });
    }, [dashboard, activeCategory, instruments]);

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
                                : formatAssetLabel(hero.symbol, activeCategory === 'EQUITY' ? 'EQUITY' : activeCategory === 'CRYPTO' ? 'CRYPTO' : 'FX')
                            : '—'}
                    </div>
                    <div style={{ fontSize: 12, color: tokens.textMuted }}>{hero?.symbol ?? ''}</div>
                </div>
                <div className="terminal-hero-price">
                    {hero?.price?.toLocaleString('tr-TR', { maximumFractionDigits: 4 }) ?? '—'}
                </div>
                <div className={`terminal-hero-change ${hero?.trend === 'UP' ? 'up' : 'down'}`}>
                    {(hero?.changePercent ?? 0) >= 0 ? '+' : ''}
                    {(hero?.changePercent ?? 0).toFixed(2)}% {(hero?.trend === 'UP' ? '↑' : '↓')}
                </div>
                <div style={{ textAlign: 'right', fontSize: 12, color: tokens.textMuted }}>
                    <div>H: {(hero?.high ?? 0).toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</div>
                    <div>L: {(hero?.low ?? 0).toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</div>
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
                        <div className="terminal-card">
                            <div style={{ fontWeight: 700, marginBottom: 10 }}>Piyasa Listesi</div>
                            <input
                                className="terminal-search"
                                placeholder="Sembol / enstrüman ara"
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                            />
                            <div
                                className="terminal-table-wrap"
                                onScroll={(e) => setTableScrollTop(e.currentTarget.scrollTop)}
                            >
                                <table className="terminal-data-table">
                                    <thead>
                                        <tr>
                                            <th>Tip</th>
                                            <th>Enstrüman</th>
                                            <th>Fiyat</th>
                                            <th>%</th>
                                            <th>Hacim</th>
                                            <th>Trend</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {virtualRows.topSpacer > 0 ? (
                                            <tr style={{ height: virtualRows.topSpacer }}>
                                                <td colSpan={6} />
                                            </tr>
                                        ) : null}
                                        {virtualRows.rows.map((row) => (
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
                                            >
                                                <td>
                                                    <span className={`instrument-type-badge ${row.type.toLowerCase()}`}>{row.typeBadge}</span>
                                                </td>
                                                <td>
                                                    <div style={{ fontWeight: 700 }}>{row.symbol}</div>
                                                    <div style={{ fontSize: 11, color: tokens.textMuted }}>{row.displayName}</div>
                                                </td>
                                                <td>{row.price.toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</td>
                                                <td style={{ color: row.changePercent >= 0 ? '#22c55e' : '#ef4444' }}>
                                                    {row.changePercent >= 0 ? '+' : ''}
                                                    {row.changePercent.toFixed(2)}%
                                                </td>
                                                <td>{row.volume == null ? '—' : Number(row.volume).toLocaleString('tr-TR', { maximumFractionDigits: 0 })}</td>
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
                                        ))}
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
                                                <td colSpan={6} />
                                            </tr>
                                        ) : null}
                                    </tbody>
                                </table>
                            </div>
                        </div>

                        <div className="terminal-card">
                            <div className="terminal-btn-row" style={{ marginBottom: 10 }}>
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
                                <div className="terminal-btn-row">
                                    <button type="button" className={`terminal-btn ${showMa ? 'active' : ''}`} onClick={() => setShowMa((v) => !v)}>
                                        MA
                                    </button>
                                    <button type="button" className={`terminal-btn ${showRsi ? 'active' : ''}`} onClick={() => setShowRsi((v) => !v)}>
                                        RSI
                                    </button>
                                </div>
                            </div>
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
                                newsMarkers={newsMarkers}
                                onNewsSelect={onNewsMarkerSelect}
                                tokens={{
                                    bg: tokens.bg,
                                    bgCard: tokens.bgCard,
                                    border: tokens.border,
                                    text: tokens.text,
                                    textMuted: tokens.textMuted,
                                }}
                            />
                        </div>

                        <div className="terminal-card terminal-right-panel">
                            <div style={{ fontWeight: 700, marginBottom: 8 }}>Piyasa İçgörü</div>
                            <div className="terminal-heatmap-detail" style={{ marginTop: 0 }}>
                                {selectedNewsMarker ? (
                                    <>
                                        <div style={{ fontWeight: 700 }}>Haber Detayı</div>
                                        <div>
                                            {selectedNewsMarker.tone === 'POSITIVE'
                                                ? 'Olumlu'
                                                : selectedNewsMarker.tone === 'NEGATIVE'
                                                ? 'Negatif'
                                                : 'Nötr'}{' '}
                                            · Etki: {impactLevel(selectedNewsMarker.impact)}
                                        </div>
                                        <div>Tarih: {new Date(selectedNewsMarker.time).toLocaleString('tr-TR')}</div>
                                        <div>Kaynak: {selectedNewsMarker.items[0]?.source ?? 'Bilinmiyor'}</div>
                                        <div>İlişki skoru: {selectedNewsMarker.relevanceScore.toLocaleString('tr-TR')}</div>
                                        <div>Eşleşen semboller: {selectedNewsMarker.matchedSymbols.join(', ') || '—'}</div>
                                        <div style={{ marginTop: 4, color: '#e2e8f0' }}>
                                            {selectedNewsMarker.items[0]?.title ?? '—'}
                                        </div>
                                        {selectedNewsMarker.items.length > 1 ? (
                                            <div style={{ marginTop: 4, color: '#94a3b8' }}>
                                                Aynı gün {selectedNewsMarker.items.length} haber kümelendi.
                                            </div>
                                        ) : null}
                                    </>
                                ) : (
                                    <div>Grafikteki haber noktasına tıklayınca detay burada açılır.</div>
                                )}
                            </div>
                            {activeCategory === 'EQUITY' || activeCategory === 'CRYPTO' || activeCategory === 'FX' ? (
                                <>
                                    <MarketFinvizTreemap
                                        tiles={(dashboard?.heatmapTiles ?? []).filter((tile) => {
                                            if (activeCategory === 'EQUITY') return tile.assetClass === 'STOCK';
                                            if (activeCategory === 'CRYPTO') return tile.assetClass === 'CRYPTO';
                                            return tile.assetClass === 'FX';
                                        })}
                                        borderColor="rgba(71, 85, 105, 0.55)"
                                        panelBg={tokens.bgCard}
                                        onTileHover={setHoverTile}
                                        onTileLeave={() => setHoverTile(null)}
                                    />
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
                                    <strong style={{ fontSize: 13, marginTop: 4 }}>Highest Volume</strong>
                                    {volumeLeaders.map((v) => (
                                        <div key={`vol-${v.symbol}`} className="terminal-mini-item">
                                            <span>{parseViopContractLabel(v.symbol)}</span>
                                            <span>{v.volume == null ? '—' : Number(v.volume).toLocaleString('tr-TR')}</span>
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
                                    {instruments.slice(0, 8).map((ins) => (
                                        <div key={`fx-metric-${ins.symbol}`} className="terminal-mini-item">
                                            <span>{ins.symbol}</span>
                                            <span>
                                                Makas {(ins.metrics?.basis ?? 0).toLocaleString('tr-TR', { maximumFractionDigits: 4 })} · Vol {Math.abs(
                                                    ins.changePercent
                                                ).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}
                                                %
                                            </span>
                                        </div>
                                    ))}
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
                                <strong style={{ fontSize: 13, marginTop: 4 }}>Hacim Liderleri</strong>
                                {volumeLeaders.map((r) => (
                                    <div key={`v-${r.symbol}`} className="terminal-mini-item">
                                        <span>{r.symbol}</span>
                                        <span>{r.volume == null ? '—' : Number(r.volume).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}</span>
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

                    <div className="terminal-card" style={{ marginTop: 12 }}>
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
                                    tokens={{
                                        bgCard: tokens.bgCard,
                                        border: tokens.border,
                                        text: tokens.text,
                                        textMuted: tokens.textMuted,
                                    }}
                                    height={280}
                                />
                            </div>
                        )}
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
                            <span className={`instrument-type-badge ${selectedInstrumentVm.type.toLowerCase()}`}>{selectedInstrumentVm.typeBadge}</span>
                            <div>
                                <div style={{ fontWeight: 700 }}>{selectedInstrumentVm.symbol}</div>
                                <div style={{ fontSize: 12, color: '#94a3b8' }}>{selectedInstrumentVm.displayName}</div>
                            </div>
                        </div>
                        <div className="instrument-drawer-grid">
                            <div>Fiyat</div>
                            <div>{selectedInstrumentVm.price.toLocaleString('tr-TR', { maximumFractionDigits: 4 })}</div>
                            <div>Değişim</div>
                            <div style={{ color: selectedInstrumentVm.changePercent >= 0 ? '#22c55e' : '#ef4444' }}>
                                {selectedInstrumentVm.changePercent >= 0 ? '+' : ''}
                                {selectedInstrumentVm.changePercent.toFixed(2)}%
                            </div>
                            <div>Hacim</div>
                            <div>{selectedInstrumentVm.volume == null ? '—' : Number(selectedInstrumentVm.volume).toLocaleString('tr-TR')}</div>
                            {selectedInstrumentVm.type === 'BOND' ? (
                                <>
                                    <div>Vade Tarihi</div>
                                    <div>{selectedInstrumentVm.maturityDate ?? '—'}</div>
                                    <div>Vadeye Kalan Gün</div>
                                    <div>
                                        <span className="instrument-highlight-chip">
                                            {selectedInstrumentVm.daysToMaturity != null
                                                ? `${selectedInstrumentVm.daysToMaturity} gün`
                                                : '—'}
                                        </span>
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
                    </div>
                </aside>
                </>
            ) : null}
        </div>
    );
}
