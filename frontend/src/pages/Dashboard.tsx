import {
    memo,
    useState,
    useEffect,
    useCallback,
    useMemo,
    useRef,
    useLayoutEffect,
    type CSSProperties,
    type ReactNode,
} from 'react';
import { useNavigate } from 'react-router-dom';
import type { AxiosResponse } from 'axios';
import DOMPurify from 'dompurify';
import { readApiError } from '../api/envelope';
import { financeClient, marketClient, readFinanceBinaryErrorMessage } from '../api/client';
import type { LatestPriceRow, MarketDashboard } from '../components/market/marketTypes';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
import { useDocumentVisibility } from '../hooks/useDocumentVisibility';
import { manualPortfolioKeys } from '../queries/manualPortfolioKeys';
import { getManualPortfolioInsights } from '../services/manualPortfolioApi';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import {
    ChevronLeft,
    ChevronRight,
    Coins,
    DollarSign,
    TrendingUp,
    type LucideIcon,
} from 'lucide-react';
import { formatAssetLabel, getDynamicLogoUrl, type MarketType } from '../lib/assetBranding';
import { AssetLogo } from '../components/AssetLogo';
import './Dashboard.css';

type PortfolioCategoryBreakdown = {
    assetType: string;
    valueTry?: number;
    costTry?: number;
    pnlTry?: number;
    pnlPct?: number;
};
type PortfolioSummary = {
    totalValueTry?: number;
    distribution?: Record<string, number>;
    totalCostTry?: number;
    totalPnlTry?: number;
    totalPnlPct?: number;
    categories?: PortfolioCategoryBreakdown[];
};
type TradingSegmentSummary = {
    totalCostTry?: number;
    totalPnlTry?: number;
    totalValueTry?: number;
};

type SummaryResponse = {
    portfolio?: PortfolioSummary;
    /** Spot + vadeli (VİOP/tahvil) birleşik portföy değeri */
    totalPortfolioValueTry?: number;
    spotTrading?: TradingSegmentSummary;
    futuresDerivatives?: TradingSegmentSummary;
};

const FALLBACK_SUMMARY: SummaryResponse = {
    portfolio: {
        totalValueTry: 0,
        distribution: {},
        totalCostTry: 0,
        totalPnlTry: 0,
        totalPnlPct: 0,
        categories: [],
    },
    totalPortfolioValueTry: 0,
};

const DISTRIBUTION_COLORS: Record<string, string> = {
    CRYPTO: '#F7931A',
    FX: '#22C55E',
    METAL: '#EAB308',
    FUND: '#6366F1',
    STOCK: '#3182CE',
};

function assetTypeLabel(
    translate: (key: string, fallback?: string) => string,
    assetType: string,
): string {
    const m: Record<string, string> = {
        CRYPTO: translate('dashboard.assetType.crypto', 'Kripto'),
        FX: translate('dashboard.assetType.fx', 'Döviz'),
        STOCK: translate('dashboard.assetType.stock', 'Hisse'),
        METAL: translate('dashboard.assetType.metal', 'Metal'),
        FUND: translate('dashboard.assetType.fund', 'Fon'),
    };
    return m[assetType] ?? assetType;
}

function buildDistributionConicGradient(slices: { key: string; pct: number }[], accent: string, borderColor: string): string {
    if (slices.length === 0) {
        return `conic-gradient(${borderColor} 0deg 360deg)`;
    }
    let deg = 0;
    const parts: string[] = [];
    for (const s of slices) {
        const span = (s.pct / 100) * 360;
        const start = deg;
        deg += span;
        const color = DISTRIBUTION_COLORS[s.key] ?? accent;
        parts.push(`${color} ${start}deg ${deg}deg`);
    }
    if (deg < 360) {
        parts.push(`${borderColor} ${deg}deg 360deg`);
    }
    return `conic-gradient(${parts.join(', ')})`;
}

/** Dashboard özet kartları; BIST/US ayrımı için `LatestPriceRow` ile uyumlu additive alanlar */
type LatestPrice = LatestPriceRow;
type NewsItem = {
    id: number;
    title: string;
    titleTr?: string | null;
    summary?: string | null;
    source: string | null;
    publishedAt: string;
};
type NewsDetailItem = NewsItem & {
    contentTr?: string | null;
    content?: string | null;
    url?: string | null;
};
type NewsPage = { content: NewsItem[]; totalElements?: number };

type StarredAssetsResponse = {
    maxItems: number;
    selected: { marketType: string; symbol: string; position: number }[];
    resolved: { marketType: string; symbol: string; position: number; defaultFilled: boolean }[];
};

function unwrapPayload<T>(payload: unknown): T {
    if (payload && typeof payload === 'object' && 'data' in (payload as object)) {
        return (payload as { data: T }).data;
    }
    return payload as T;
}

function normalizeSymbolKey(symbol: string): string {
    return String(symbol ?? '')
        .trim()
        .replace(/\s+/g, '')
        .toUpperCase();
}

function marketTypeToSparkAssetClass(mt: MarketType): string {
    switch (mt) {
        case 'FX':
            return 'FX';
        case 'METALS':
            return 'METAL';
        case 'CRYPTO':
            return 'CRYPTO';
        case 'FUNDS':
            return 'FUND';
        case 'EQUITY':
            return 'STOCK';
        default:
            return 'FX';
    }
}

function latestMapsFromMarketDashboard(
    dashboard: MarketDashboard | undefined,
): Record<MarketType, Record<string, LatestPrice>> {
    const latest = dashboard?.latest;
    return {
        FX: latest?.doviz ?? {},
        METALS: latest?.metals ?? {},
        CRYPTO: latest?.crypto ?? {},
        FUNDS: latest?.funds ?? {},
        EQUITY: latest?.stocks ?? {},
    };
}

