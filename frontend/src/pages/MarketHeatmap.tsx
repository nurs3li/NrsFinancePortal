import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
    type MetalsSubmarket,
    symbolMatchesMetalsSubmarket,
} from '../constants/preciousMetalsUsd';
import { useQuery } from '@tanstack/react-query';
import { financeClient } from '../api/client';
import { getBistBatchHistory, getBistLatest, bistPickClose } from '../services/bistEquityApi';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import type { MarketDashboard } from '../components/market/marketTypes';
import { MarketFinvizTreemap, type TreemapTile } from '../components/market/MarketFinvizTreemap';
import { approxHeatmapPctFromSpark } from '../components/market/heatmapApproxPct';
import { CHART_RANGE_SEQUENCE, type ChartRangeId, RANGE_TO_DAYS } from '../components/market/heatmapRange';
import { heatmapRangeLabel, heatmapSectorLabel } from '../components/market/heatmapSectorLabels';
import { fetchMarketTerminalList } from '../services/marketTerminalListApi';
import { buildTefasTreemapTiles, HEATMAP_SECTOR_TEFAS_FUNDS, tefasHeatmapSortForRange } from '../utils/tefasHeatmap';
import './MarketHeatmap.css';

function fmtPct(v: number): string {
    return `${v >= 0 ? '+' : ''}${v.toFixed(2)}%`;
}

function unwrapData<T>(payload: unknown): T {
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

function istCalendarYmd(d: Date): string {
    const parts = new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Europe/Istanbul',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
    }).formatToParts(d);
    const g = (t: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === t)?.value ?? '00';
    return `${g('year')}-${g('month')}-${g('day')}`;
}

function bistCalendarRange(daysBack: number): { from: string; to: string } {
    const to = new Date();
    const from = new Date(to.getTime() - Math.max(1, daysBack) * 86_400_000);
    return { from: istCalendarYmd(from), to: istCalendarYmd(to) };
}

