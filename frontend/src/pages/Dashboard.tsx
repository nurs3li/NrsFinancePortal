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
import { financeClient, marketClient, notificationClient, readFinanceBinaryErrorMessage } from '../api/client';
import { isPortfolioInsightNotificationType } from '../utils/portfolioInsightNotifications';
import type { LatestPriceRow, MarketDashboard } from '../components/market/marketTypes';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
import { useDocumentVisibility } from '../hooks/useDocumentVisibility';
import { notificationKeys } from '../queries/notificationKeys';
import { manualPortfolioKeys } from '../queries/manualPortfolioKeys';
import { getManualPortfolioInsights } from '../services/manualPortfolioApi';
import { useQuery } from '@tanstack/react-query';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import {
    Bell,
    CheckCircle2,
    ChevronRight,
    Clock3,
    Coins,
    DollarSign,
    Info,
    ShieldAlert,
    TrendingUp,
    Wallet,
    type LucideIcon,
} from 'lucide-react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
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
type SummaryResponse = {
    portfolio?: PortfolioSummary;
    /** Öncelikli toplam TRY; backend dashboard özeti */
    totalPortfolioValueTry?: number;
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

type SnapshotRow = { snapshotAt: string; portfolioValueTry?: number };

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
type TimeFilter = '1A' | '3A';

/**
 * Dashboard'un sag panelindeki "Son Bildirimler" kartinda gosterdigimiz bildirim satiri.
 * Notifications sayfasindaki tam DTO'nun bir alt kumesi; Dashboard'da uzun body/referans
 * alanlarini render etmiyoruz, tikladiginda zaten Bildirimler sayfasina yonlendiriyoruz.
 */
type NotificationSummary = {
    id: number;
    title: string;
    type: string;
    readAt: string | null;
    createdAt: string;
    lastOccurredAt: string | null;
};

// Spring Data 3.3+ VIA_DTO sekli (bkz. NotificationServiceApplication).
type NotificationPage = {
    content: NotificationSummary[];
    page?: { totalElements?: number; size?: number; number?: number; totalPages?: number };
};

type NotifCategory = 'APPROVAL' | 'SECURITY' | 'REVIEW' | 'INFO';

/**
 * Bildirim turunden (string) UI kategorisi cikariyoruz; Notifications sayfasindaki
 * `classifyNotification` ile ayni anahtar kelimeleri kullaniyoruz ki dashboard'daki
 * mini liste ile detay sayfasi tutarli renk/ikon gostersin.
 */
function classifyNotification(type: string): NotifCategory {
    const t = (type ?? '').toUpperCase();
    if (t === 'REAL_RETURN_NEGATIVE' || t === 'PORTFOLIO_CONCENTRATION_RISK') {
        return 'SECURITY';
    }
    if (t === 'REAL_RETURN_POSITIVE') {
        return 'APPROVAL';
    }
    if (
        t.includes('APPROVED') ||
        t.includes('APPROVAL') ||
        t.includes('SUCCESS') ||
        t.includes('COMPLETED') ||
        t.includes('CONFIRM') ||
        t.includes('ONAY')
    ) {
        return 'APPROVAL';
    }
    if (
        t.includes('SUSPICIOUS') ||
        t.includes('SECURITY') ||
        t.includes('RISK') ||
        t.includes('REJECT') ||
        t.includes('FAIL') ||
        t.includes('FRAUD') ||
        t.includes('BLOCK') ||
        t.includes('FROZEN') ||
        t.includes('GUVENL')
    ) {
        return 'SECURITY';
    }
    if (
        t.includes('REVIEW') ||
        t.includes('PENDING') ||
        t.includes('REGISTERED') ||
        t.includes('REQUEST') ||
        t.includes('TASK') ||
        t.includes('INCELE')
    ) {
        return 'REVIEW';
    }
    return 'INFO';
}

function notifCategoryColor(category: NotifCategory): string {
    switch (category) {
        case 'APPROVAL':
            return '#22c55e';
        case 'SECURITY':
            return '#ef4444';
        case 'REVIEW':
            return '#38bdf8';
        default:
            return '#c0c0c0';
    }
}

function NotifCategoryIcon({ category, size = 14 }: { category: NotifCategory; size?: number }) {
    switch (category) {
        case 'APPROVAL':
            return <CheckCircle2 size={size} aria-hidden />;
        case 'SECURITY':
            return <ShieldAlert size={size} aria-hidden />;
        case 'REVIEW':
            return <Info size={size} aria-hidden />;
        default:
            return <Bell size={size} aria-hidden />;
    }
}
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
        return <p className="dashboard-muted">Bu aralıkta portföy anlığı yok; grafik oluşturulamadı.</p>;
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
    const tabVisible = useDocumentVisibility();
    const { data: recentNotifications = [], isLoading: notificationsLoading } = useQuery({
        queryKey: notificationKeys.dashboardRecent(),
        queryFn: async () => {
            const res = await notificationClient.get<NotificationPage>('/api/notifications/me', {
                params: { page: 0, size: 4, unreadOnly: false, sort: ['lastOccurredAt,desc', 'createdAt,desc'] },
            });
            const content = res?.data?.content;
            return Array.isArray(content) ? content : [];
        },
        staleTime: 120_000,
        refetchInterval: tabVisible ? 180_000 : false,
    });

    const insightsQuery = useQuery({
        queryKey: manualPortfolioKeys.insights(),
        queryFn: getManualPortfolioInsights,
        staleTime: 60_000,
        refetchInterval: tabVisible ? 180_000 : false,
    });

    const insightSummary = insightsQuery.data?.summary;

    const notifCardTitle = useMemo(() => {
        const hasInsight = recentNotifications.some((n) => isPortfolioInsightNotificationType(n.type));
        return hasInsight
            ? t('dashboard.smartAlerts', 'Akıllı Uyarılar')
            : t('dashboard.recentNotifications', 'Son Bildirimler');
    }, [recentNotifications, t]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const portfolioDistRef = useRef<HTMLDivElement>(null);
    const newsListRef = useRef<HTMLDivElement>(null);
    /*
     * Haberler kartinin kendi yuksekligi, ust kolonlarin (Yildizlanan / Portfoy+Aktivite)
     * yuksegine gore stretch ile belirleniyor. Bunu doğrudan ölçüp haber sayısını adapte
     * ediyoruz; portfolioDistRef parent zincirine guvenmek artik dogru olmaz (Son Bildirimler
     * sag kolona tasindi). Bkz. news useLayoutEffect.
     */
    const newsCardRef = useRef<HTMLDivElement>(null);
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

    const buildSnapshotChartSeries = (rows: SnapshotRow[]): BalancePoint[] => {
        if (!Array.isArray(rows) || rows.length === 0) return [];
        const sorted = [...rows].sort((a, b) => new Date(a.snapshotAt).getTime() - new Date(b.snapshotAt).getTime());
        const latestByDay = new Map<string, { balance: number }>();
        sorted.forEach((row) => {
            const dt = new Date(row.snapshotAt);
            if (Number.isNaN(dt.getTime())) return;
            const key = dt.toISOString().slice(0, 10);
            latestByDay.set(key, {
                balance: Number(row.portfolioValueTry ?? 0),
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

    const unwrapSnapshotEnvelope = (res: AxiosResponse<unknown>): SnapshotRow[] => {
        const body = res.data as { data?: unknown };
        if (body && Array.isArray(body.data)) return body.data as SnapshotRow[];
        if (Array.isArray(res.data)) return res.data as SnapshotRow[];
        return [];
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
            const loadSnapshotsInRange = async (): Promise<SnapshotRow[]> => {
                try {
                    const res = await financeClient.get('/api/portfolio/snapshots/me', {
                        params: { from: start.toISOString(), to: end.toISOString() },
                    });
                    return unwrapSnapshotEnvelope(res);
                } catch {
                    return [];
                }
            };

            const phase1 = await Promise.allSettled([
                financeClient.get<SummaryResponse>('/api/dashboard/summary'),
                loadSnapshotsInRange(),
            ]);

            if (phase1[0].status === 'fulfilled') {
                setSummary(unwrapAxiosData(phase1[0].value));
            } else {
                if (!silent) {
                    console.warn('Dashboard özet isteği başarısız', phase1[0].reason);
                }
                setSummary(FALLBACK_SUMMARY);
            }

            if (phase1[1].status === 'fulfilled') {
                setBalancePoints(buildSnapshotChartSeries(phase1[1].value));
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

    const totalPortfolioTry = Number(summary?.totalPortfolioValueTry ?? summary?.portfolio?.totalValueTry ?? 0);

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

    // İlk yüklemede KPI animasyonu; sonraki polling'de sıçrama yok.
    const displayPortfolio = useCountUp(totalPortfolioTry);
    const displayCost = useCountUp(Number(summary?.portfolio?.totalCostTry ?? 0));
    const displayPnlValue = useCountUp(Number(summary?.portfolio?.totalPnlTry ?? 0));
    const displayPnlPct = useCountUp(Number(summary?.portfolio?.totalPnlPct ?? 0));

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

    const lastSnapshotDayLabel = useMemo(() => {
        if (!balancePoints.length) return null;
        return balancePoints[balancePoints.length - 1]?.date ?? null;
    }, [balancePoints]);

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

        // Haberler kartı artık sağ kolonda kendi yüksekliğini diğer kolonlarla stretch
        // alıyor. Hedef alanı doğrudan news-card'ın kendi yüksekliği belirliyor; bu sayede
        // "Son Bildirimler" kartının sağ kolonda altında ne kadar yer kapladığından bağımsız
        // olarak haber sayısı doğru ölçeklenir.
        const measureTarget = () => newsCardRef.current?.getBoundingClientRect().height ?? 0;

        const run = async () => {
            await Promise.resolve();
            if (!alive) return;

            const rowApprox = 58;
            const headerOffset = 56; // baslik + ust padding
            let size = Math.min(10, Math.max(5, Math.floor(Math.max(measureTarget() - headerOffset, 240) / rowApprox)));
            let items: NewsItem[] = [];

            for (let attempt = 0; attempt < 6; attempt++) {
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
                const targetH = measureTarget();
                const listH = newsListRef.current?.scrollHeight ?? 0;
                // 10px tolerans: ufak overflow varsa size'i azalt; alt bosluk varsa devam.
                const overflows = targetH > 0 && listH > targetH - headerOffset + 10;
                if (!overflows || size <= 5 || items.length < size) break;
                size = Math.max(5, size - 1);
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

            <div className="dashboard-kpi-grid">
                <DashboardKpiCard
                    label={t('dashboard.totalPortfolioValue', 'Toplam portföy değeri')}
                    value={formatMoney(displayPortfolio)}
                />
                <DashboardKpiCard label={t('dashboard.totalCostTry', 'Toplam maliyet (TRY)')} value={formatMoney(displayCost)} />
                <DashboardKpiCard label={t('dashboard.totalPnlTry', 'Toplam kar (PNL)')} value={formatMoney(displayPnlValue)} />
                <DashboardKpiCard
                    label={t('dashboard.realReturn', 'Reel K/Z')}
                    value={
                        realReturnTry != null
                            ? formatMoney(displayRealReturn)
                            : t('portfolio.realPnlWaitingCpi', 'TÜFE verisi bekleniyor')
                    }
                    sub={
                        realReturnPct != null
                            ? `${displayRealReturnPct.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%`
                            : undefined
                    }
                    valueClassName={
                        realReturnTry != null
                            ? realReturnTry >= 0
                                ? 'portfolio-pnl-pos'
                                : 'portfolio-pnl-neg'
                            : 'kpi-value-small'
                    }
                />
            </div>

            <div className="dashboard-card dashboard-chart-card">
                <div className="chart-card-header">
                    <h2 className="section-title">
                        {t('dashboard.portfolioSnapshotsChart', 'Portföy değeri (günlük anlık, TRY)')} —{' '}
                        {selectedFilter === '1A' ? '30' : '90'} {t('dashboard.days', 'gün')}
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
                                        <span>{formatMoney(displayCost)}</span>
                                    </div>
                                    <div className="portfolio-pnl-row">
                                        <span>Güncel değer</span>
                                        <span>{formatMoney(displayPortfolio)}</span>
                                    </div>
                                    <div className="portfolio-pnl-row">
                                        <span>Toplam kar (PNL)</span>
                                        <span
                                            className={
                                                (summary.portfolio?.totalPnlTry ?? 0) >= 0 ? 'portfolio-pnl-pos' : 'portfolio-pnl-neg'
                                            }
                                        >
                                            {formatMoney(displayPnlValue)}
                                        </span>
                                    </div>
                                    <div className="portfolio-pnl-row">
                                        <span>Kar oranı</span>
                                        <span
                                            className={
                                                (summary.portfolio?.totalPnlPct ?? 0) >= 0 ? 'portfolio-pnl-pos' : 'portfolio-pnl-neg'
                                            }
                                        >
                                            {displayPnlPct.toLocaleString('tr-TR', {
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
                        <h2 className="section-title">{t('dashboard.snapshotActivity', 'Portföy anlıkları')}</h2>
                        <div className="activity-row">
                            <Clock3 size={14} className="activity-icon" />
                            <div className="activity-text">
                                <p className="activity-label">{t('dashboard.lastSnapshotDay', 'Son günlük nokta')}</p>
                                <p className="activity-value">
                                    {lastSnapshotDayLabel ?? t('dashboard.noSnapshotYet', 'Henüz anlık yok')}
                                </p>
                            </div>
                        </div>
                        <div className="activity-row">
                            <Wallet size={14} className="activity-icon" />
                            <div className="activity-text">
                                <p className="activity-label">{t('dashboard.summaryPortfolioTry', 'Özet portföy (TRY)')}</p>
                                <p className="activity-value">{formatMoney(displayPortfolio)}</p>
                            </div>
                        </div>
                    </div>
                    </div>

                    {/*
                     * Sag kolon: Haberler (esnek/uzun) + Son Bildirimler (alt, sabit). Kullanici
                     * istegi: bildirim karti bos kalan sag alti doldursun, sayfanin tum
                     * kolonlari ayni alt cizgide bitsin. Haber sayisi hesabi dinamik (newsCardRef
                     * uzerinden olculur), yetersizse scroll uyutulur.
                     */}
                    <div className="dashboard-right-column">
                    <div ref={newsCardRef} className="dashboard-card dashboard-news-card dashboard-news-card--fill">
                        <h2 className="section-title">{t('dashboard.latestNews', 'En Son Haberler')}</h2>
                        <div
                            ref={newsListRef}
                            className={`news-list news-list--stretch${newsFadeIn ? ' news-list--ready' : ''}${
                                newsLoading ? ' news-list--loading' : ''
                            }`}
                        >
                            {newsLoading && latestNews.length === 0 ? (
                                <div className="news-skeleton-block" aria-hidden="true">
                                    {Array.from({ length: 8 }).map((_, i) => (
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
                    <div className="dashboard-card dashboard-compact-card dashboard-notif-card">
                        <div className="dashboard-notif-card__header">
                            <span className="dashboard-notif-card__icon" aria-hidden>
                                <Bell size={14} />
                            </span>
                            <h2 className="section-title dashboard-notif-card__title">{notifCardTitle}</h2>
                            <button
                                type="button"
                                className="dashboard-notif-card__all"
                                onClick={() => navigate('/notifications')}
                                title={t('dashboard.allNotifications', 'Tümünü gör')}
                            >
                                {t('dashboard.allNotifications', 'Tümü')}
                                <ChevronRight size={12} strokeWidth={2.5} aria-hidden />
                            </button>
                        </div>
                        {notificationsLoading && recentNotifications.length === 0 ? (
                            <div className="dashboard-notif-skeleton" aria-hidden="true">
                                {Array.from({ length: 3 }).map((_, i) => (
                                    <div key={i} className="dashboard-notif-skel-row">
                                        <div className="skeleton-dot" />
                                        <div className="skeleton-line skeleton-row-main" />
                                    </div>
                                ))}
                            </div>
                        ) : null}
                        {!notificationsLoading && recentNotifications.length === 0 ? (
                            <p className="dashboard-muted dashboard-notif-empty">
                                {t('dashboard.noNotifications', 'Henüz bildirim yok.')}
                            </p>
                        ) : null}
                        <ul className="dashboard-notif-list">
                            {recentNotifications.map((n) => {
                                const cat = classifyNotification(n.type);
                                const color = notifCategoryColor(cat);
                                const occurredAt = n.lastOccurredAt ?? n.createdAt;
                                const isUnread = !n.readAt;
                                return (
                                    <li
                                        key={n.id}
                                        className={`dashboard-notif-item${isUnread ? ' is-unread' : ''}`}
                                    >
                                        <button
                                            type="button"
                                            className="dashboard-notif-item__btn"
                                            onClick={() => navigate('/notifications')}
                                            title={t('notifications.viewDetail', 'Detayı gör')}
                                        >
                                            <span
                                                className="dashboard-notif-item__icon"
                                                style={{ color }}
                                                aria-hidden
                                            >
                                                <NotifCategoryIcon category={cat} size={14} />
                                            </span>
                                            <span className="dashboard-notif-item__title">{n.title}</span>
                                            <span className="dashboard-notif-item__date">
                                                {new Date(occurredAt).toLocaleString(uiLocale, {
                                                    day: '2-digit',
                                                    month: '2-digit',
                                                    hour: '2-digit',
                                                    minute: '2-digit',
                                                })}
                                            </span>
                                        </button>
                                    </li>
                                );
                            })}
                        </ul>
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