function sparklineClosesForDashboard(dashboard: MarketDashboard | undefined, symbol: string, assetClass: string): number[] {
    if (!dashboard) return [];
    const key = normalizeSymbolKey(symbol);
    const exact = dashboard.sparklines.find((s) => normalizeSymbolKey(s.symbol) === key && s.assetClass === assetClass);
    if (exact?.closes?.length) return exact.closes.map((c) => Number(c));
    const loose = dashboard.sparklines.find((s) => normalizeSymbolKey(s.symbol) === key);
    return loose?.closes?.map((c) => Number(c)) ?? [];
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

function changePctFromSparkline(closes: number[]): number {
    if (closes.length < 2) return 0;
    const prev = closes[closes.length - 2];
    const curr = closes[closes.length - 1];
    if (!Number.isFinite(prev) || !Number.isFinite(curr) || prev === 0) return 0;
    return ((curr - prev) / prev) * 100;
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

function sparklinePolylinePoints(values: number[]): string {
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
}

function extractReadableNewsBody(rawHtml: string | null | undefined): string {
    if (!rawHtml || !rawHtml.trim()) return '<p>İçerik bulunamadı.</p>';
    try {
        const parser = new DOMParser();
        const doc = parser.parseFromString(rawHtml, 'text/html');
        doc.querySelectorAll('script,style,iframe,object,embed,form,input,button,noscript,svg').forEach((el) => el.remove());
        doc.querySelectorAll('*').forEach((el) => {
            Array.from(el.attributes).forEach((attr) => {
                const name = attr.name.toLowerCase();
                if (name.startsWith('on')) el.removeAttribute(attr.name);
            });
        });
        doc.querySelectorAll('a').forEach((anchor) => {
            anchor.setAttribute('target', '_blank');
            anchor.setAttribute('rel', 'noreferrer noopener');
        });
        const candidate = doc.querySelector('article') || doc.querySelector('.content') || doc.querySelector('main') || doc.body;
        const cleaned = candidate?.innerHTML?.trim() || '<p>İçerik bulunamadı.</p>';
        return DOMPurify.sanitize(cleaned, {
            USE_PROFILES: { html: true },
            ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'u', 'ul', 'ol', 'li', 'a', 'blockquote', 'h2', 'h3', 'h4'],
            ALLOWED_ATTR: ['href', 'target', 'rel'],
        });
    } catch {
        return DOMPurify.sanitize(rawHtml);
    }
}

type StarAsset = {
    key: string;
    label: string;
    code: string;
    marketType: MarketType;
    symbol: string;
    price: number;
    change24h: number;
    sparkline: number[];
};
const STARRED_PAGE_SIZE = 7;
const LATEST_NEWS_LIMIT = 7;
const DASHBOARD_SUMMARY_KEY = ['dashboard', 'summary'] as const;
const DASHBOARD_MARKET_KEY = ['dashboard', 'market'] as const;
const DASHBOARD_STARRED_KEY = ['dashboard', 'starred'] as const;
const DASHBOARD_SUMMARY_STORAGE = 'nrs.dashboard.summary.v1';

function readStoredDashboardSummary(): SummaryResponse | undefined {
    try {
        const raw = sessionStorage.getItem(DASHBOARD_SUMMARY_STORAGE);
        if (!raw) return undefined;
        return JSON.parse(raw) as SummaryResponse;
    } catch {
        return undefined;
    }
}

const DashboardKpiCard = memo(function DashboardKpiCard({
    label,
    value,
    sub,
    valueClassName,
    compact,
}: {
    label: string;
    value: string;
    sub?: ReactNode;
    valueClassName?: string;
    compact?: boolean;
}) {
    return (
        <div
            className={`dashboard-card dashboard-kpi-card card-premium${compact ? ' dashboard-kpi-card--compact' : ''}`}
        >
            <p className="kpi-label">{label}</p>
            <p className={`kpi-value ${valueClassName ?? ''}`.trim()}>{value}</p>
            {sub ? <p className="kpi-subtext">{sub}</p> : <span className="kpi-subtext kpi-subtext--placeholder" aria-hidden="true" />}
        </div>
    );
});

const StarredAssetRow = memo(function StarredAssetRow({
    asset,
    logoUrl,
    fallback,
    onGoMarket,
    numberLocale,
    detailLabel,
}: {
    asset: StarAsset;
    logoUrl: string | null;
    fallback: { Icon: LucideIcon; color: string };
    onGoMarket: () => void;
    numberLocale: string;
    detailLabel: string;
}) {
    const pts = sparklinePolylinePoints(asset.sparkline.slice(-14));
    const up = asset.change24h >= 0;
    return (
        <div
            className="asset-row asset-row--interactive"
            role="button"
            tabIndex={0}
            onClick={onGoMarket}
            onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    onGoMarket();
                }
            }}
        >
            <div className="asset-main">
                <span className="asset-star">★</span>
                <AssetLogo
                    src={logoUrl}
                    alt={`${asset.code} logo`}
                    fallbackIcon={fallback.Icon}
                    fallbackColor={fallback.color}
                />
                <div>
                    <p className="asset-name">{asset.label}</p>
                    <p className="asset-code">{asset.code}</p>
                </div>
            </div>
            <div className="asset-trend-cell">
                <svg className="asset-sparkline" viewBox="0 0 100 24" preserveAspectRatio="none" aria-hidden="true">
                    <polyline points={pts} fill="none" stroke={up ? '#22c55e' : '#ef4444'} strokeWidth="2" />
                </svg>
            </div>
            <p className="asset-price">{asset.price > 0 ? asset.price.toLocaleString(numberLocale) : '-'}</p>
            <p className={`asset-change ${up ? 'up' : 'down'}`}>
                {up ? '+' : ''}
                {asset.change24h.toLocaleString(numberLocale)}%
            </p>
            <span className="asset-row-go" aria-hidden="true">
                {detailLabel} <ChevronRight size={14} strokeWidth={2.5} />
            </span>
        </div>
    );
});