export function MarketHeatmap() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const [hovered, setHovered] = useState<TreemapTile | null>(null);
    const [chartRange, setChartRange] = useState<ChartRangeId>('1M');
    const [sectorFilter, setSectorFilter] = useState<string>('ALL');
    const [metalsSubmarket, setMetalsSubmarket] = useState<MetalsSubmarket>('GRAM');

    useEffect(() => {
        if (searchParams.get('fundSubmarket') === 'TR') {
            const sector = searchParams.get('sector');
            navigate(sector?.trim() ? `/market/heatmap?sector=${encodeURIComponent(sector.trim())}` : '/market/heatmap', {
                replace: true,
            });
            return;
        }
        const s = searchParams.get('sector')?.trim();
        if (s && s.length > 0) {
            const normalized =
                s === HEATMAP_SECTOR_TEFAS_FUNDS || /şemsiye/i.test(s) ? HEATMAP_SECTOR_TEFAS_FUNDS : s;
            setSectorFilter(normalized);
        }
    }, [searchParams, navigate]);

    const { data, isLoading, error } = useQuery({
        queryKey: ['market', 'dashboard'],
        queryFn: () => financeClient.get<MarketDashboard>('/api/market/dashboard').then((r) => unwrapData<MarketDashboard>(r.data)),
        refetchInterval: 60_000,
    });

    const { data: tefasHeatmapPage, isLoading: loadingTefasHeatmap } = useQuery({
        queryKey: ['market', 'tefas', 'heatmap-detail', chartRange],
        queryFn: ({ signal }) =>
            fetchMarketTerminalList(
                {
                    category: 'FUNDS',
                    fundSubmarket: 'TR',
                    page: 0,
                    size: 100,
                    filter: 'ALL',
                    sort: tefasHeatmapSortForRange(chartRange),
                    dir: 'desc',
                },
                signal,
            ),
        staleTime: 120_000,
    });

    const hasDashBist = useMemo(
        () => (data?.heatmapTiles ?? []).some((t) => t.assetClass === 'BIST'),
        [data?.heatmapTiles],
    );

    const { data: bistLatest = [] } = useQuery({
        queryKey: ['market', 'bist', 'latest', 'heatmap-detail'],
        queryFn: ({ signal }) => getBistLatest(signal),
        enabled: Boolean(data) && !hasDashBist,
        staleTime: 60_000,
        refetchInterval: 60_000,
    });

    const bistSparkSymbolsCsv = useMemo(() => {
        if (!bistLatest.length) return '';
        return bistLatest
            .slice(0, 22)
            .map((r) => normalizeSymbolKey(String(r.symbol ?? '')))
            .filter(Boolean)
            .join(',');
    }, [bistLatest]);

    const bistSparkRange = useMemo(() => bistCalendarRange(400), []);

    const { data: bistBatchSpark } = useQuery({
        queryKey: ['market', 'bist', 'batch-spark', 'heatmap-detail', bistSparkRange.from, bistSparkRange.to, bistSparkSymbolsCsv],
        queryFn: ({ signal }) => {
            const syms = bistSparkSymbolsCsv.split(',').map((s) => s.trim()).filter(Boolean);
            return getBistBatchHistory(syms, bistSparkRange.from, bistSparkRange.to, signal);
        },
        enabled: Boolean(data) && !hasDashBist && bistSparkSymbolsCsv.length > 0,
        staleTime: 120_000,
    });

    const bistSparkClosesBySymbol = useMemo(() => {
        const m: Record<string, number[]> = {};
        const by = bistBatchSpark?.historiesBySymbol;
        if (!by) return m;
        Object.entries(by).forEach(([sym, rows]) => {
            const key = normalizeSymbolKey(sym);
            const closes = [...(rows ?? [])]
                .sort((a, b) => String(a.date ?? '').localeCompare(String(b.date ?? '')))
                .map((r) => bistPickClose(r))
                .filter((x) => Number.isFinite(x) && x > 0);
            if (closes.length >= 2) m[key] = closes;
        });
        return m;
    }, [bistBatchSpark]);

    const syntheticBistTiles = useMemo((): TreemapTile[] => {
        if (hasDashBist) return [];
        const out: TreemapTile[] = [];
        for (const r of bistLatest) {
            const sym = normalizeSymbolKey(String(r.symbol ?? ''));
            if (!sym) continue;
            const pctRaw = r.changePercent;
            const pct =
                typeof pctRaw === 'number' && Number.isFinite(pctRaw)
                    ? pctRaw
                    : parseFloat(String(pctRaw ?? '0').replace(',', '.')) || 0;
            const mcRaw = r.marketCapTry;
            const mc =
                typeof mcRaw === 'number' && Number.isFinite(mcRaw)
                    ? mcRaw
                    : parseFloat(String(mcRaw ?? '0').replace(',', '.')) || 0;
            const w = mc > 0 ? mc : Math.abs(pct) + 1;
            const display =
                typeof r.displayName === 'string' ? String(r.displayName).trim() : '';
            out.push({
                sector: 'BIST_EQUITY',
                industry: display.length > 0 ? display : null,
                symbol: sym,
                assetClass: 'BIST',
                changePercent: pct,
                layoutWeight: Math.max(1, w),
                mode: 'BIST_DAILY',
            });
        }
        return out;
    }, [hasDashBist, bistLatest]);

    const tefasHeatmapTiles = useMemo(
        () => buildTefasTreemapTiles(tefasHeatmapPage?.items ?? [], chartRange),
        [tefasHeatmapPage?.items, chartRange],
    );

    const heatTilesSource = useMemo(() => {
        const base = [...(data?.heatmapTiles ?? [])];
        const withBist = hasDashBist ? base : [...base, ...syntheticBistTiles];
        return [...withBist, ...tefasHeatmapTiles];
    }, [data?.heatmapTiles, hasDashBist, syntheticBistTiles, tefasHeatmapTiles]);

    const sparklineMap = useMemo(() => {
        const m = new Map<string, number[]>();
        for (const s of data?.sparklines ?? []) {
            m.set(`${s.assetClass}|${s.symbol}`, s.closes ?? []);
        }
        if (!hasDashBist) {
            for (const [sym, closes] of Object.entries(bistSparkClosesBySymbol)) {
                m.set(`BIST|${sym}`, closes);
            }
        }
        return m;
    }, [data?.sparklines, hasDashBist, bistSparkClosesBySymbol]);

    const displayTiles = useMemo(() => {
        const withRange = heatTilesSource.map((tile) => {
            if (tile.assetClass === 'FUND') {
                return {
                    ...tile,
                    changeHorizon: chartRange,
                };
            }
            const closes = sparklineMap.get(`${tile.assetClass}|${tile.symbol}`) ?? [];
            const pct = approxHeatmapPctFromSpark(closes, tile.assetClass, tile.symbol, chartRange, tile.changePercent);
            return {
                ...tile,
                changePercent: pct,
                changeHorizon: chartRange,
            };
        });
        const metalsFiltered = withRange.filter((t) => {
            if (t.assetClass !== 'METAL') return true;
            return symbolMatchesMetalsSubmarket(t.symbol, metalsSubmarket);
        });
        if (sectorFilter === 'ALL') return metalsFiltered;
        if (sectorFilter === 'PRECIOUS_METALS') {
            return metalsFiltered.filter(
                (t) =>
                    t.sector === 'PRECIOUS_METALS' ||
                    t.sector === 'PRECIOUS_METALS_GRAM' ||
                    t.sector === 'PRECIOUS_METALS_OUNCE',
            );
        }
        return metalsFiltered.filter((t) => t.sector === sectorFilter);
    }, [heatTilesSource, sectorFilter, sparklineMap, chartRange, metalsSubmarket]);

    const sectorOptions = useMemo(() => {
        const all = new Set<string>();
        for (const t of heatTilesSource) all.add(t.sector);
        if (sectorFilter !== 'ALL') all.add(sectorFilter);
        const rest = Array.from(all).sort((a, b) => a.localeCompare(b, 'tr'));
        const preferred = [
            'BIST_EQUITY',
            HEATMAP_SECTOR_TEFAS_FUNDS,
            'PRECIOUS_METALS_GRAM',
            'PRECIOUS_METALS_OUNCE',
        ];
        const head = preferred.filter((k) => all.has(k));
        const tail = rest.filter((k) => !preferred.includes(k));
        return ['ALL', ...head, ...tail];
    }, [heatTilesSource, sectorFilter]);

    const sortedByAbsMove = useMemo(() => {
        const list = [...displayTiles];
        list.sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent));
        return list.slice(0, 8);
    }, [displayTiles]);

    const rangeDays = RANGE_TO_DAYS[chartRange];

    const toAdvancedType = (assetClass: string): 'FX' | 'CRYPTO' | 'METALS' | 'FUNDS' | 'EQUITY' => {
        switch (assetClass) {
            case 'FX':
                return 'FX';
            case 'CRYPTO':
                return 'CRYPTO';
            case 'METAL':
                return 'METALS';
            case 'FUND':
                return 'FUNDS';
            case 'STOCK':
            case 'BIST':
                return 'EQUITY';
            default:
                return 'EQUITY';
        }
    };

    const cardStyle: React.CSSProperties = {
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        color: tokens.text,
    };

    const errMsg =
        error instanceof Error
            ? error.message
            : error != null
              ? String((error as { message?: string }).message ?? error)
              : undefined;

    const rangeUi = heatmapRangeLabel(chartRange, t);
    const sectorNameForUi = (k: string) => heatmapSectorLabel(k, t);

    return (
        <main
            className="heatmap-detail-page"
            style={{ background: tokens.bg, color: tokens.text }}
        >
            <div className="heatmap-detail-page__header">
                <div style={{ minWidth: 0, flex: '1 1 12rem' }}>
                    <h1 className="heatmap-detail-page__title">{t('market.detailedHeatmap', 'Detaylı Isı Haritası')}</h1>
                    <p className="heatmap-detail-page__subtitle" style={{ color: tokens.textMuted }}>
                        {t('heatmap.subtitle', 'Sektör / sembol dağılımı ve hover ile ayrıntılar.')}
                    </p>
                </div>
                <button
                    type="button"
                    className="heatmap-detail-page__back"
                    onClick={() => navigate('/market')}
                    style={{
                        border: `1px solid ${tokens.border}`,
                        background: tokens.bgCard,
                        color: tokens.text,
                    }}
                >
                    {t('heatmap.backToMarket', '← Piyasa sayfasına dön')}
                </button>
            </div>

            {isLoading || loadingTefasHeatmap ? (
                <p style={{ color: tokens.textMuted }}>{t('common.loading', 'Yükleniyor...')}</p>
            ) : errMsg ? (
                <p style={{ color: tokens.error }}>{t('news.errorPrefix', 'Hata')}: {errMsg}</p>
            ) : (
                <div className="heatmap-detail-grid">
                    <div className="heatmap-detail-card" style={cardStyle}>
                        <div className="heatmap-detail-card__toolbar">
                            <label style={{ color: tokens.textMuted }}>
                                {t('heatmap.timeframe', 'Zaman dilimi')}:
                                <select
                                    value={chartRange}
                                    onChange={(e) => setChartRange(e.target.value as ChartRangeId)}
                                    style={{
                                        marginLeft: 8,
                                        padding: '5px 10px',
                                        borderRadius: 8,
                                        border: `1px solid ${tokens.border}`,
                                        background: tokens.bgCard,
                                        color: tokens.text,
                                    }}
                                >
                                    {CHART_RANGE_SEQUENCE.map((r) => (
                                        <option key={r} value={r}>
                                            {heatmapRangeLabel(r, t)}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <label style={{ fontSize: 13, color: tokens.textMuted }}>
                                {t('market.sector', 'Sektör')}:
                                <select
                                    value={sectorFilter}
                                    onChange={(e) => setSectorFilter(e.target.value)}
                                    style={{
                                        marginLeft: 8,
                                        padding: '5px 10px',
                                        borderRadius: 8,
                                        border: `1px solid ${tokens.border}`,
                                        background: tokens.bgCard,
                                        color: tokens.text,
                                        minWidth: 160,
                                    }}
                                >
                                    {sectorOptions.map((s) => (
                                        <option key={s} value={s}>
                                            {sectorNameForUi(s)}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <div
                                className="terminal-equity-submarket heatmap-detail-metals-submarket"
                                role="group"
                                aria-label={t('metals.submarketGroup', 'Kıymetli maden alt pazarı')}
                            >
                                <button
                                    type="button"
                                    className={`terminal-submarket-chip ${metalsSubmarket === 'GRAM' ? 'is-active' : ''}`}
                                    onClick={() => {
                                        setMetalsSubmarket('GRAM');
                                        if (
                                            sectorFilter === 'PRECIOUS_METALS_OUNCE' ||
                                            sectorFilter === 'PRECIOUS_METALS'
                                        ) {
                                            setSectorFilter('PRECIOUS_METALS_GRAM');
                                        }
                                    }}
                                >
                                    {t('metals.gramGold', 'Gram altın')}
                                </button>
                                <button
                                    type="button"
                                    className={`terminal-submarket-chip ${metalsSubmarket === 'OUNCE' ? 'is-active' : ''}`}
                                    onClick={() => {
                                        setMetalsSubmarket('OUNCE');
                                        if (
                                            sectorFilter === 'PRECIOUS_METALS_GRAM' ||
                                            sectorFilter === 'PRECIOUS_METALS'
                                        ) {
                                            setSectorFilter('PRECIOUS_METALS_OUNCE');
                                        }
                                    }}
                                >
                                    {t('metals.usdOunce', 'Ons')}
                                </button>
                            </div>
                            <span className="heatmap-detail-card__hint" style={{ color: tokens.textMuted }}>
                                {t('heatmap.clickHint', 'Kutuya tıklayınca seçili dönem ile gelişmiş grafikte açılır.')}
                            </span>
                        </div>
                        <p className="heatmap-detail-card__metals-note" style={{ color: tokens.textMuted, margin: '0 0 8px', fontSize: 12 }}>
                            {t(
                                'heatmap.metalsTryNote',
                                'Ons fiyatları ısı haritasında her tarih için USD/ons × o günün USD/TRY kuru ile TL bazında gösterilir.',
                            )}
                        </p>
                        <div className="heatmap-detail-card__meta" style={{ color: tokens.textMuted }}>
                            Equity: {data?.heatmapMeta?.equityMode ?? 'EQUITY_FINVIZ'} (
                            {data?.heatmapMeta?.equityChangeHorizon ?? '1D'} /{' '}
                            {data?.heatmapMeta?.equityWeightMode ?? 'EQUAL'})
                            {' · '}
                            {t('heatmap.otherAssets', 'Diğer varlıklar')}:{' '}
                            {data?.heatmapMeta?.multiAssetMode ?? 'MULTI_ASSET'} ({t('heatmap.uiHorizon', 'ekran')}: {rangeUi} /{' '}
                            {data?.heatmapMeta?.multiAssetWeightMode ?? 'PRICE_SQRT'})
                            {tefasHeatmapTiles.length > 0
                                ? ` · TEFAS: ${tefasHeatmapTiles.length} ${t('funds.turkishFunds', 'Türk fonları')} (${rangeUi})`
                                : ''}
                        </div>
                        <div className="heatmap-detail-treemap">
                            <MarketFinvizTreemap
                                tiles={displayTiles}
                                borderColor={tokens.border}
                                panelBg={tokens.bg}
                                sectorDisplayName={sectorNameForUi}
                                onTileHover={setHovered}
                                onTileLeave={() => setHovered(null)}
                                onTileClick={(tile) => {
                                    const type = toAdvancedType(tile.assetClass);
                                    const sub =
                                        type === 'METALS'
                                            ? symbolMatchesMetalsSubmarket(tile.symbol, 'GRAM')
                                                ? 'GRAM'
                                                : 'OUNCE'
                                            : '';
                                    const q = new URLSearchParams({
                                        type,
                                        symbol: tile.symbol,
                                        days: String(rangeDays),
                                    });
                                    if (sub) q.set('metalsSubmarket', sub);
                                    navigate(`/market?${q.toString()}`);
                                }}
                            />
                        </div>
                    </div>

                    <div className="heatmap-detail-card heatmap-detail-card--sticky" style={cardStyle}>
                        <h3 style={{ marginTop: 0, marginBottom: 10, fontSize: '1rem' }}>{t('heatmap.hoverDetail', 'Hover detayı')}</h3>
                        {hovered ? (
                            <div style={{ display: 'grid', gap: 6, fontSize: '0.9rem' }}>
                                <div><strong>{hovered.symbol}</strong> ({hovered.assetClass})</div>
                                <div>
                                    {t('market.sector', 'Sektör')}: {sectorNameForUi(hovered.sector)}
                                </div>
                                {hovered.industry ? <div>Industry: {hovered.industry}</div> : null}
                                <div>
                                    {t('market.change', 'Değişim')} ({rangeUi}): {fmtPct(hovered.changePercent)}
                                </div>
                                {hovered.changeHorizon ? <div>Horizon: {hovered.changeHorizon}</div> : null}
                                {hovered.weightMode ? <div>Weight: {hovered.weightMode}</div> : null}
                                {hovered.marketCapSource ? <div>Cap Source: {hovered.marketCapSource}</div> : null}
                                {hovered.marketCapAsOf ? (
                                    <div>Cap AsOf: {new Date(hovered.marketCapAsOf).toLocaleString(lang === 'en' ? 'en-US' : 'tr-TR')}</div>
                                ) : null}
                                {hovered.mode ? <div>Mode: {hovered.mode}</div> : null}
                            </div>
                        ) : (
                            <p style={{ margin: 0, color: tokens.textMuted, fontSize: 13 }}>
                                {t('heatmap.hoverHint', 'Detayları görmek için bir kutunun üstüne gel veya tıkla.')}
                            </p>
                        )}

                        <hr style={{ borderColor: tokens.border, opacity: 0.5, margin: '14px 0' }} />
                        <h4 style={{ margin: 0, marginBottom: 8, fontSize: '0.95rem' }}>
                            {t('heatmap.topMoversTitle', 'En hareketli 8 sembol')} ({rangeUi})
                        </h4>
                        <div style={{ display: 'grid', gap: 8 }}>
                            {sortedByAbsMove.map((t) => (
                                <div
                                    key={`${t.assetClass}-${t.symbol}`}
                                    style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        fontSize: 13,
                                        color: tokens.textMuted,
                                    }}
                                >
                                    <span>{t.symbol}</span>
                                    <span style={{ color: t.changePercent >= 0 ? '#22c55e' : '#f87171', fontWeight: 700 }}>
                                        {fmtPct(t.changePercent)}
                                    </span>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            )}
        </main>
    );
}
