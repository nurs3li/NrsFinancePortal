import { useState, useEffect, useCallback, useMemo, useRef, type CSSProperties } from 'react';
import { useNavigate } from 'react-router-dom';
import { financeClient, marketClient } from '../api/client';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
import { useTheme } from '../theme/ThemeContext';
import { Coins, DollarSign, TrendingUp, type LucideIcon } from 'lucide-react';
import { Area, AreaChart, CartesianGrid, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatAssetLabel, getDynamicLogoUrl, type MarketType } from '../lib/assetBranding';
import { AssetLogo } from '../components/AssetLogo';
import './Dashboard.css';

type WhaleSummary = { level?: string; impactScore?: number; triggeredAt?: string };
type CashSummary = { amountTry?: number };
type PortfolioSummary = { totalValueTry?: number; distribution?: Record<string, number> };
type ActivitySummary = { lastTradeAt?: string };

type SummaryResponse = {
    whale?: WhaleSummary;
    cash?: CashSummary;
    portfolio?: PortfolioSummary;
    activity?: ActivitySummary;
};

type TxRow = { id: number; balanceAfter: number; createdAt: string };
type LatestPrice = { symbol?: string; buyPrice?: number; sellPrice?: number; price?: number; status?: string };
type NewsItem = { id: number; title: string; source: string | null; publishedAt: string };
type NewsPage = { content: NewsItem[] };
type TimeFilter = '1A' | '3A';

type StarAsset = {
    key: string;
    label: string;
    code: string;
    marketType: MarketType;
    symbol: string;
    price: number;
    change24h: number;
};

const TIME_FILTERS: { id: TimeFilter; label: string }[] = [
    { id: '1A', label: '1A' },
    { id: '3A', label: '3A' },
];

