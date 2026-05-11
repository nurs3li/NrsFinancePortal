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
import { financeClient, marketClient, readFinanceBinaryErrorMessage } from '../api/client';
import type { MarketDashboard } from '../components/market/marketTypes';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import { Activity, Bell, ChevronRight, CircleAlert, Coins, DollarSign, TrendingUp, Wallet, type LucideIcon } from 'lucide-react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatAssetLabel, getDynamicLogoUrl, type MarketType } from '../lib/assetBranding';
import { AssetLogo } from '../components/AssetLogo';
import './Dashboard.css';

type WhaleSummary = { level?: string; impactScore?: number; triggeredAt?: string };
type CashSummary = { amountTry?: number };
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
type ActivitySummary = { lastTradeAt?: string };

const DISTRIBUTION_COLORS: Record<string, string> = {
    CRYPTO: '#F7931A',
    FX: '#22C55E',
    METAL: '#EAB308',
    FUND: '#6366F1',
    STOCK: '#3182CE',
};

function assetTypeLabelTr(t: string): string {
    const m: Record<string, string> = {
        CRYPTO: 'Kripto',
        FX: 'Döviz',
        STOCK: 'Hisse',
        METAL: 'Metal',
        FUND: 'Fon',
    };
    return m[t] ?? t;
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

type SummaryResponse = {
    whale?: WhaleSummary;
    cash?: CashSummary;
    portfolio?: PortfolioSummary;
    activity?: ActivitySummary;
};

const FALLBACK_SUMMARY: SummaryResponse = {
    cash: { amountTry: 0 },
    portfolio: {
        totalValueTry: 0,
        distribution: {},
        totalCostTry: 0,
        totalPnlTry: 0,
        totalPnlPct: 0,
        categories: [],
    },
    activity: {},
};

type TxRow = { id: number; balanceAfter: number; createdAt: string };
type LatestPrice = { symbol?: string; buyPrice?: number; sellPrice?: number; price?: number; status?: string };
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
type TimeFilter = '1A' | '3A';
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
type BalancePoint = { key: string; date: string; balance: number };

const TIME_FILTERS: { id: TimeFilter; label: string }[] = [
    { id: '1A', label: '1 ay' },
    { id: '3A', label: '3 ay' },
];

const DashboardKpiCard = memo(function DashboardKpiCard({
    label,
    value,
    sub,
    valueClassName,
}: {
    label: string;
    value: string;
    sub?: ReactNode;
    valueClassName?: string;
}) {
    return (
        <div className="dashboard-card dashboard-kpi-card card-premium">
            <p className="kpi-label">{label}</p>
            <p className={`kpi-value ${valueClassName ?? ''}`.trim()}>{value}</p>
            {sub ? <p className="kpi-subtext">{sub}</p> : <span className="kpi-subtext kpi-subtext--placeholder" aria-hidden="true" />}
        </div>
    );
});

const DashboardBalanceChart = memo(function DashboardBalanceChart({
    balancePoints,
    tokens,
    formatMoney,
}: {
    balancePoints: BalancePoint[];
    tokens: { border: string; textMuted: string; text: string; bgCard: string };
    formatMoney: (v: number) => string;
}) {
    const tooltipRenderer = useCallback(
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (props: any) => {
            const { active, payload, label } = props;
            if (!active || !payload?.length) return null;
            return (
                <div className="dashboard-chart-tooltip">
                    <div className="dashboard-chart-tooltip-label">Tarih: {String(label ?? '')}</div>
                    <div className="dashboard-chart-tooltip-value">{formatMoney(Number(payload[0]?.value ?? 0))}</div>
                </div>
            );
        },
        [formatMoney]
    );

    if (balancePoints.length === 0) {
        return <p className="dashboard-muted">Bu aralıkta işlem yok; grafik oluşturulamadı.</p>;
    }
    return (
        <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={balancePoints} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
                <defs>
                    <linearGradient id="balanceFill" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#60A5FA" stopOpacity={0.42} />
                        <stop offset="35%" stopColor="#3182CE" stopOpacity={0.28} />
                        <stop offset="100%" stopColor="#1e3a5f" stopOpacity={0.04} />
                    </linearGradient>
                </defs>
                <CartesianGrid stroke={tokens.border} vertical={false} />
                <XAxis dataKey="date" tick={{ fill: tokens.textMuted, fontSize: 12 }} axisLine={false} tickLine={false} />
                <YAxis
                    tick={{ fill: tokens.textMuted, fontSize: 12 }}
                    tickFormatter={(v) => `${Math.round(Number(v) / 1000)}K`}
                    axisLine={false}
                    tickLine={false}
                />
                <Tooltip
                    content={tooltipRenderer}
                    cursor={{ stroke: 'rgba(203, 213, 225, 0.85)', strokeWidth: 1 }}
                />
                <Area type="monotone" dataKey="balance" stroke="#93C5FD" strokeWidth={3} fill="url(#balanceFill)" />
            </AreaChart>
        </ResponsiveContainer>
    );
});

const StarredAssetRow = memo(function StarredAssetRow({
    asset,
    logoUrl,
    fallback,
    onGoMarket,
}: {
    asset: StarAsset;
    logoUrl: string | null;
    fallback: { Icon: LucideIcon; color: string };
    onGoMarket: () => void;
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
            <p className="asset-price">{asset.price > 0 ? asset.price.toLocaleString('tr-TR') : '-'}</p>
            <p className={`asset-change ${up ? 'up' : 'down'}`}>
                {up ? '+' : ''}
                {asset.change24h.toLocaleString('tr-TR')}%
            </p>
            <span className="asset-row-go" aria-hidden="true">
                Detaya Git <ChevronRight size={14} strokeWidth={2.5} />
            </span>
        </div>
    );
});

export function Dashboard() {
    const navigate = useNavigate();
    const { theme, tokens } = useTheme();
    const { t, lang } = useLanguage();
    const [summary, setSummary] = useState<SummaryResponse | null>(null);
    const [balancePoints, setBalancePoints] = useState<BalancePoint[]>([]);
    const [selectedFilter, setSelectedFilter] = useState<TimeFilter>('1A');
    const [starAssets, setStarAssets] = useState<StarAsset[]>([]);
    const [latestNews, setLatestNews] = useState<NewsItem[]>([]);
    const [newsLoading, setNewsLoading] = useState(false);
    const [newsFadeIn, setNewsFadeIn] = useState(false);
    const [newsDetailOpen, setNewsDetailOpen] = useState(false);
    const [newsDetail, setNewsDetail] = useState<NewsDetailItem | null>(null);
    const [newsDetailLoading, setNewsDetailLoading] = useState(false);
    const [displayPortfolio, setDisplayPortfolio] = useState(0);
    const [displayBalance, setDisplayBalance] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const portfolioDistRef = useRef<HTMLDivElement>(null);
    const newsListRef = useRef<HTMLDivElement>(null);
    const [portfolioDistLayoutTick, setPortfolioDistLayoutTick] = useState(0);

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

    const getFilterStart = (filter: TimeFilter): Date => {
        const now = new Date();
        const start = new Date(now);
        if (filter === '1A') start.setDate(now.getDate() - 30);
        if (filter === '3A') start.setDate(now.getDate() - 90);
        return start;
    };

    const hasLoadedOnceRef = useRef(false);

    const buildDailyBalanceSeries = (txList: TxRow[]): BalancePoint[] => {
        if (!Array.isArray(txList) || txList.length === 0) return [];
        const sorted = [...txList].sort(
            (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
        );
        const latestByDay = new Map<string, { ts: number; balance: number }>();
        sorted.forEach((tx) => {
            const dt = new Date(tx.createdAt);
            if (Number.isNaN(dt.getTime())) return;
            const key = dt.toISOString().slice(0, 10);
            latestByDay.set(key, {
                ts: dt.getTime(),
                balance: Number(tx.balanceAfter ?? 0),
            });
        });
        return [...latestByDay.entries()]
            .sort((a, b) => a[0].localeCompare(b[0]))
            .map(([key, value]) => ({
                key,
                date: new Date(`${key}T00:00:00`).toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit' }),
                balance: value.balance,
            }));
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

    const fetchDashboard = useCallback(async (silent = false) => {
        const showLoader = !silent || !hasLoadedOnceRef.current;
        if (showLoader) setLoading(true);
        if (!silent) setError(null);
        try {
            const end = new Date();
            const start = getFilterStart(selectedFilter);
            // Özet ve işlemler: biri hata verse bile diğerini göster; ikisini de all ile beklemek sayfayı gereksiz kilitlemez.
            const phase1 = await Promise.allSettled([
                financeClient.get<SummaryResponse>('/api/dashboard/summary'),
                financeClient.get('/api/transactions/me/range', {
                    params: { start: start.toISOString(), end: end.toISOString(), page: 0, size: 200 },
                }),
            ]);

            if (phase1[0].status === 'fulfilled') {
                setSummary(unwrapAxiosData(phase1[0].value));
            } else {
                if (!silent) {
                    console.warn('Dashboard özet isteği başarısız', phase1[0].reason);
                }
                // Sayfanın geri kalanı (yıldız, haber) yine yüklensin; KPI'lar geçici olarak sıfır.
                setSummary(FALLBACK_SUMMARY);
            }

            if (phase1[1].status === 'fulfilled') {
                const page = unwrapAxiosData(phase1[1].value) as { content?: TxRow[] };
                const txContent = page?.content ?? [];
                const txList = Array.isArray(txContent) ? txContent : [];
                setBalancePoints(buildDailyBalanceSeries(txList));
            } else {
                setBalancePoints([]);
            }

            hasLoadedOnceRef.current = true;

            if (showLoader) setLoading(false);

            // İkinci aşama: her uç bağımsız — biri timeout/hata verse bile diğerlerini sıfırlama.
            const settled = await Promise.allSettled([
                marketClient.get<Record<string, LatestPrice>>('/api/market/doviz/latest'),
                marketClient.get<Record<string, LatestPrice>>('/api/market/metals/latest'),
                marketClient.get<Record<string, LatestPrice>>('/api/market/crypto/latest'),
                marketClient.get<Record<string, LatestPrice>>('/api/market/funds/latest'),
                marketClient.get<Record<string, LatestPrice>>('/api/market/equity/latest'),
                financeClient.get<MarketDashboard>('/api/market/dashboard'),
                financeClient.get<StarredAssetsResponse>('/api/me/starred-assets'),
            ]);

            const pickData = <T,>(i: number): T | undefined => {
                const r = settled[i];
                if (!r || r.status !== 'fulfilled') return undefined;
                return unwrapPayload<T>((r.value as AxiosResponse<T>).data);
            };

            const fx = pickData<Record<string, LatestPrice>>(0) ?? {};
            const metals = pickData<Record<string, LatestPrice>>(1) ?? {};
            const crypto = pickData<Record<string, LatestPrice>>(2) ?? {};
            const funds = pickData<Record<string, LatestPrice>>(3) ?? {};
            const equity = pickData<Record<string, LatestPrice>>(4) ?? {};
            const marketDashboard = pickData<MarketDashboard>(5);

            const starred = pickData<StarredAssetsResponse>(6);

            const mapByType: Record<MarketType, Record<string, LatestPrice>> = {
                FX: fx,
                METALS: metals,
                CRYPTO: crypto,
                FUNDS: funds,
                EQUITY: equity,
            };

            const resolvedStars = starred?.resolved ?? [];
            const starRows: StarAsset[] = resolvedStars.map((item) => {
                const marketType = item.marketType as MarketType;
                const symbol = item.symbol;
                const row = mapByType[marketType]?.[symbol];
                const price = getPrice(row);
                const ac = marketTypeToSparkAssetClass(marketType);
                const sparkRaw = sparklineClosesForDashboard(marketDashboard, symbol, ac);
                return {
                    key: `${marketType}-${symbol}`,
                    label: formatAssetLabel(symbol, marketType),
                    code: symbol,
                    marketType,
                    symbol,
                    price,
                    change24h: 0,
                    sparkline: normalizeTrendSparkline(sparkRaw, price, 0, 14),
                };
            });

            const changeResults = await Promise.allSettled(
                starRows.map((row) =>
                    marketClient
                        .get('/api/market/indicators', {
                            params: { type: row.marketType, symbol: row.symbol, days: 7, ma: '7' },
                        })
                        .then((res) => {
                            const close = res.data?.close ?? [];
                            if (!Array.isArray(close) || close.length < 2) return 0;
                            const prev = Number(close[close.length - 2]?.value ?? 0);
                            const curr = Number(close[close.length - 1]?.value ?? 0);
                            if (!prev) return 0;
                            return ((curr - prev) / prev) * 100;
                        })
                )
            );

            setStarAssets(
                starRows.map((row, idx) => {
                    const pct =
                        changeResults[idx].status === 'fulfilled'
                            ? Number(changeResults[idx].value.toFixed(2))
                            : 0;
                    const ac = marketTypeToSparkAssetClass(row.marketType);
                    const sparkRaw = sparklineClosesForDashboard(marketDashboard, row.symbol, ac);
                    return {
                        ...row,
                        change24h: pct,
                        sparkline: normalizeTrendSparkline(sparkRaw, row.price, pct, 14),
                    };
                })
            );
        } catch (err: unknown) {
            const msg =
                readFinanceBinaryErrorMessage(err) ??
                (err as { response?: { data?: { errors?: { error?: string }; message?: string } }; message?: string })
                    ?.response?.data?.errors?.error ??
                (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
                (err as { message?: string })?.message ??
                'Bilinmeyen hata';
            if (!silent || !hasLoadedOnceRef.current) setError(msg);
        } finally {
            if (showLoader) setLoading(false);
        }
    }, [selectedFilter]);

    useEffect(() => {
        fetchDashboard(false);
    }, [fetchDashboard]);

    useRefetchOnFocus(() => fetchDashboard(true));
    usePolling(() => fetchDashboard(true), 180_000);

    const formatMoney = useCallback(
        (v: number) => '₺' + v.toLocaleString('tr-TR', { maximumFractionDigits: 2 }),
        []
    );

    const controlDate = summary?.whale?.triggeredAt
        ? new Date(summary.whale.triggeredAt).toLocaleDateString('tr-TR')
        : '12.01.2026';
    const totalCash = summary?.cash?.amountTry ?? 0;
    const totalPortfolio = summary?.portfolio?.totalValueTry ?? 0;
    const totalBalance = totalCash + totalPortfolio;
    const distributionSlices = useMemo(() => {
        const distribution = summary?.portfolio?.distribution ?? {};
        const rows = Object.entries(distribution)
            .map(([key, value]) => ({ key, value: Number(value) || 0 }))
            .filter((r) => r.value > 0)
            .sort((a, b) => b.value - a.value);
        if (!rows.length || totalPortfolio <= 0) return [] as { key: string; value: number; pct: number }[];
        return rows.map((r) => ({
            ...r,
            pct: (r.value / totalPortfolio) * 100,
        }));
    }, [summary?.portfolio?.distribution, totalPortfolio]);

    const kpiIntroDoneRef = useRef(false);
    useEffect(() => {
        if (loading || !summary) return;
        if (!kpiIntroDoneRef.current) {
            kpiIntroDoneRef.current = true;
            let raf = 0;
            const start = performance.now();
            const dur = 680;
            const tick = (now: number) => {
                const t = Math.min(1, (now - start) / dur);
                const eased = 1 - (1 - t) ** 2;
                setDisplayPortfolio(totalPortfolio * eased);
                setDisplayBalance(totalBalance * eased);
                if (t < 1) raf = requestAnimationFrame(tick);
            };
            raf = requestAnimationFrame(tick);
            return () => cancelAnimationFrame(raf);
        }
        setDisplayPortfolio(totalPortfolio);
        setDisplayBalance(totalBalance);
        return undefined;
    }, [loading, summary, totalPortfolio, totalBalance]);

    useEffect(() => {
        const el = portfolioDistRef.current;
        if (!el || !summary || loading) return;
        let timeoutId: number;
        const ro = new ResizeObserver(() => {
            window.clearTimeout(timeoutId);
            timeoutId = window.setTimeout(() => setPortfolioDistLayoutTick((n) => n + 1), 220);
        });
        ro.observe(el);
        return () => {
            window.clearTimeout(timeoutId);
            ro.disconnect();
        };
    }, [summary, loading]);

    useLayoutEffect(() => {
        if (!summary || loading) return;
        let alive = true;
        setNewsLoading(true);
        setNewsFadeIn(false);

        const measureDist = () => portfolioDistRef.current?.getBoundingClientRect().height ?? 0;

        const run = async () => {
            await Promise.resolve();
            if (!alive) return;

            const rowApprox = 80;
            let size = Math.min(5, Math.max(2, Math.floor(Math.max(measureDist(), 220) / rowApprox)));
            let items: NewsItem[] = [];

            for (let attempt = 0; attempt < 8; attempt++) {
                if (!alive) return;
                try {
                    const res = await marketClient.get<NewsPage>('/api/news', { params: { page: 0, size, detail: false } });
                    const body = res.data as NewsPage;
                    items = Array.isArray(body?.content) ? body.content : [];
                } catch {
                    items = [];
                    break;
                }
                if (!alive) return;
                setLatestNews(items);
                await new Promise<void>((r) => requestAnimationFrame(() => r()));
                const distH = measureDist();
                const listH = newsListRef.current?.scrollHeight ?? 0;
                const fitsWell = listH <= distH - 28;
                if (distH <= 0 || fitsWell || size <= 2 || items.length < size) break;
                size = Math.max(2, size - 1);
            }

            if (!alive) return;
            setNewsLoading(false);
            requestAnimationFrame(() => {
                if (alive) setNewsFadeIn(true);
            });
        };

        void run();
        return () => {
            alive = false;
        };
    }, [summary, loading, lang, distributionSlices.length, portfolioDistLayoutTick]);

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
                <div className="dashboard-kpi-grid">
                    {Array.from({ length: 4 }).map((_, i) => (
                        <div key={i} className="dashboard-card loading-card">
                            <div className="skeleton-line skeleton-title" />
                            <div className="skeleton-line skeleton-value" />
                            <div className="skeleton-line skeleton-sub" />
                        </div>
                    ))}
                </div>
                <div className="dashboard-card dashboard-chart-card loading-card">
                    <div className="skeleton-line skeleton-section-title" />
                    <div className="skeleton-chart" />
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
                            <div className="dashboard-card loading-card">
                                <div className="skeleton-line skeleton-section-title" />
                                <div className="skeleton-line skeleton-sub" />
                                <div className="skeleton-line skeleton-sub" />
                            </div>
                        </div>
                        <div className="dashboard-card loading-card">
                            <div className="skeleton-line skeleton-section-title" />
                            {Array.from({ length: 4 }).map((_, i) => (
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

            <div className="dashboard-kpi-grid">
                <DashboardKpiCard
                    label={t('dashboard.totalPortfolioValue', 'Toplam portföy değeri')}
                    value={formatMoney(displayPortfolio)}
                />
                <DashboardKpiCard label={t('dashboard.cashTry', 'Nakit (TRY)')} value={formatMoney(totalCash)} />
                <DashboardKpiCard label="Toplam bakiye (portföy + nakit)" value={formatMoney(displayBalance)} />
                <DashboardKpiCard
                    label="Balina seviyesi"
                    value={summary.whale?.level ?? 'L1_LARGE_TRADER'}
                    valueClassName="kpi-value-small"
                    sub={<>Kontrol Tarihi: {controlDate}</>}
                />
            </div>

            <div className="dashboard-card dashboard-chart-card">
                <div className="chart-card-header">
                    <h2 className="section-title">
                        Bakiye grafiği (son {selectedFilter === '1A' ? '30' : '90'} gün)
                    </h2>
                    <div className="chart-filter-group">
                        {TIME_FILTERS.map((filter) => (
                            <button
                                key={filter.id}
                                type="button"
                                className={`chart-filter-btn ${selectedFilter === filter.id ? 'active' : ''}`}
                                onClick={() => setSelectedFilter(filter.id)}
                            >
                                {filter.label}
                            </button>
                        ))}
                    </div>
                </div>

                <div className="chart-wrapper">
                    <DashboardBalanceChart balancePoints={balancePoints} tokens={tokens} formatMoney={formatMoney} />
                </div>
            </div>

            <div className="dashboard-bottom-grid">
                <div className="dashboard-card dashboard-starred-card">
                    <h2 className="section-title">{t('dashboard.starredAssets', 'Yıldızlanan varlıklar')}</h2>
                    <div className="assets-table">
                        {starAssets.map((asset) => {
                            const logoUrl = getDynamicLogoUrl(asset.code, asset.marketType);
                            const fallback = getFallbackIcon(asset.marketType);
                            return (
                                <StarredAssetRow
                                    key={asset.key}
                                    asset={asset}
                                    logoUrl={logoUrl}
                                    fallback={fallback}
                                    onGoMarket={() => navigate('/market')}
                                />
                            );
                        })}
                    </div>
                </div>

                <div className="dashboard-side-panels">
                    <div className="dashboard-middle-column">
                        <div ref={portfolioDistRef} className="dashboard-card portfolio-dist-card">
                        <h2 className="section-title">Portföy dağılımı</h2>
                        <p className="portfolio-dist-subtitle">Birleşik: gerçek işlemler + manuel pozisyonlar (TRY)</p>
                        {distributionSlices.length === 0 ? (
                            <>
                                <div className="donut-placeholder donut-empty">
                                    <div className="donut-inner">—</div>
                                </div>
                                <p className="dashboard-muted">Portföyde dağıtılacak varlık yok.</p>
                            </>
                        ) : (
                            <>
                                <div className="donut-placeholder donut-multi" style={{ background: donutBackground }}>
                                    <div className="donut-inner">
                                        <span className="donut-inner-label">TRY</span>
                                    </div>
                                </div>
                                <ul className="donut-legend">
                                    {distributionSlices.map((s) => (
                                        <li key={s.key}>
                                            <span
                                                className="donut-legend-swatch"
                                                style={{ background: DISTRIBUTION_COLORS[s.key] ?? '#3182CE' }}
                                            />
                                            <span className="donut-legend-label">{assetTypeLabelTr(s.key)}</span>
                                            <span className="donut-legend-pct">
                                                {s.pct.toLocaleString('tr-TR', { maximumFractionDigits: 1 })}%
                                            </span>
                                        </li>
                                    ))}
                                </ul>
                                <div className="portfolio-pnl-summary">
                                    <div className="portfolio-pnl-row">
                                        <span>Toplam maliyet</span>
                                        <span>{formatMoney(summary.portfolio?.totalCostTry ?? 0)}</span>
                                    </div>
                                    <div className="portfolio-pnl-row">
                                        <span>Güncel değer</span>
                                        <span>{formatMoney(summary.portfolio?.totalValueTry ?? totalPortfolio)}</span>
                                    </div>
                                    <div className="portfolio-pnl-row">
                                        <span>Toplam kar (PNL)</span>
                                        <span
                                            className={
                                                (summary.portfolio?.totalPnlTry ?? 0) >= 0 ? 'portfolio-pnl-pos' : 'portfolio-pnl-neg'
                                            }
                                        >
                                            {formatMoney(summary.portfolio?.totalPnlTry ?? 0)}
                                        </span>
                                    </div>
                                    <div className="portfolio-pnl-row">
                                        <span>Kar oranı</span>
                                        <span
                                            className={
                                                (summary.portfolio?.totalPnlPct ?? 0) >= 0 ? 'portfolio-pnl-pos' : 'portfolio-pnl-neg'
                                            }
                                        >
                                            {Number(summary.portfolio?.totalPnlPct ?? 0).toLocaleString('tr-TR', {
                                                maximumFractionDigits: 2,
                                            })}
                                            %
                                        </span>
                                    </div>
                                </div>
                                {(summary.portfolio?.categories?.length ?? 0) > 0 ? (
                                    <>
                                        <p className="portfolio-category-heading">Sınıf bazında</p>
                                        <div className="portfolio-category-list">
                                            {(summary.portfolio?.categories ?? []).map((cat) => {
                                                const pnl = Number(cat.pnlTry ?? 0);
                                                const up = pnl >= 0;
                                                return (
                                                    <div key={cat.assetType} className="portfolio-category-row">
                                                        <div className="portfolio-category-title">{assetTypeLabelTr(cat.assetType)}</div>
                                                        <div className="portfolio-category-metrics">
                                                            <span className="portfolio-cat-val">{formatMoney(Number(cat.valueTry ?? 0))}</span>
                                                            <span className={up ? 'portfolio-pnl-pos' : 'portfolio-pnl-neg'}>
                                                                Kar {formatMoney(pnl)} (
                                                                {Number(cat.pnlPct ?? 0).toLocaleString('tr-TR', {
                                                                    maximumFractionDigits: 2,
                                                                })}
                                                                %)
                                                            </span>
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </>
                                ) : null}
                            </>
                        )}
                    </div>

                    <div className="dashboard-card dashboard-compact-card">
                        <h2 className="section-title">Son aktivite</h2>
                        <div className="activity-row">
                            <Activity size={14} className="activity-icon" />
                            <p>
                                Son işlem:{' '}
                                {summary.activity?.lastTradeAt
                                    ? new Date(summary.activity.lastTradeAt).toLocaleString('tr-TR')
                                    : 'Kayıt bulunamadı'}
                            </p>
                        </div>
                        <div className="activity-row">
                            <Wallet size={14} className="activity-icon" />
                            <p>
                                Toplam bakiye güncellendi: {formatMoney(totalBalance)}
                            </p>
                        </div>
                    </div>
                    <div className="dashboard-card dashboard-compact-card">
                        <h2 className="section-title">Uyarılar</h2>
                        <div className="activity-row">
                            <Bell size={14} className="activity-icon" />
                            <p>Balina seviyesi: {summary.whale?.level ?? 'L1_LARGE_TRADER'}</p>
                        </div>
                        <div className="activity-row">
                            <CircleAlert size={14} className="activity-icon" />
                            <p>
                                Etki skoru: {typeof summary.whale?.impactScore === 'number' ? summary.whale.impactScore : 'Veri bekleniyor'}
                            </p>
                        </div>
                    </div>
                    </div>

                    <div className="dashboard-card dashboard-news-card">
                        <h2 className="section-title">{t('dashboard.latestNews', 'En Son Haberler')}</h2>
                        <div
                            ref={newsListRef}
                            className={`news-list news-list--stretch${newsFadeIn ? ' news-list--ready' : ''}${
                                newsLoading ? ' news-list--loading' : ''
                            }`}
                        >
                            {newsLoading && latestNews.length === 0 ? (
                                <div className="news-skeleton-block" aria-hidden="true">
                                    {Array.from({ length: 7 }).map((_, i) => (
                                        <div key={i} className="skeleton-news-row dashboard-news-skel-row">
                                            <div className="skeleton-dot" />
                                            <div className="skeleton-line skeleton-row-main" />
                                        </div>
                                    ))}
                                </div>
                            ) : null}
                            {!newsLoading && latestNews.length === 0 ? (
                                <p className="dashboard-muted">Haber verisi bulunamadı.</p>
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
                                            {item.source ?? 'Kaynak'} ·{' '}
                                            {new Date(item.publishedAt).toLocaleString(uiLocale)}
                                        </p>
                                    </div>
                                </button>
                            ))}
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