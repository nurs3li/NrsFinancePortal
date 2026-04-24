import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { financeClient, marketClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
import { Coins, DollarSign, TrendingUp, type LucideIcon } from 'lucide-react';
import type { LatestPriceRow, MarketDashboard, TabId } from '../components/market/marketTypes';
import {
    formatAssetLabel,
    getDynamicLogoUrl,
    tabIdToMarketKind,
    type MarketKind,
} from '../lib/assetBranding';
import { AssetLogo } from '../components/AssetLogo';
import { sparklineClosesFor, volatilityForSymbol } from '../components/market/marketSparklineMap';
import { MarketSparkline } from '../components/market/MarketSparkline';
import { MarketFinvizTreemap } from '../components/market/MarketFinvizTreemap';
import { MarketIndicatorAreaChart } from '../components/market/MarketIndicatorAreaChart';
import { MarketCompareLwChart } from '../components/market/MarketCompareLwChart';

type IndicatorPoint = {
    t: string;
    value: number;
};

type IndicatorsResponse = {
    type: string;
    symbol: string;
    days: number;
    close: IndicatorPoint[];
    ma: Record<string, IndicatorPoint[]>;
    trend: {
        direction: string;
        slope: number;
        normalizedReturn: number;
        strength: number;
    };
};

type CandlePoint = {
    t: string;
    o: number;
    h: number;
    l: number;
    c: number;
    v: number;
};

type BatchHistoryResponse = {
    series: Record<string, CandlePoint[]>;
};

type CompareRow = { time: string; values: Record<string, number> };
type StarSelection = { marketType: string; symbol: string; position?: number };
type StarredAssetsResponse = {
    maxItems: number;
    selected: StarSelection[];
    resolved: { marketType: string; symbol: string; position: number; defaultFilled: boolean }[];
};

const DAYS_OPTIONS = [7, 14, 30];
const COMPARE_COLORS = ['#38bdf8', '#22c55e', '#eab308', '#f87171'];

function fallbackForMarket(m: MarketKind): { Icon: LucideIcon; color: string } {
    switch (m) {
        case 'FX':
            return { Icon: DollarSign, color: '#22c55e' };
        case 'METALS':
            return { Icon: Coins, color: '#eab308' };
        case 'CRYPTO':
            return { Icon: TrendingUp, color: '#f7931a' };
        case 'FUNDS':
        case 'EQUITY':
            return { Icon: TrendingUp, color: '#38bdf8' };
        default:
            return { Icon: TrendingUp, color: '#94a3b8' };
    }
}

function resolveBuy(row: LatestPriceRow): number | null {
    if (row.status === 'NO_DATA') return null;
    if (row.buyPrice != null) return Number(row.buyPrice);
    if (row.buy != null) return Number(row.buy);
    if (row.price != null) return Number(row.price);
    return null;
}

function resolveSell(row: LatestPriceRow): number | null {
    if (row.status === 'NO_DATA') return null;
    if (row.sellPrice != null) return Number(row.sellPrice);
    if (row.sell != null) return Number(row.sell);
    return null;
}

/** Sparkline uçlarından yaklaşık trend % (tablo Trend sütunu). */
function sparkRangeChangePct(closes: number[]): number | null {
    if (!closes || closes.length < 2) return null;
    const a = closes[0];
    const b = closes[closes.length - 1];
    if (a == null || b == null || !Number.isFinite(a) || !Number.isFinite(b) || a === 0) return null;
    return ((b - a) / a) * 100;
}

function getMarketType(tab: TabId): 'FX' | 'CRYPTO' | 'METALS' | 'FUNDS' | 'EQUITY' {
    switch (tab) {
        case 'doviz':
            return 'FX';
        case 'crypto':
            return 'CRYPTO';
        case 'metals':
            return 'METALS';
        case 'funds':
            return 'FUNDS';
        case 'equity':
            return 'EQUITY';
        default:
            return 'FX';
    }
}

function latestMapForTab(latest: MarketDashboard['latest'], tab: TabId): Record<string, LatestPriceRow> {
    switch (tab) {
        case 'doviz':
            return latest.doviz ?? {};
        case 'crypto':
            return latest.crypto ?? {};
        case 'metals':
            return latest.metals ?? {};
        case 'funds':
            return latest.funds ?? {};
        case 'equity':
            return latest.stocks ?? {};
        default:
            return {};
    }
}

export function Market() {
    const { tokens } = useTheme();
    const navigate = useNavigate();
    const queryClient = useQueryClient();

    const {
        data: dashboard,
        error: dashboardError,
        isLoading: dashboardLoading,
        refetch: refetchDashboard,
    } = useQuery({
        queryKey: ['market', 'dashboard'],
        queryFn: () => financeClient.get<MarketDashboard>('/api/market/dashboard').then((r) => r.data),
        refetchInterval: 60_000,
    });

    useRefetchOnFocus(() => {
        void queryClient.invalidateQueries({ queryKey: ['market', 'dashboard'] });
    });
    usePolling(() => {
        void refetchDashboard();
    }, 60_000);

    const [activeTab, setActiveTab] = useState<TabId>('doviz');
    const [chartSymbol, setChartSymbol] = useState<string>('USDTRY');
    const [chartDays, setChartDays] = useState(7);
    const [selectedMa, setSelectedMa] = useState<string[]>(['7', '30']);

    const [compareSymbols, setCompareSymbols] = useState<string[]>([]);
    const [compareDays, setCompareDays] = useState(7);
    const [compareCategory, setCompareCategory] = useState<TabId>('doviz');
    const [compareRows, setCompareRows] = useState<CompareRow[]>([]);
    const [loadingCompare, setLoadingCompare] = useState(false);
    const [savingStarKey, setSavingStarKey] = useState<string | null>(null);

    const { data: starredData, refetch: refetchStarred } = useQuery({
        queryKey: ['me', 'starred-assets'],
        queryFn: () => financeClient.get<StarredAssetsResponse>('/api/me/starred-assets').then((r) => r.data),
        staleTime: 60_000,
    });

    const fetchIndicators = useCallback((tab: TabId, symbol: string, days: number) => {
        const type = getMarketType(tab);
        const allMa = [7, 30, 90];
        const allowedMa = allMa.filter((w) => w <= days);
        const maParam = allowedMa.join(',');

        return marketClient
            .get<IndicatorsResponse>('/api/market/indicators', {
                params: { type, symbol, days, ma: maParam },
            })
            .then((res) => res.data);
    }, []);

    const fetchBatchHistory = useCallback((tab: TabId, symbols: string[], days: number) => {
        const type = getMarketType(tab);
        if (symbols.length < 2) {
            return Promise.resolve<BatchHistoryResponse>({ series: {} });
        }
        return marketClient
            .get<BatchHistoryResponse>('/api/market/history/batch', {
                params: { type, symbols: symbols.join(','), days },
            })
            .then((res) => res.data);
    }, []);

    const getSymbolsForTab = useCallback(
        (tab: TabId): string[] => {
            if (!dashboard) return [];
            const data = latestMapForTab(dashboard.latest, tab);
            return Object.keys(data).filter(
                (k) => data[k] && typeof data[k] === 'object' && data[k].status !== 'NO_DATA',
            );
        },
        [dashboard],
    );

    const {
        data: indicatorData,
        isFetching: loadingChart,
    } = useQuery({
        queryKey: ['market', 'indicators', activeTab, chartSymbol, chartDays],
        queryFn: () => fetchIndicators(activeTab, chartSymbol, chartDays),
        enabled: Boolean(chartSymbol),
        staleTime: 120_000,
    });
    const chartClose = indicatorData?.close ?? [];
    const chartMa = indicatorData?.ma ?? {};

    const loadCompare = useCallback(() => {
        if (compareSymbols.length < 2) {
            setCompareRows([]);
            return;
        }
        setLoadingCompare(true);
        const symbols = compareSymbols.slice(0, 4);

        fetchBatchHistory(compareCategory, symbols, compareDays)
            .then((batch) => {
                const byDate: Record<string, Record<string, number>> = {};
                Object.entries(batch.series).forEach(([sym, candles]) => {
                    candles.forEach((c) => {
                        const d = new Date(c.t).toISOString().slice(0, 10);
                        if (!byDate[d]) byDate[d] = {};
                        byDate[d][sym] = Number(c.c);
                    });
                });

                const dates = Object.keys(byDate).sort();
                if (dates.length === 0) {
                    setCompareRows([]);
                    return;
                }

                const first: Record<string, number> = {};
                symbols.forEach((sym) => {
                    const d = dates.find((dt) => byDate[dt][sym] != null);
                    if (d != null) first[sym] = byDate[d][sym];
                });

                const out: CompareRow[] = dates.map((date) => {
                    const values: Record<string, number> = {};
                    symbols.forEach((sym) => {
                        const v = byDate[date][sym];
                        const base = first[sym];
                        values[sym] =
                            base && v != null ? Math.round((v / base) * 1000) / 10 : Number.NaN;
                    });
                    return { time: date, values };
                });

                setCompareRows(out);
            })
            .catch(() => setCompareRows([]))
            .finally(() => setLoadingCompare(false));
    }, [compareSymbols, compareDays, compareCategory, fetchBatchHistory]);

    useEffect(() => {
        if (compareSymbols.length >= 2) loadCompare();
        else setCompareRows([]);
    }, [compareSymbols, compareDays, compareCategory, loadCompare]);

    const pageStyle: React.CSSProperties = {
        padding: 24,
        background: tokens.bg,
        color: tokens.text,
        minHeight: '100%',
    };
    const titleStyle: React.CSSProperties = {
        fontSize: '1.75rem',
        fontWeight: 700,
        marginBottom: 4,
    };
    const mutedStyle: React.CSSProperties = {
        color: tokens.textMuted,
        fontSize: '0.875rem',
    };
    const cardStyle: React.CSSProperties = {
        padding: 16,
        borderRadius: 12,
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        marginBottom: 16,
    };

    const tabs: { id: TabId; label: string }[] = [
        { id: 'doviz', label: 'Döviz' },
        { id: 'crypto', label: 'Kripto' },
        { id: 'metals', label: 'Altın' },
        { id: 'funds', label: 'Fonlar' },
        { id: 'equity', label: 'Hisse' },
    ];

    const tableData = dashboard ? latestMapForTab(dashboard.latest, activeTab) : {};
    const tableEntries = Object.entries(tableData).filter(([, v]) => v && typeof v === 'object');
    const symbolsForTab = getSymbolsForTab(activeTab);
    const symbolsForCompare = getSymbolsForTab(compareCategory);

    const lineWidthBySymbol = useMemo(() => {
        const out: Record<string, number> = {};
        if (!dashboard) return out;
        const slice = compareSymbols.slice(0, 4);
        const vols = slice.map((s) => volatilityForSymbol(dashboard, compareCategory, s));
        const max = Math.max(...vols.map((v) => (v > 0 ? v : 0)), 0.0001);
        for (const sym of slice) {
            const v = volatilityForSymbol(dashboard, compareCategory, sym);
            out[sym] = 2 + Math.min(2.5, (v / max) * 2.5);
        }
        return out;
    }, [dashboard, compareCategory, compareSymbols]);

    const selectedStars = useMemo(
        () => (starredData?.selected ?? []).map((s) => `${s.marketType}|${s.symbol}`),
        [starredData?.selected],
    );

    const selectedStarSet = useMemo(() => new Set(selectedStars), [selectedStars]);

    const toggleStar = useCallback(
        async (marketType: string, symbol: string) => {
            const key = `${marketType}|${symbol}`;
            const selected = [...(starredData?.selected ?? [])];
            const exists = selected.some((s) => `${s.marketType}|${s.symbol}` === key);

            let next: StarSelection[];
            if (exists) {
                next = selected.filter((s) => `${s.marketType}|${s.symbol}` !== key);
            } else {
                const maxItems = starredData?.maxItems ?? 7;
                if (selected.length >= maxItems) {
                    window.alert(`En fazla ${maxItems} varlık yıldızlanabilir.`);
                    return;
                }
                next = [...selected, { marketType, symbol }];
            }

            try {
                setSavingStarKey(key);
                await financeClient.put('/api/me/starred-assets', { selected: next });
                await refetchStarred();
            } finally {
                setSavingStarKey(null);
            }
        },
        [refetchStarred, starredData?.maxItems, starredData?.selected],
    );

    const handleGoToAdvanced = () => {
        if (!chartSymbol) return;
        const type = getMarketType(activeTab);
        const url = `/market/advanced?type=${type}&symbol=${encodeURIComponent(
            chartSymbol,
        )}&days=${chartDays}`;
        navigate(url);
    };

    const errMsg =
        dashboardError instanceof Error
            ? dashboardError.message
            : dashboardError != null
              ? String((dashboardError as unknown as { message?: string }).message ?? dashboardError)
              : undefined;

    if (errMsg) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Piyasa</h1>
                <p style={{ ...mutedStyle, color: tokens.error }}>Hata: {errMsg}</p>
            </div>
        );
    }

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Piyasa</h1>
            <p style={mutedStyle}>
                Finansal özet, sektör ısı haritası ve TradingView Lightweight Charts ile profesyonel
                görünüm.
            </p>

            <div
                style={{
                    display: 'flex',
                    gap: 8,
                    marginBottom: 20,
                    flexWrap: 'wrap',
                }}
            >
                {tabs.map((t) => (
                    <button
                        key={t.id}
                        type="button"
                        onClick={() => {
                            setActiveTab(t.id);
                            const syms = getSymbolsForTab(t.id);
                            setChartSymbol(syms[0] ?? '');
                        }}
                        style={{
                            padding: '8px 16px',
                            fontSize: '0.875rem',
                            fontWeight: activeTab === t.id ? 600 : 500,
                            background: activeTab === t.id ? tokens.accent : tokens.bgCard,
                            color: activeTab === t.id ? '#fff' : tokens.text,
                            border: `1px solid ${tokens.border}`,
                            borderRadius: 8,
                            cursor: 'pointer',
                        }}
                    >
                        {t.label}
                    </button>
                ))}
            </div>

            {dashboardLoading ? (
                <p style={mutedStyle}>Yükleniyor...</p>
            ) : (
                <div
                    style={{
                        display: 'grid',
                        gridTemplateColumns: 'minmax(0, 1fr) minmax(280px, 440px)',
                        gap: 16,
                        alignItems: 'start',
                    }}
                    className="market-dashboard-grid"
                >
                    <style>{`
            @media (max-width: 1024px) {
              .market-dashboard-grid { grid-template-columns: 1fr !important; }
            }
          `}</style>

                    <div>
                        <div style={cardStyle}>
                            <h2
                                style={{
                                    fontSize: '1rem',
                                    fontWeight: 600,
                                    marginBottom: 12,
                                }}
                            >
                                Güncel fiyatlar
                            </h2>
                            {tableEntries.length === 0 ? (
                                <p style={mutedStyle}>Bu kategoride veri yok.</p>
                            ) : (
                                <div style={{ overflowX: 'auto' }}>
                                    <table
                                        style={{
                                            width: '100%',
                                            borderCollapse: 'collapse',
                                            fontSize: '0.9375rem',
                                        }}
                                    >
                                        <thead>
                                            <tr
                                                style={{
                                                    borderBottom: `2px solid ${tokens.border}`,
                                                }}
                                            >
                                                <th style={{ textAlign: 'left', padding: 12 }}>
                                                    Sembol
                                                </th>
                                                <th
                                                    style={{
                                                        textAlign: 'right',
                                                        padding: 12,
                                                        minWidth: 108,
                                                    }}
                                                >
                                                    Trend (%)
                                                </th>
                                                <th style={{ textAlign: 'right', padding: 12 }}>
                                                    Alış
                                                </th>
                                                <th style={{ textAlign: 'right', padding: 12 }}>
                                                    Satış
                                                </th>
                                                <th style={{ textAlign: 'left', padding: 12 }}>
                                                    Kaynak / Tarih
                                                </th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {tableEntries.map(([sym, row]) => {
                                                const buy = resolveBuy(row);
                                                const sell = resolveSell(row);
                                                const noData = row.status === 'NO_DATA';
                                                const mk = tabIdToMarketKind(activeTab);
                                                const logoUrl = getDynamicLogoUrl(sym, mk);
                                                const fb = fallbackForMarket(mk);
                                                const spark = sparklineClosesFor(
                                                    dashboard,
                                                    activeTab,
                                                    sym,
                                                );
                                                const trendPct =
                                                    spark && spark.length >= 2
                                                        ? sparkRangeChangePct(spark)
                                                        : null;
                                                const starKey = `${getMarketType(activeTab)}|${sym}`;
                                                const isStarred = selectedStarSet.has(starKey);
                                                const starBusy = savingStarKey === starKey;
                                                return (
                                                    <tr
                                                        key={sym}
                                                        style={{
                                                            borderBottom: `1px solid ${tokens.border}`,
                                                        }}
                                                    >
                                                        <td style={{ padding: 12 }}>
                                                            <div
                                                                style={{
                                                                    display: 'flex',
                                                                    alignItems: 'center',
                                                                    gap: 10,
                                                                }}
                                                            >
                                                                <AssetLogo
                                                                    src={logoUrl}
                                                                    alt=""
                                                                    fallbackIcon={fb.Icon}
                                                                    fallbackColor={fb.color}
                                                                    size={28}
                                                                />
                                                                <button
                                                                    type="button"
                                                                    onClick={() => void toggleStar(getMarketType(activeTab), sym)}
                                                                    title={isStarred ? 'Yıldızdan çıkar' : 'Yıldızla'}
                                                                    disabled={starBusy}
                                                                    style={{
                                                                        border: 'none',
                                                                        background: 'transparent',
                                                                        color: isStarred ? '#facc15' : tokens.textMuted,
                                                                        cursor: starBusy ? 'not-allowed' : 'pointer',
                                                                        fontSize: 17,
                                                                        lineHeight: 1,
                                                                        padding: 0,
                                                                        marginRight: 2,
                                                                    }}
                                                                >
                                                                    {isStarred ? '★' : '☆'}
                                                                </button>
                                                                <div>
                                                                    <div
                                                                        style={{
                                                                            fontWeight: 600,
                                                                            fontSize: '0.9rem',
                                                                        }}
                                                                    >
                                                                        {formatAssetLabel(sym, mk)}
                                                                    </div>
                                                                    <div
                                                                        style={{
                                                                            fontSize: '0.72rem',
                                                                            color: tokens.textMuted,
                                                                            marginTop: 2,
                                                                        }}
                                                                    >
                                                                        {row.symbol ?? sym}
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        </td>
                                                        <td
                                                            style={{
                                                                padding: '6px 12px',
                                                                textAlign: 'right',
                                                                verticalAlign: 'middle',
                                                            }}
                                                        >
                                                            <div
                                                                style={{
                                                                    display: 'flex',
                                                                    flexDirection: 'column',
                                                                    alignItems: 'flex-end',
                                                                    gap: 4,
                                                                }}
                                                            >
                                                                {spark && spark.length >= 2 ? (
                                                                    <>
                                                                        <MarketSparkline
                                                                            closes={spark}
                                                                            bgColor={tokens.bgCard}
                                                                            lineColor="#38bdf8"
                                                                        />
                                                                        <span
                                                                            style={{
                                                                                fontSize: 11,
                                                                                fontWeight: 700,
                                                                                color:
                                                                                    trendPct == null
                                                                                        ? tokens.textMuted
                                                                                        : trendPct >= 0
                                                                                          ? '#22c55e'
                                                                                          : '#f87171',
                                                                            }}
                                                                        >
                                                                            {trendPct == null
                                                                                ? '—'
                                                                                : `${trendPct >= 0 ? '+' : ''}${trendPct.toFixed(2)}%`}
                                                                        </span>
                                                                    </>
                                                                ) : (
                                                                    <span
                                                                        style={{
                                                                            fontSize: 11,
                                                                            color: tokens.textMuted,
                                                                        }}
                                                                    >
                                                                        —
                                                                    </span>
                                                                )}
                                                            </div>
                                                        </td>
                                                        <td
                                                            style={{
                                                                padding: 12,
                                                                textAlign: 'right',
                                                            }}
                                                        >
                                                            {noData
                                                                ? '-'
                                                                : buy != null
                                                                  ? buy.toLocaleString('tr-TR')
                                                                  : '-'}
                                                        </td>
                                                        <td
                                                            style={{
                                                                padding: 12,
                                                                textAlign: 'right',
                                                            }}
                                                        >
                                                            {noData
                                                                ? '-'
                                                                : sell != null
                                                                  ? sell.toLocaleString('tr-TR')
                                                                  : buy != null
                                                                    ? buy.toLocaleString('tr-TR')
                                                                    : '-'}
                                                        </td>
                                                        <td
                                                            style={{
                                                                padding: 12,
                                                                color: tokens.textMuted,
                                                                fontSize: '0.8125rem',
                                                            }}
                                                        >
                                                            {noData
                                                                ? row.message ?? '-'
                                                                : (row.source ?? '') +
                                                                  (row.timestamp
                                                                      ? ' · ' +
                                                                        new Date(
                                                                            row.timestamp,
                                                                        ).toLocaleString('tr-TR')
                                                                      : '')}
                                                        </td>
                                                    </tr>
                                                );
                                            })}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </div>

                        <div style={cardStyle}>
                            <div
                                style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                    marginBottom: 12,
                                }}
                            >
                                <h2 style={{ fontSize: '1rem', fontWeight: 600 }}>
                                    {activeTab === 'doviz' && 'Döviz grafiği'}
                                    {activeTab === 'crypto' && 'Kripto grafiği'}
                                    {activeTab === 'metals' && 'Altın grafiği'}
                                    {activeTab === 'funds' && 'Fon grafiği'}
                                    {activeTab === 'equity' && 'Hisse grafiği'}
                                </h2>
                                <button
                                    type="button"
                                    onClick={handleGoToAdvanced}
                                    style={{
                                        padding: '6px 12px',
                                        fontSize: '0.8rem',
                                        borderRadius: 999,
                                        border: `1px solid ${tokens.border}`,
                                        background: tokens.bgCard,
                                        color: tokens.accent,
                                        cursor: 'pointer',
                                    }}
                                >
                                    Detaylı mum grafik →
                                </button>
                            </div>
                            <div
                                style={{
                                    marginBottom: 12,
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 16,
                                    flexWrap: 'wrap',
                                }}
                            >
                                <label style={{ fontSize: '0.875rem' }}>
                                    Sembol:
                                    <select
                                        value={chartSymbol}
                                        onChange={(e) => setChartSymbol(e.target.value)}
                                        style={{
                                            marginLeft: 8,
                                            padding: '6px 10px',
                                            borderRadius: 8,
                                            border: `1px solid ${tokens.border}`,
                                            background:
                                                (tokens as { inputBg?: string }).inputBg ??
                                                tokens.bgCard,
                                            color: tokens.text,
                                            fontSize: '0.875rem',
                                        }}
                                    >
                                        {symbolsForTab.map((s) => (
                                            <option key={s} value={s}>
                                                {s}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                                <label style={{ fontSize: '0.875rem' }}>
                                    Dönem:
                                    <select
                                        value={chartDays}
                                        onChange={(e) =>
                                            setChartDays(Number(e.target.value))
                                        }
                                        style={{
                                            marginLeft: 8,
                                            padding: '6px 10px',
                                            borderRadius: 8,
                                            border: `1px solid ${tokens.border}`,
                                            background:
                                                (tokens as { inputBg?: string }).inputBg ??
                                                tokens.bgCard,
                                            color: tokens.text,
                                            fontSize: '0.875rem',
                                        }}
                                    >
                                        {DAYS_OPTIONS.map((d) => (
                                            <option key={d} value={d}>
                                                Son {d} gün
                                            </option>
                                        ))}
                                    </select>
                                </label>
                                <div
                                    style={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: 8,
                                        flexWrap: 'wrap',
                                        fontSize: '0.8125rem',
                                    }}
                                >
                                    <span>MA:</span>
                                    {['7', '30', '90'].map((ma) => (
                                        <label
                                            key={ma}
                                            style={{
                                                display: 'flex',
                                                alignItems: 'center',
                                                gap: 4,
                                            }}
                                        >
                                            <input
                                                type="checkbox"
                                                checked={selectedMa.includes(ma)}
                                                onChange={(e) => {
                                                    if (e.target.checked) {
                                                        setSelectedMa((prev) =>
                                                            prev.includes(ma)
                                                                ? prev
                                                                : [...prev, ma],
                                                        );
                                                    } else {
                                                        setSelectedMa((prev) =>
                                                            prev.filter((m) => m !== ma),
                                                        );
                                                    }
                                                }}
                                            />
                                            {ma}
                                        </label>
                                    ))}
                                </div>
                            </div>
                            <MarketIndicatorAreaChart
                                close={chartClose}
                                chartMa={chartMa}
                                selectedMa={selectedMa}
                                loading={loadingChart}
                                tokens={{
                                    bgCard: tokens.bgCard,
                                    border: tokens.border,
                                    text: tokens.text,
                                    textMuted: tokens.textMuted,
                                }}
                                height={300}
                            />
                        </div>

                        <div style={cardStyle}>
                            <h2
                                style={{
                                    fontSize: '1rem',
                                    fontWeight: 600,
                                    marginBottom: 8,
                                }}
                            >
                                Karşılaştırma (baz 100)
                            </h2>
                            <p style={{ ...mutedStyle, marginBottom: 12 }}>
                                Aynı kategoriden 2–4 sembol seçtiğinizde baz-100 karşılaştırma grafiği
                                çizilir (tek sembol veya seçim yoksa alan boş kalır). Yüksek volatilite
                                daha kalın çizgi; çizgi üzerine gelince vurgulanır.
                            </p>
                            <div
                                style={{
                                    marginBottom: 12,
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 16,
                                    flexWrap: 'wrap',
                                }}
                            >
                                <label style={{ fontSize: '0.875rem' }}>
                                    Kategori:
                                    <select
                                        value={compareCategory}
                                        onChange={(e) => {
                                            setCompareCategory(e.target.value as TabId);
                                            setCompareSymbols([]);
                                        }}
                                        style={{
                                            marginLeft: 8,
                                            padding: '6px 10px',
                                            borderRadius: 8,
                                            border: `1px solid ${tokens.border}`,
                                            background:
                                                (tokens as { inputBg?: string }).inputBg ??
                                                tokens.bgCard,
                                            color: tokens.text,
                                        }}
                                    >
                                        {tabs.map((t) => (
                                            <option key={t.id} value={t.id}>
                                                {t.label}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                                <label style={{ fontSize: '0.875rem' }}>
                                    Dönem:
                                    <select
                                        value={compareDays}
                                        onChange={(e) =>
                                            setCompareDays(Number(e.target.value))
                                        }
                                        style={{
                                            marginLeft: 8,
                                            padding: '6px 10px',
                                            borderRadius: 8,
                                            border: `1px solid ${tokens.border}`,
                                            background:
                                                (tokens as { inputBg?: string }).inputBg ??
                                                tokens.bgCard,
                                            color: tokens.text,
                                        }}
                                    >
                                        {DAYS_OPTIONS.map((d) => (
                                            <option key={d} value={d}>
                                                Son {d} gün
                                            </option>
                                        ))}
                                    </select>
                                </label>
                                <div
                                    style={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: 8,
                                        flexWrap: 'wrap',
                                    }}
                                >
                                    <span style={{ fontSize: '0.875rem' }}>Semboller:</span>
                                    {symbolsForCompare.slice(0, 12).map((sym) => (
                                        <label
                                            key={sym}
                                            style={{
                                                display: 'flex',
                                                alignItems: 'center',
                                                gap: 4,
                                                fontSize: '0.8125rem',
                                            }}
                                        >
                                            <input
                                                type="checkbox"
                                                checked={compareSymbols.includes(sym)}
                                                onChange={(e) => {
                                                    if (e.target.checked) {
                                                        setCompareSymbols((prev) =>
                                                            prev.length >= 4 ? prev : [...prev, sym],
                                                        );
                                                    } else {
                                                        setCompareSymbols((prev) =>
                                                            prev.filter((s) => s !== sym),
                                                        );
                                                    }
                                                }}
                                            />
                                            {sym}
                                        </label>
                                    ))}
                                </div>
                            </div>
                            {loadingCompare ? (
                                <p style={mutedStyle}>Karşılaştırma yükleniyor...</p>
                            ) : compareRows.length === 0 ? (
                                <p style={mutedStyle}>
                                    Karşılaştırma grafiği için aynı kategoriden en az iki sembol
                                    işaretleyin.
                                </p>
                            ) : (
                                <MarketCompareLwChart
                                    rows={compareRows}
                                    symbols={compareSymbols.slice(0, 4)}
                                    colors={COMPARE_COLORS}
                                    lineWidthBySymbol={lineWidthBySymbol}
                                    tokens={{
                                        bgCard: tokens.bgCard,
                                        border: tokens.border,
                                        text: tokens.text,
                                        textMuted: tokens.textMuted,
                                    }}
                                    height={320}
                                />
                            )}
                        </div>
                    </div>

                    <aside style={{ ...cardStyle, marginBottom: 0, position: 'sticky', top: 16 }}>
                        <h2
                            style={{
                                fontSize: '1rem',
                                fontWeight: 600,
                                marginBottom: 8,
                            }}
                        >
                            Piyasa ısı haritası
                        </h2>
                        <p style={{ ...mutedStyle, marginBottom: 12 }}>
                            Equity: {dashboard?.heatmapMeta?.equityMode ?? 'EQUITY_FINVIZ'} (
                            {dashboard?.heatmapMeta?.equityChangeHorizon ?? '1D'} /{' '}
                            {dashboard?.heatmapMeta?.equityWeightMode ?? 'EQUAL'}). Diğer varlıklar:{' '}
                            {dashboard?.heatmapMeta?.multiAssetMode ?? 'MULTI_ASSET'} (
                            {dashboard?.heatmapMeta?.multiAssetChangeHorizon ?? '14D'} /{' '}
                            {dashboard?.heatmapMeta?.multiAssetWeightMode ?? 'PRICE_SQRT'}). Geçmiş yoksa gri nötr.
                        </p>
                        <button
                            type="button"
                            onClick={() => navigate('/market/heatmap')}
                            style={{
                                marginBottom: 12,
                                padding: '6px 12px',
                                fontSize: '0.8rem',
                                borderRadius: 999,
                                border: `1px solid ${tokens.border}`,
                                background: tokens.bgCard,
                                color: tokens.accent,
                                cursor: 'pointer',
                            }}
                        >
                            Detaylı ısı haritası →
                        </button>
                        <MarketFinvizTreemap
                            tiles={dashboard?.heatmapTiles ?? []}
                            borderColor={tokens.border}
                            panelBg={tokens.bg}
                        />
                        {dashboard?.computedAt ? (
                            <p
                                style={{
                                    ...mutedStyle,
                                    marginTop: 12,
                                    fontSize: 11,
                                }}
                            >
                                Hesap:{' '}
                                {new Date(dashboard.computedAt).toLocaleString('tr-TR')}
                            </p>
                        ) : null}
                    </aside>
                </div>
            )}
        </div>
    );
}