export function Dashboard() {
    const navigate = useNavigate();
    const { theme, tokens } = useTheme();
    const [summary, setSummary] = useState<SummaryResponse | null>(null);
    const [balancePoints, setBalancePoints] = useState<{ date: string; balance: number }[]>([]);
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

    const fetchDashboard = useCallback(async (silent = false) => {
        const showLoader = !silent || !hasLoadedOnceRef.current;
        if (showLoader) setLoading(true);
        if (!silent) setError(null);
        try {
            const end = new Date();
            const start = getFilterStart(selectedFilter);
            const [summaryRes, txRes, fxRes, metalsRes, cryptoRes, fundsRes, equityRes, newsRes] = await Promise.all([
                financeClient.get('/api/dashboard/summary'),
                financeClient.get<{ content?: TxRow[] }>('/api/transactions/me/range', {
                    params: { start: start.toISOString(), end: end.toISOString(), page: 0, size: 500 },
                }),
                marketClient.get<Record<string, LatestPrice>>('/api/market/doviz/latest'),
                marketClient.get<Record<string, LatestPrice>>('/api/market/metals/latest'),
                marketClient.get<Record<string, LatestPrice>>('/api/market/crypto/latest'),
                marketClient.get<Record<string, LatestPrice>>('/api/market/funds/latest'),
                marketClient.get<Record<string, LatestPrice>>('/api/market/equity/latest'),
                marketClient.get<NewsPage>('/api/news', { params: { page: 0, size: 3 } }),
            ]);

            const rawSummary = summaryRes.data?.data ?? summaryRes.data;
            setSummary(rawSummary);

            const txContent = txRes.data?.content ?? txRes.data ?? [];
            const txList = Array.isArray(txContent) ? txContent : [];
            const sortedTx = [...txList].sort(
                (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
            );
            const chartData = sortedTx.map((tx) => ({
                date: new Date(tx.createdAt).toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit' }),
                balance: Number(tx.balanceAfter),
            }));
            setBalancePoints(chartData);

            const fx = fxRes.data ?? {};
            const metals = metalsRes.data ?? {};
            const crypto = cryptoRes.data ?? {};
            const funds = fundsRes.data ?? {};
            const equity = equityRes.data ?? {};
            const newsContent = newsRes.data?.content ?? [];
            setLatestNews(newsContent.slice(0, 3));

            const datasets: { marketType: MarketType; data: Record<string, LatestPrice> }[] = [
                { marketType: 'FX', data: fx },
                { marketType: 'METALS', data: metals },
                { marketType: 'CRYPTO', data: crypto },
                { marketType: 'FUNDS', data: funds },
                { marketType: 'EQUITY', data: equity },
            ];

            const starRows: StarAsset[] = datasets.flatMap(({ marketType, data }) =>
                Object.entries(data)
                    .filter(([, row]) => row && typeof row === 'object' && row.status !== 'NO_DATA')
                    .map(([symbol, row]) => ({
                        key: `${marketType}-${symbol}`,
                        label: formatAssetLabel(symbol, marketType),
                        code: symbol,
                        marketType,
                        symbol,
                        price: getPrice(row),
                        change24h: 0,
                    }))
            );

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
            hasLoadedOnceRef.current = true;
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
    const distributionRows = Object.entries(distribution);
    const donutPercent = useMemo(() => {
        if (!distributionRows.length || totalPortfolio <= 0) return 0;
        const maxAsset = Math.max(...distributionRows.map(([, value]) => Number(value) || 0));
        return Math.min(100, Math.round((maxAsset / totalPortfolio) * 100));
    }, [distributionRows, totalPortfolio]);

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
                <p className="dashboard-muted">Ozet verisi bulunamadi.</p>
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
                    <p className="kpi-label">Toplam Portfoy Degeri</p>
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
                    <p className="kpi-label">Toplam Bakiye (Portfoy + Nakit)</p>
                    <p className="kpi-value">{formatMoney(totalBalance)}</p>
                </div>
                <div className="dashboard-card">
                    <p className="kpi-label">Balina Seviyesi</p>
                    <p className="kpi-value kpi-value-small">{summary.whale?.level ?? 'L1_LARGE_TRADER'}</p>
                    <p className="kpi-subtext">Kontrol Tarihi: {controlDate}</p>
                </div>
            </div>

            <div className="dashboard-card dashboard-chart-card">
                <div className="chart-card-header">
                    <h2 className="section-title">Bakiye grafigi (son 30 gun)</h2>
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
                        <p className="dashboard-muted">Bu aralikta islem yok; grafik olusturulamadi.</p>
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
                                    contentStyle={{ borderRadius: 12, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text }}
                                />
                                <Area type="monotone" dataKey="balance" stroke="none" fill="url(#balanceFill)" />
                                <Line type="monotone" dataKey="balance" stroke="#3182CE" strokeWidth={3} dot={false} />
                            </AreaChart>
                        </ResponsiveContainer>
                    )}
                </div>
            </div>

            <div className="dashboard-bottom-grid">
                <div className="dashboard-card">
                    <h2 className="section-title">Yildizlanan Varliklar</h2>
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
                    <div className="dashboard-card">
                        <h2 className="section-title">Portfoy Dagilimi</h2>
                        <div className="donut-placeholder" style={{ ['--fill' as string]: `${donutPercent}%` }}>
                            <div className="donut-inner">Dagilim</div>
                        </div>
                        <p className="dashboard-muted">Dagilim verisi yok.</p>
                    </div>

                    <div className="dashboard-card">
                        <h2 className="section-title">Son Aktivite & Uyarilar</h2>
                        <div className="activity-row">
                            <span className="status-dot" />
                            <p>Balina seviyesi: {summary.whale?.level ?? 'L1_LARGE_TRADER'}</p>
                        </div>
                        <div className="activity-row">
                            <span className="news-dot">📰</span>
                            <p>
                                Son islem:{' '}
                                {summary.activity?.lastTradeAt
                                    ? new Date(summary.activity.lastTradeAt).toLocaleString('tr-TR')
                                    : 'Kayit bulunamadi'}
                            </p>
                        </div>
                    </div>
                </div>

                <div className="dashboard-card">
                    <h2 className="section-title">En Son Haberler</h2>
                    <div className="news-list">
                        {latestNews.length === 0 ? (
                            <p className="dashboard-muted">Haber verisi bulunamadi.</p>
                        ) : (
                            latestNews.map((item) => (
                                <button
                                    type="button"
                                    className="news-item news-item-button"
                                    key={item.id}
                                    onClick={() => navigate('/news')}
                                    title="Haberler sayfasina git"
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