/**
 * Dashboard'daki tum sayisal degerleri (TRY tutarlari, yuzdeler, PNL...) ilk
 * render'da 0'dan akarak gosteren generic CountUp hook'u. Mevcut kod yalnizca
 * portfoy/bakiye icin elle bir easing yapiyordu; bu hook ile *tum* metrikleri
 * tek seferde ve sade ekiple animate ediyoruz.
 *
 * Davranis:
 * - Ilk gercerli (>=0) deger gelene kadar 0 dondurur (skeleton sirasinda gozu
 *   yanlis bir rakam tirpaniyla yormasin).
 * - Sonraki guncellemelerde (polling/refresh) animasyon yapmadan dogrudan
 *   hedef degere atlar -- yoksa her 3 dakikada bir butun rakamlar tekrar
 *   "akiyor" gibi gozukurdu.
 * - prefers-reduced-motion AC: kullanici hareket azaltma istedigi anda hicbir
 *   animasyon olmadan an'a-an deger basariz.
 */
function useCountUp(value: number, durationMs = 720): number {
    const [display, setDisplay] = useState(0);
    const introDoneRef = useRef(false);
    const rafRef = useRef<number | null>(null);

    useEffect(() => {
        if (!Number.isFinite(value)) {
            setDisplay(0);
            return;
        }
        const reduceMotion =
            typeof window !== 'undefined' &&
            typeof window.matchMedia === 'function' &&
            window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        if (reduceMotion || introDoneRef.current) {
            introDoneRef.current = true;
            setDisplay(value);
            return;
        }
        introDoneRef.current = true;
        const startTs = performance.now();
        const startVal = 0;
        const tick = (now: number) => {
            const tNorm = Math.min(1, (now - startTs) / durationMs);
            const eased = 1 - (1 - tNorm) ** 3;
            setDisplay(startVal + (value - startVal) * eased);
            if (tNorm < 1) {
                rafRef.current = requestAnimationFrame(tick);
            }
        };
        rafRef.current = requestAnimationFrame(tick);
        return () => {
            if (rafRef.current != null) cancelAnimationFrame(rafRef.current);
        };
        // Sadece *yeni bir gercerli deger* tetikleyince animasyon basliyor.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [value]);

    return display;
}

export function Dashboard() {
    const navigate = useNavigate();
    const { theme, tokens } = useTheme();
    const { t, lang } = useLanguage();
    const qc = useQueryClient();
    const [starAssets, setStarAssets] = useState<StarAsset[]>([]);
    const [starredResolved, setStarredResolved] = useState<StarredAssetsResponse['resolved']>([]);
    const [starredPage, setStarredPage] = useState(0);
    const [latestNews, setLatestNews] = useState<NewsItem[]>([]);
    const [newsLoading, setNewsLoading] = useState(false);
    const [newsFadeIn, setNewsFadeIn] = useState(false);
    const [newsDetailOpen, setNewsDetailOpen] = useState(false);
    const [newsDetail, setNewsDetail] = useState<NewsDetailItem | null>(null);
    const [newsDetailLoading, setNewsDetailLoading] = useState(false);
    const tabVisible = useDocumentVisibility();

    const summaryQuery = useQuery({
        queryKey: DASHBOARD_SUMMARY_KEY,
        queryFn: async () => {
            const res = await financeClient.get<SummaryResponse>('/api/dashboard/summary');
            return unwrapAxiosData(res);
        },
        placeholderData: () => readStoredDashboardSummary(),
        staleTime: 90_000,
        gcTime: 10 * 60_000,
        retry: 1,
        refetchOnWindowFocus: false,
    });

    useEffect(() => {
        if (summaryQuery.data) {
            try {
                sessionStorage.setItem(DASHBOARD_SUMMARY_STORAGE, JSON.stringify(summaryQuery.data));
            } catch {
                /* ignore quota */
            }
        }
    }, [summaryQuery.data]);

    const marketDashboardQuery = useQuery({
        queryKey: DASHBOARD_MARKET_KEY,
        queryFn: async () => {
            const res = await financeClient.get<MarketDashboard>('/api/market/dashboard');
            return unwrapPayload<MarketDashboard>(res.data);
        },
        enabled: starredResolved.length > 0,
        staleTime: 5 * 60_000,
        gcTime: 10 * 60_000,
        refetchOnWindowFocus: false,
    });

    const starredListQuery = useQuery({
        queryKey: DASHBOARD_STARRED_KEY,
        queryFn: async () => {
            const res = await financeClient.get<StarredAssetsResponse>('/api/me/starred-assets');
            return unwrapPayload<StarredAssetsResponse>(res.data);
        },
        enabled: summaryQuery.isSuccess,
        staleTime: 5 * 60_000,
        gcTime: 10 * 60_000,
        refetchOnWindowFocus: false,
    });

    const summary = summaryQuery.data ?? (summaryQuery.isError ? FALLBACK_SUMMARY : null);
    const loading = summaryQuery.isPending && summaryQuery.data === undefined && !readStoredDashboardSummary();
    const error =
        summaryQuery.isError && summaryQuery.data === undefined
            ? readFinanceBinaryErrorMessage(summaryQuery.error) ??
              readApiError(summaryQuery.error).message ??
              'Bilinmeyen hata'
            : null;

    const hasManualPositions = (summary?.portfolio?.categories?.length ?? 0) > 0;

    const insightsQuery = useQuery({
        queryKey: manualPortfolioKeys.insights(),
        queryFn: getManualPortfolioInsights,
        enabled: summaryQuery.isSuccess && tabVisible && hasManualPositions,
        staleTime: 120_000,
        refetchInterval: summaryQuery.isSuccess && tabVisible && hasManualPositions ? 180_000 : false,
    });

    const insightSummary = insightsQuery.data?.summary;

    const portfolioDistRef = useRef<HTMLDivElement>(null);
    /*
     * Haberler kartinin kendi yuksekligi, ust kolonlarin (Yildizlanan / Portfoy+Aktivite)
     * yuksegine gore stretch ile belirleniyor. Bunu doğrudan ölçüp haber sayısını adapte
     * ediyoruz; portfolioDistRef parent zincirine guvenmek artik dogru olmaz (Son Bildirimler
     * sag kolona tasindi). Bkz. news useLayoutEffect.
     */

    const fallbackIconMap = useMemo<Record<string, { Icon: LucideIcon; color: string }>>(
        () => ({
            FX: { Icon: DollarSign, color: '#22C55E' },
            METALS: { Icon: Coins, color: '#EAB308' },
            CRYPTO: { Icon: TrendingUp, color: '#F7931A' },
            FUNDS: { Icon: TrendingUp, color: '#3182CE' },
            EQUITY: { Icon: TrendingUp, color: '#3182CE' },
            DEFAULT: { Icon: TrendingUp, color: '#94A3B8' },
        }),
        []
    );

    const getFallbackIcon = (marketType: MarketType): { Icon: LucideIcon; color: string } => {
        return fallbackIconMap[marketType] ?? fallbackIconMap.DEFAULT;
    };

    const getPrice = (row: LatestPrice | null | undefined): number => {
        if (!row || row.status === 'NO_DATA') return 0;
        if (row.buyPrice != null) return Number(row.buyPrice);
        if (row.sellPrice != null) return Number(row.sellPrice);
        if (row.price != null) return Number(row.price);
        return 0;
    };

    const unwrapAxiosData = <T,>(res: AxiosResponse<T>): T => {
        const body = res.data as unknown;
        if (body && typeof body === 'object' && 'data' in (body as object)) {
            const w = body as { data: T };
            if (w.data !== undefined) {
                return w.data;
            }
        }
        return body as T;
    };

    const buildStarRowsForPage = useCallback(
        (
            resolved: StarredAssetsResponse['resolved'],
            page: number,
            marketDashboard: MarketDashboard | undefined,
        ): StarAsset[] => {
            const start = page * STARRED_PAGE_SIZE;
            const slice = (resolved ?? []).slice(start, start + STARRED_PAGE_SIZE);
            const mapByType = latestMapsFromMarketDashboard(marketDashboard);
            return slice.map((item) => {
                const marketType = item.marketType as MarketType;
                const symbol = item.symbol;
                const row = mapByType[marketType]?.[symbol];
                const price = getPrice(row);
                const ac = marketTypeToSparkAssetClass(marketType);
                const sparkRaw = sparklineClosesForDashboard(marketDashboard, symbol, ac);
                const change24h = Number(changePctFromSparkline(sparkRaw).toFixed(2));
                return {
                    key: `${marketType}-${symbol}`,
                    label: formatAssetLabel(symbol, marketType),
                    code: symbol,
                    marketType,
                    symbol,
                    price,
                    change24h,
                    sparkline: normalizeTrendSparkline(sparkRaw, price, change24h, 14),
                };
            });
        },
        [getPrice],
    );

    useEffect(() => {
        if (!starredListQuery.data?.resolved) return;
        setStarredResolved(starredListQuery.data.resolved);
    }, [starredListQuery.data]);

    const starredPageCount = Math.max(1, Math.ceil(starredResolved.length / STARRED_PAGE_SIZE));
    const starredPageSafe = Math.min(starredPage, starredPageCount - 1);

    useEffect(() => {
        setStarredPage((p) => Math.min(p, Math.max(0, starredPageCount - 1)));
    }, [starredPageCount]);

    useEffect(() => {
        if (!starredResolved.length) {
            setStarAssets([]);
            return;
        }
        setStarAssets(buildStarRowsForPage(starredResolved, starredPageSafe, marketDashboardQuery.data));
    }, [starredResolved, starredPageSafe, marketDashboardQuery.data, buildStarRowsForPage]);

    const refreshDashboard = useCallback(() => {
        void qc.invalidateQueries({ queryKey: DASHBOARD_SUMMARY_KEY });
        void qc.invalidateQueries({ queryKey: DASHBOARD_STARRED_KEY });
        void qc.invalidateQueries({ queryKey: DASHBOARD_MARKET_KEY });
    }, [qc]);

    useRefetchOnFocus(refreshDashboard);
    usePolling(refreshDashboard, 180_000);

    const numberLocale = lang === 'en' ? 'en-US' : 'tr-TR';
    const formatMoney = useCallback(
        (v: number) => '₺' + v.toLocaleString(numberLocale, { maximumFractionDigits: 2 }),
        [numberLocale],
    );

    const spotSegment = summary?.spotTrading;
    const futuresSegment = summary?.futuresDerivatives;

    const spotCostTry = Number(spotSegment?.totalCostTry ?? summary?.portfolio?.totalCostTry ?? 0);
    const spotPnlTry = Number(spotSegment?.totalPnlTry ?? summary?.portfolio?.totalPnlTry ?? 0);
    const spotValueTry = Number(spotSegment?.totalValueTry ?? summary?.portfolio?.totalValueTry ?? 0);

    const futuresCostTry = Number(futuresSegment?.totalCostTry ?? 0);
    const futuresPnlTry = Number(futuresSegment?.totalPnlTry ?? 0);
    const futuresValueTry = Number(futuresSegment?.totalValueTry ?? 0);

    const totalPortfolioTry = Number(
        summary?.totalPortfolioValueTry ?? spotValueTry + futuresValueTry,
    );

    const distributionSlices = useMemo(() => {
        const distribution = summary?.portfolio?.distribution ?? {};
        const rows = Object.entries(distribution)
            .map(([key, value]) => ({ key, value: Number(value) || 0 }))
            .filter((r) => r.value > 0)
            .sort((a, b) => b.value - a.value);
        if (!rows.length || totalPortfolioTry <= 0) return [] as { key: string; value: number; pct: number }[];
        return rows.map((r) => ({
            ...r,
            pct: (r.value / totalPortfolioTry) * 100,
        }));
    }, [summary?.portfolio?.distribution, totalPortfolioTry]);

    const categoryByAssetType = useMemo(() => {
        const map = new Map<string, PortfolioCategoryBreakdown>();
        for (const cat of summary?.portfolio?.categories ?? []) {
            map.set(cat.assetType, cat);
        }
        return map;
    }, [summary?.portfolio?.categories]);

    // İlk yüklemede KPI animasyonu; sonraki polling'de sıçrama yok.
    const totalCostTry = spotCostTry + futuresCostTry;
    const totalPnlTry = spotPnlTry + futuresPnlTry;

    const displayPortfolio = useCountUp(totalPortfolioTry);
    const displaySpotValue = useCountUp(spotValueTry);
    const displayTotalCost = useCountUp(totalCostTry);
    const displayTotalPnl = useCountUp(totalPnlTry);
    const displaySpotCost = useCountUp(spotCostTry);
    const displaySpotPnl = useCountUp(spotPnlTry);
    const displayFuturesCost = useCountUp(futuresCostTry);
    const displayFuturesPnl = useCountUp(futuresPnlTry);
    const displayPnlPct = useCountUp(Number(summary?.portfolio?.totalPnlPct ?? 0));

    const starredPageAssets = starAssets;

    const realReturnTry = useMemo(() => {
        if (insightSummary?.realReturnAvailable === true && Number.isFinite(Number(insightSummary.realReturn))) {
            return Number(insightSummary.realReturn);
        }
        return null;
    }, [insightSummary]);

    const realReturnPct = useMemo(() => {
        if (insightSummary?.realReturnAvailable === true && Number.isFinite(Number(insightSummary.realReturnPct))) {
            return Number(insightSummary.realReturnPct);
        }
        return null;
    }, [insightSummary]);

    const displayRealReturn = useCountUp(realReturnTry ?? 0);
    const displayRealReturnPct = useCountUp(realReturnPct ?? 0);

    useLayoutEffect(() => {
        if (!summary || loading) return;
        let alive = true;
        setNewsLoading(true);
        setNewsFadeIn(false);

        const run = async () => {
            try {
                const res = await marketClient.get<NewsPage>('/api/news', {
                    params: { page: 0, size: LATEST_NEWS_LIMIT, detail: false },
                });
                const body = res.data as NewsPage;
                const items = Array.isArray(body?.content)
                    ? body.content.slice(0, LATEST_NEWS_LIMIT)
                    : [];
                if (alive) setLatestNews(items);
            } catch {
                if (alive) setLatestNews([]);
            } finally {
                if (alive) {
                    setNewsLoading(false);
                    requestAnimationFrame(() => {
                        if (alive) setNewsFadeIn(true);
                    });
                }
            }
        };

        void run();
        return () => {
            alive = false;
        };
    }, [summary, loading, lang]);

    const openNewsDetail = useCallback((item: NewsItem) => {
        setNewsDetailOpen(true);
        setNewsDetail(item as NewsDetailItem);
        setNewsDetailLoading(true);
        marketClient
            .get<NewsDetailItem>(`/api/news/${item.id}`)
            .then((res) => setNewsDetail(unwrapPayload<NewsDetailItem>(res.data)))
            .catch(() => {})
            .finally(() => setNewsDetailLoading(false));
    }, []);

    useEffect(() => {
        if (!newsDetailOpen) return;
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setNewsDetailOpen(false);
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [newsDetailOpen]);

    const donutBackground = useMemo(
        () => buildDistributionConicGradient(distributionSlices, '#3182CE', tokens.border),
        [distributionSlices, tokens.border]
    );

    const preferTurkish = lang === 'tr';
    const uiLocale = lang === 'en' ? 'en-US' : 'tr-TR';

    const dashboardVars = {
        '--dashboard-bg': theme === 'light' ? '#F9FAFB' : tokens.bg,
        '--dashboard-card': tokens.bgCard,
        '--dashboard-text': tokens.text,
        '--dashboard-muted': tokens.textMuted,
        '--dashboard-border': tokens.border,
        '--dashboard-accent': '#3182CE',
        '--dashboard-shadow': theme === 'dark' ? '0 4px 8px -2px rgba(0, 0, 0, 0.35)' : '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
        '--dashboard-news-logo-bg': theme === 'dark' ? '#334155' : '#EDF2F7',
        '--dashboard-success': tokens.success,
        '--dashboard-danger': tokens.error,
        '--skeleton-base': theme === 'dark' ? 'rgba(71, 85, 105, 0.28)' : 'rgba(148, 163, 184, 0.16)',
        '--skeleton-highlight': theme === 'dark' ? 'rgba(100, 116, 139, 0.48)' : 'rgba(148, 163, 184, 0.32)',
    } as CSSProperties;

    if (loading) {
        return (
            <div className="saas-dashboard" style={dashboardVars}>
                <div className="dashboard-headline">
                    <h1 className="dashboard-title">{t('nav.dashboard', 'Dashboard')}</h1>
                </div>
                <div className="dashboard-kpi-grid dashboard-kpi-grid--compact">
                    {Array.from({ length: 8 }).map((_, i) => (
                        <div key={i} className="dashboard-card loading-card">
                            <div className="skeleton-line skeleton-title" />
                            <div className="skeleton-line skeleton-value" />
                            <div className="skeleton-line skeleton-sub" />
                        </div>
                    ))}
                </div>
                <div className="dashboard-bottom-grid">
                    <div className="dashboard-card loading-card">
                        <div className="skeleton-line skeleton-section-title" />
                        <div className="skeleton-table">
                            {Array.from({ length: 4 }).map((_, i) => (
                                <div key={i} className="skeleton-table-row">
                                    <div className="skeleton-dot" />
                                    <div className="skeleton-line skeleton-row-main" />
                                    <div className="skeleton-line skeleton-row-side" />
                                </div>
                            ))}
                        </div>
                    </div>
                    <div className="dashboard-side-panels">
                        <div className="dashboard-middle-column">
                            <div className="dashboard-card loading-card">
                                <div className="skeleton-line skeleton-section-title" />
                                <div className="skeleton-donut" />
                            </div>
                        </div>
                        <div className="dashboard-card loading-card dashboard-news-card-skel">
                            <div className="skeleton-line skeleton-section-title" />
                            {Array.from({ length: 8 }).map((_, i) => (
                                <div key={i} className="skeleton-news-row">
                                    <div className="skeleton-dot" />
                                    <div className="skeleton-line skeleton-row-main" />
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            </div>
        );
    }
    if (error) {
        return (
            <div className="saas-dashboard" style={dashboardVars}>
                <h1 className="dashboard-title">{t('nav.dashboard', 'Dashboard')}</h1>
                <p className="dashboard-error">{t('news.errorPrefix', 'Hata')}: {error}</p>
            </div>
        );
    }
    if (!summary) {
        return (
            <div className="saas-dashboard" style={dashboardVars}>
                <h1 className="dashboard-title">{t('nav.dashboard', 'Dashboard')}</h1>
                <p className="dashboard-muted">{t('dashboard.summaryMissing', 'Özet verisi bulunamadı.')}</p>
            </div>
        );
    }

    return (
        <div
            className="saas-dashboard"
            style={dashboardVars}
        >
            <div className="dashboard-headline">
                <h1 className="dashboard-title">{t('nav.dashboard', 'Dashboard')}</h1>
            </div>

            <div className="dashboard-kpi-grid dashboard-kpi-grid--compact">
                <DashboardKpiCard
                    label={t('dashboard.totalPortfolioValue', 'Toplam portföy değeri')}
                    value={formatMoney(displayPortfolio)}
                    compact
                />
                <DashboardKpiCard
                    label={t('dashboard.totalCostTry', 'Toplam maliyet (TRY)')}
                    value={formatMoney(displayTotalCost)}
                    compact
                />
                <DashboardKpiCard
                    label={t('dashboard.totalPnlTry', 'Toplam kar (PNL)')}
                    value={formatMoney(displayTotalPnl)}
                    valueClassName={totalPnlTry >= 0 ? 'portfolio-pnl-pos' : 'portfolio-pnl-neg'}
                    compact
                />
                <DashboardKpiCard
                    label={t('dashboard.futuresTotalPnl', 'Vadeli işlem toplam K/Z')}
                    value={formatMoney(displayFuturesPnl)}
                    valueClassName={futuresPnlTry >= 0 ? 'portfolio-pnl-pos' : 'portfolio-pnl-neg'}
                    compact
                />
                <DashboardKpiCard
                    label={t('dashboard.futuresTotalCost', 'Vadeli işlem toplam maliyet')}
                    value={formatMoney(displayFuturesCost)}
                    compact
                />
                <DashboardKpiCard
                    label={t('dashboard.spotTotalCost', 'Spot işlem toplam maliyet')}
                    value={formatMoney(displaySpotCost)}
                    compact
                />
                <DashboardKpiCard
                    label={t('dashboard.spotTotalPnl', 'Spot işlem toplam K/Z')}
                    value={formatMoney(displaySpotPnl)}
                    valueClassName={spotPnlTry >= 0 ? 'portfolio-pnl-pos' : 'portfolio-pnl-neg'}
                    compact
                />
                <DashboardKpiCard
                    label={t('dashboard.realReturn', 'Reel K/Z')}
                    value={
                        realReturnTry != null
                            ? formatMoney(displayRealReturn)
                            : t('portfolio.realPnlWaitingCpi', 'TÜFE verisi bekleniyor')
                    }
                    sub={
                        realReturnPct != null
                            ? `${displayRealReturnPct.toLocaleString(numberLocale, { maximumFractionDigits: 2 })}%`
                            : undefined
                    }
                    valueClassName={
                        realReturnTry != null
                            ? realReturnTry >= 0
                                ? 'portfolio-pnl-pos'
                                : 'portfolio-pnl-neg'
                            : 'kpi-value-small'
                    }
                    compact
                />
            </div>

            <div className="dashboard-bottom-grid">
                <div className="dashboard-card dashboard-starred-card">
                    <h2 className="section-title section-title--compact">
                        {t('dashboard.starredAssets', 'Yıldızlanan varlıklar')}
                    </h2>
                    <div className="assets-table assets-table--compact">
                        {starredPageAssets.length === 0 ? (
                            <p className="dashboard-muted dashboard-starred-empty">
                                {t('dashboard.starredEmpty', 'Yıldızlanan varlık yok. Piyasa sayfasından ekleyebilirsiniz.')}
                            </p>
                        ) : (
                            starredPageAssets.map((asset) => {
                                const logoUrl = getDynamicLogoUrl(asset.code, asset.marketType, {
                                    equitySubmarket: asset.marketType === 'EQUITY' ? 'US' : undefined,
                                });
                                const fallback = getFallbackIcon(asset.marketType);
                                return (
                                    <StarredAssetRow
                                        key={asset.key}
                                        asset={asset}
                                        logoUrl={logoUrl}
                                        fallback={fallback}
                                        onGoMarket={() => navigate('/market')}
                                        numberLocale={numberLocale}
                                        detailLabel={t('common.detail', 'Detaya git')}
                                    />
                                );
                            })
                        )}
                    </div>
                    {starredResolved.length > STARRED_PAGE_SIZE ? (
                        <div className="dashboard-starred-pagination" aria-live="polite">
                            <span className="dashboard-starred-pagination__count">
                                {starredPageSafe * STARRED_PAGE_SIZE + 1}–
                                {Math.min((starredPageSafe + 1) * STARRED_PAGE_SIZE, starredResolved.length)} /{' '}
                                {starredResolved.length}
                            </span>
                            <div className="dashboard-starred-pagination__nav">
                                <button
                                    type="button"
                                    className="dashboard-starred-page-btn"
                                    disabled={starredPageSafe <= 0}
                                    onClick={() => setStarredPage((p) => Math.max(0, p - 1))}
                                    aria-label={t('dashboard.starredPrev', 'Önceki sayfa')}
                                >
                                    <ChevronLeft size={14} aria-hidden />
                                </button>
                                <span className="dashboard-starred-pagination__sep">
                                    {starredPageSafe + 1} / {starredPageCount}
                                </span>
                                <button
                                    type="button"
                                    className="dashboard-starred-page-btn"
                                    disabled={starredPageSafe >= starredPageCount - 1}
                                    onClick={() =>
                                        setStarredPage((p) => Math.min(starredPageCount - 1, p + 1))
                                    }
                                    aria-label={t('dashboard.starredNext', 'Sonraki sayfa')}
                                >
                                    <ChevronRight size={14} aria-hidden />
                                </button>
                            </div>
                        </div>
                    ) : null}
                </div>

                <div className="dashboard-side-panels">
                    <div className="dashboard-middle-column">
                        <div ref={portfolioDistRef} className="dashboard-card portfolio-dist-card">
                        <h2 className="section-title">{t('dashboard.portfolioDist.title', 'Portföy dağılımı')}</h2>
                        <p className="portfolio-dist-subtitle">
                            {t(
                                'dashboard.portfolioDistHint',
                                'Manuel pozisyonlar ve güncel piyasa fiyatlarıyla hesaplanan analitik portföy (TRY).',
                            )}
                        </p>
                        {distributionSlices.length === 0 ? (
                            <>
                                <div className="donut-placeholder donut-empty">
                                    <div className="donut-inner">—</div>
                                </div>
                                <p className="dashboard-muted">
                                    {t('dashboard.portfolioDist.empty', 'Portföyde dağıtılacak varlık yok.')}
                                </p>
                            </>
                        ) : (
                            <>
                                <div className="donut-placeholder donut-multi" style={{ background: donutBackground }}>
                                    <div className="donut-inner">
                                        <span className="donut-inner-label">TRY</span>
                                    </div>
                                </div>
                                <ul className="donut-legend" aria-label={t('dashboard.portfolioDist.legend', 'Dağılım özeti')}>
                                    <li className="donut-legend-head" aria-hidden="true">
                                        <span className="donut-legend-leading" />
                                        <span>{t('dashboard.legend.cost', 'Maliyet')}</span>
                                        <span>{t('dashboard.legend.profit', 'Kar')}</span>
                                        <span>{t('dashboard.legend.share', 'Pay')}</span>
                                    </li>
                                    {distributionSlices.map((s) => {
                                        const cat = categoryByAssetType.get(s.key);
                                        const cost = Number(cat?.costTry ?? 0);
                                        const pnl = Number(cat?.pnlTry ?? 0);
                                        const up = pnl >= 0;
                                        return (
                                            <li key={s.key}>
                                                <span className="donut-legend-leading">
                                                    <span
                                                        className="donut-legend-swatch"
                                                        style={{
                                                            background: DISTRIBUTION_COLORS[s.key] ?? '#3182CE',
                                                        }}
                                                    />
                                                    <span className="donut-legend-label">
                                                        {assetTypeLabel(t, s.key)}
                                                    </span>
                                                </span>
                                                <span className="donut-legend-metric">{formatMoney(cost)}</span>
                                                <span
                                                    className={`donut-legend-metric ${up ? 'portfolio-pnl-pos' : 'portfolio-pnl-neg'}`}
                                                >
                                                    {formatMoney(pnl)}
                                                </span>
                                                <span className="donut-legend-pct">
                                                    {s.pct.toLocaleString(numberLocale, {
                                                        maximumFractionDigits: 1,
                                                    })}
                                                    %
                                                </span>
                                            </li>
                                        );
                                    })}
                                </ul>
                                <div className="portfolio-pnl-summary">
                                    <div className="portfolio-pnl-row">
                                        <span>{t('dashboard.pnl.totalCost', 'Toplam maliyet')}</span>
                                        <span>{formatMoney(displaySpotCost)}</span>
                                    </div>
                                    <div className="portfolio-pnl-row">
                                        <span>{t('dashboard.pnl.currentValue', 'Güncel değer')}</span>
                                        <span>{formatMoney(displaySpotValue)}</span>
                                    </div>
                                    <div className="portfolio-pnl-row">
                                        <span>{t('dashboard.pnl.totalPnl', 'Toplam kar (PNL)')}</span>
                                        <span
                                            className={
                                                spotPnlTry >= 0 ? 'portfolio-pnl-pos' : 'portfolio-pnl-neg'
                                            }
                                        >
                                            {formatMoney(displaySpotPnl)}
                                        </span>
                                    </div>
                                    <div className="portfolio-pnl-row">
                                        <span>{t('dashboard.pnl.returnRate', 'Kar oranı')}</span>
                                        <span
                                            className={
                                                (summary.portfolio?.totalPnlPct ?? 0) >= 0 ? 'portfolio-pnl-pos' : 'portfolio-pnl-neg'
                                            }
                                        >
                                            {displayPnlPct.toLocaleString(numberLocale, {
                                                maximumFractionDigits: 2,
                                            })}
                                            %
                                        </span>
                                    </div>
                                </div>
                            </>
                        )}
                    </div>
                    </div>

                    <div className="dashboard-right-column">
                    <div className="dashboard-card dashboard-news-card dashboard-news-card--fill">
                        <h2 className="section-title">{t('dashboard.latestNews', 'En Son Haberler')}</h2>
                        <div
                            className={`news-list news-list--stretch${newsFadeIn ? ' news-list--ready' : ''}${
                                newsLoading ? ' news-list--loading' : ''
                            }`}
                        >
                            {newsLoading && latestNews.length === 0 ? (
                                <div className="news-skeleton-block" aria-hidden="true">
                                    {Array.from({ length: LATEST_NEWS_LIMIT }).map((_, i) => (
                                        <div key={i} className="skeleton-news-row dashboard-news-skel-row">
                                            <div className="skeleton-dot" />
                                            <div className="skeleton-line skeleton-row-main" />
                                        </div>
                                    ))}
                                </div>
                            ) : null}
                            {!newsLoading && latestNews.length === 0 ? (
                                <p className="dashboard-muted">{t('dashboard.news.empty', 'Haber verisi bulunamadı.')}</p>
                            ) : null}
                            {latestNews.map((item) => (
                                <button
                                    type="button"
                                    className="news-item news-item-button"
                                    key={item.id}
                                    onClick={() => openNewsDetail(item)}
                                    title={t('news.openDetail', 'Haber detayı')}
                                >
                                    <span className="news-logo">{item.source?.[0] ?? 'N'}</span>
                                    <div>
                                        <p className="news-title">
                                            {preferTurkish && item.titleTr ? item.titleTr : item.title}
                                        </p>
                                        <p className="news-meta">
                                            {item.source ?? t('dashboard.news.sourceFallback', 'Kaynak')} ·{' '}
                                            {new Date(item.publishedAt).toLocaleString(uiLocale)}
                                        </p>
                                    </div>
                                </button>
                            ))}
                        </div>
                    </div>
                    </div>
                </div>
            </div>

            {newsDetailOpen ? (
                <div
                    className="dashboard-news-modal-backdrop"
                    role="presentation"
                    onClick={() => setNewsDetailOpen(false)}
                >
                    <div
                        className="dashboard-news-modal"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="dashboard-news-modal-title"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <button
                            type="button"
                            className="dashboard-news-modal-close"
                            onClick={() => setNewsDetailOpen(false)}
                            aria-label={t('common.close', 'Kapat')}
                        >
                            ×
                        </button>
                        {newsDetailLoading && !newsDetail?.summary && !(newsDetail as NewsDetailItem | null)?.contentTr ? (
                            <p className="dashboard-muted">{t('news.loading', 'Yükleniyor...')}</p>
                        ) : null}
                        {newsDetail ? (
                            <>
                                <h2 id="dashboard-news-modal-title" className="dashboard-news-modal-title">
                                    {preferTurkish && newsDetail.titleTr ? newsDetail.titleTr : newsDetail.title}
                                </h2>
                                <div className="dashboard-news-modal-meta">
                                    <span>
                                        <strong>{t('news.source', 'Kaynak')}:</strong> {newsDetail.source ?? '—'}
                                    </span>
                                    <span>
                                        <strong>{t('news.date', 'Tarih')}:</strong>{' '}
                                        {new Date(newsDetail.publishedAt).toLocaleString(uiLocale)}
                                    </span>
                                </div>
                                <div
                                    className="dashboard-news-modal-body"
                                    dangerouslySetInnerHTML={{
                                        __html: extractReadableNewsBody(
                                            (preferTurkish ? newsDetail.contentTr : null) ??
                                                (newsDetail as NewsDetailItem).content ??
                                                newsDetail.summary
                                        ),
                                    }}
                                />
                                {newsDetail.url ? (
                                    <a
                                        href={newsDetail.url}
                                        target="_blank"
                                        rel="noreferrer noopener"
                                        className="dashboard-news-modal-source-link"
                                    >
                                        {t('news.goToSource', 'Kaynağa git')}
                                    </a>
                                ) : null}
                            </>
                        ) : null}
                    </div>
                </div>
            ) : null}
        </div>
    );
}