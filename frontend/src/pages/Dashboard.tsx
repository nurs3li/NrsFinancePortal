import { useState, useEffect, useCallback, useMemo, useRef, type CSSProperties } from 'react';
import { useNavigate } from 'react-router-dom';
import type { AxiosResponse } from 'axios';
import { financeClient, marketClient } from '../api/client';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
import { useTheme } from '../theme/ThemeContext';
import { Coins, DollarSign, TrendingUp, type LucideIcon } from 'lucide-react';
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
type NewsItem = { id: number; title: string; source: string | null; publishedAt: string };
type NewsPage = { content: NewsItem[] };
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

type StarAsset = {
    key: string;
    label: string;
    code: string;
    marketType: MarketType;
    symbol: string;
    price: number;
    change24h: number;
};
type BalancePoint = { key: string; date: string; balance: number };

const TIME_FILTERS: { id: TimeFilter; label: string }[] = [
    { id: '1A', label: '1 ay' },
    { id: '3A', label: '3 ay' },
];

export function Dashboard() {
    const navigate = useNavigate();
    const { theme, tokens } = useTheme();
    const [summary, setSummary] = useState<SummaryResponse | null>(null);
    const [balancePoints, setBalancePoints] = useState<BalancePoint[]>([]);
    const [selectedFilter, setSelectedFilter] = useState<TimeFilter>('1A');
    const [starAssets, setStarAssets] = useState<StarAsset[]>([]);
    const [latestNews, setLatestNews] = useState<NewsItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

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
                marketClient.get<NewsPage>('/api/news', { params: { page: 0, size: 3 } }),
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
            const newsPage = pickData<NewsPage>(5);
            const newsContent = newsPage?.content ?? [];
            setLatestNews(newsContent.slice(0, 3));

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
                return {
                    key: `${marketType}-${symbol}`,
                    label: formatAssetLabel(symbol, marketType),
                    code: symbol,
                    marketType,
                    symbol,
                    price: getPrice(row),
                    change24h: 0,
                } satisfies StarAsset;
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
                starRows.map((row, idx) => ({
                    ...row,
                    change24h:
                        changeResults[idx].status === 'fulfilled'
                            ? Number(changeResults[idx].value.toFixed(2))
                            : 0,
                }))
            );
        } catch (err: any) {
            const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'Bilinmeyen hata';
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

    const formatMoney = (v: number) => '₺' + v.toLocaleString('tr-TR', { maximumFractionDigits: 2 });
    const controlDate = summary?.whale?.triggeredAt
        ? new Date(summary.whale.triggeredAt).toLocaleDateString('tr-TR')
        : '12.01.2026';
    const totalCash = summary?.cash?.amountTry ?? 0;
    const totalPortfolio = summary?.portfolio?.totalValueTry ?? 0;
    const totalBalance = totalCash + totalPortfolio;
    const distribution = summary?.portfolio?.distribution ?? {};
    const distributionSlices = useMemo(() => {
        const rows = Object.entries(distribution)
            .map(([key, value]) => ({ key, value: Number(value) || 0 }))
            .filter((r) => r.value > 0)
            .sort((a, b) => b.value - a.value);
        if (!rows.length || totalPortfolio <= 0) return [] as { key: string; value: number; pct: number }[];
        return rows.map((r) => ({
            ...r,
            pct: (r.value / totalPortfolio) * 100,
        }));
    }, [distribution, totalPortfolio]);

    const donutBackground = useMemo(
        () => buildDistributionConicGradient(distributionSlices, '#3182CE', tokens.border),
        [distributionSlices, tokens.border]
    );

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
                    <h1 className="dashboard-title">Dashboard</h1>
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
                        {Array.from({ length: 3 }).map((_, i) => (
                            <div key={i} className="skeleton-news-row">
                                <div className="skeleton-dot" />
                                <div className="skeleton-line skeleton-row-main" />
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        );
    }
    if (error) {
        return (
            <div className="saas-dashboard" style={dashboardVars}>
                <h1 className="dashboard-title">Dashboard</h1>
                <p className="dashboard-error">Hata: {error}</p>
            </div>
        );
    }
    if (!summary) {
        return (
            <div className="saas-dashboard" style={dashboardVars}>
                <h1 className="dashboard-title">Dashboard</h1>
                <p className="dashboard-muted">Özet verisi bulunamadı.</p>
            </div>
        );
    }

    return (
        <div
            className="saas-dashboard"
            style={dashboardVars}
        >
            <div className="dashboard-headline">
                <h1 className="dashboard-title">Dashboard</h1>
            </div>

            <div className="dashboard-kpi-grid">
                <div className="dashboard-card">
                    <p className="kpi-label">Toplam portföy değeri</p>
                    <p className="kpi-value">{formatMoney(totalPortfolio)}</p>
                    <p className="kpi-subtext">
                        Balina etkisi skoru:{' '}
                        {typeof summary.whale?.impactScore === 'number' ? summary.whale.impactScore : 'Veri yok'}
                    </p>
                </div>
                <div className="dashboard-card">
                    <p className="kpi-label">Nakit (TRY)</p>
                    <p className="kpi-value">{formatMoney(totalCash)}</p>
                </div>
                <div className="dashboard-card">
                    <p className="kpi-label">Toplam bakiye (portföy + nakit)</p>
                    <p className="kpi-value">{formatMoney(totalBalance)}</p>
                </div>
                <div className="dashboard-card">
                    <p className="kpi-label">Balina seviyesi</p>
                    <p className="kpi-value kpi-value-small">{summary.whale?.level ?? 'L1_LARGE_TRADER'}</p>
                    <p className="kpi-subtext">Kontrol Tarihi: {controlDate}</p>
                </div>
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
                    {balancePoints.length === 0 ? (
                        <p className="dashboard-muted">Bu aralıkta işlem yok; grafik oluşturulamadı.</p>
                    ) : (
                        <ResponsiveContainer width="100%" height="100%">
                            <AreaChart data={balancePoints} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
                                <defs>
                                    <linearGradient id="balanceFill" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="5%" stopColor="#3182CE" stopOpacity={0.25} />
                                        <stop offset="95%" stopColor="#3182CE" stopOpacity={0.02} />
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
                                    formatter={(value: number | undefined) => [formatMoney(Number(value ?? 0)), 'Bakiye']}
                                    labelFormatter={(label: any) => `Tarih: ${String(label ?? '')}`}
                                    contentStyle={{ borderRadius: 12, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text }}
                                />
                                <Area type="monotone" dataKey="balance" stroke="#3182CE" strokeWidth={3} fill="url(#balanceFill)" />
                            </AreaChart>
                        </ResponsiveContainer>
                    )}
                </div>
            </div>

            <div className="dashboard-bottom-grid">
                <div className="dashboard-card">
                    <h2 className="section-title">Yıldızlanan varlıklar</h2>
                    <div className="assets-table">
                        {starAssets.map((asset) => {
                            const logoUrl = getDynamicLogoUrl(asset.code, asset.marketType);
                            const fallback = getFallbackIcon(asset.marketType);
                            return (
                                <div className="asset-row" key={asset.key}>
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
                                    <p className="asset-price">{asset.price > 0 ? asset.price.toLocaleString('tr-TR') : '-'}</p>
                                    <p className={`asset-change ${asset.change24h >= 0 ? 'up' : 'down'}`}>
                                        {asset.change24h >= 0 ? '+' : ''}
                                        {asset.change24h.toLocaleString('tr-TR')}%
                                    </p>
                                </div>
                            );
                        })}
                    </div>
                </div>

                <div className="dashboard-middle-column">
                    <div className="dashboard-card portfolio-dist-card">
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

                    <div className="dashboard-card">
                        <h2 className="section-title">Son aktivite ve uyarılar</h2>
                        <div className="activity-row">
                            <span className="status-dot" />
                            <p>Balina seviyesi: {summary.whale?.level ?? 'L1_LARGE_TRADER'}</p>
                        </div>
                        <div className="activity-row">
                            <span className="news-dot">📰</span>
                            <p>
                                Son işlem:{' '}
                                {summary.activity?.lastTradeAt
                                    ? new Date(summary.activity.lastTradeAt).toLocaleString('tr-TR')
                                    : 'Kayıt bulunamadı'}
                            </p>
                        </div>
                    </div>
                </div>

                <div className="dashboard-card">
                    <h2 className="section-title">En Son Haberler</h2>
                    <div className="news-list">
                        {latestNews.length === 0 ? (
                            <p className="dashboard-muted">Haber verisi bulunamadı.</p>
                        ) : (
                            latestNews.map((item) => (
                                <button
                                    type="button"
                                    className="news-item news-item-button"
                                    key={item.id}
                                    onClick={() => navigate('/news')}
                                    title="Haberler sayfasına git"
                                >
                                    <span className="news-logo">{item.source?.[0] ?? 'N'}</span>
                                    <div>
                                        <p className="news-title">{item.title}</p>
                                        <p className="news-meta">
                                            {item.source ?? 'Kaynak'} · {new Date(item.publishedAt).toLocaleString('tr-TR')}
                                        </p>
                                    </div>
                                </button>
                            ))
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}