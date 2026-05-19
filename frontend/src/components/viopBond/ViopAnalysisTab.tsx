import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
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
import { useLanguage } from '../../i18n/LanguageContext';
import { viopCategoryFor } from '../../constants/ViopWhitelist';
import { MarketSparkline } from '../market/MarketSparkline';
import { PriceAlertModal } from '../priceAlert/PriceAlertModal';
import { viopPositionKeys } from '../../queries/viopPositionKeys';
import { bondPositionKeys } from '../../queries/bondPositionKeys';
import {
    closeViopPosition,
    createViopPosition,
    deleteViopPosition,
    getViopSummary,
    listViopPositions,
    updateViopPosition,
} from '../../services/viopPositionApi';
import { readFinanceApiError } from '../../services/manualPortfolioApi';
import type { ManualViopPosition, ManualViopPositionCreatePayload } from '../../types/viopPosition';
import type { TerminalListInstrumentVm } from '../../utils/marketTerminalListVm';
import {
    fetchAllTerminalInstruments,
    filterViopByCategory,
    viopCategoryLabel,
    viopFilterLabel,
    type ViopMarketFilter,
} from './viopBondMarket';
import { fmtMoney, fmtPct } from './formatViopBond';
import { ViopPositionAddModal } from './ViopPositionAddModal';
import { ViopPositionDetailDrawer } from './ViopPositionDetailDrawer';
import { MarketInstrumentDetailPanel } from './MarketInstrumentDetailPanel';
import { AnalysisMiniCard, RankList, pctClass, pnlClass } from './vbTabShared';

const CHART_COLORS = ['#3b82f6', '#f59e0b', '#22c55e', '#ef4444', '#a855f7', '#06b6d4'];
const VIOP_FILTERS: ViopMarketFilter[] = ['ALL', 'FX', 'INDEX', 'COMMODITY', 'EQUITY'];
type ChartMode = 'pnl' | 'risk' | 'margin';

type Props = {
    tokens: { border: string; bgCard: string; textMuted: string };
};

export function ViopAnalysisTab({ tokens }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const queryClient = useQueryClient();

    const [marketFilter, setMarketFilter] = useState<ViopMarketFilter>('ALL');
    const [marketSearch, setMarketSearch] = useState('');
    const [selectedMarketSymbol, setSelectedMarketSymbol] = useState<string | null>(null);
    const [positionSearch, setPositionSearch] = useState('');
    const [selectedPositionId, setSelectedPositionId] = useState<number | null>(null);
    const [chartMode, setChartMode] = useState<ChartMode>('pnl');
    const [addInstrument, setAddInstrument] = useState<TerminalListInstrumentVm | null>(null);
    const [editRow, setEditRow] = useState<ManualViopPosition | null>(null);
    const [modalOpen, setModalOpen] = useState(false);
    const [alertSymbol, setAlertSymbol] = useState<{ symbol: string; displayName?: string } | null>(null);
    const [detailPosition, setDetailPosition] = useState<ManualViopPosition | null>(null);

    const { data: rows = [], isLoading: positionsLoading } = useQuery({
        queryKey: viopPositionKeys.list(),
        queryFn: listViopPositions,
    });

    const { data: summary } = useQuery({
        queryKey: viopPositionKeys.summary(),
        queryFn: getViopSummary,
    });

    const { data: marketRows = [], isLoading: marketLoading } = useQuery({
        queryKey: ['viop-bond', 'market', 'FUTURES'],
        queryFn: () => fetchAllTerminalInstruments('FUTURES'),
        staleTime: 60_000,
    });

    const invalidate = () => {
        void queryClient.invalidateQueries({ queryKey: viopPositionKeys.all });
        void queryClient.invalidateQueries({ queryKey: bondPositionKeys.combined() });
    };

    const createMut = useMutation({ mutationFn: createViopPosition, onSuccess: invalidate });
    const updateMut = useMutation({
        mutationFn: ({ id, payload }: { id: number; payload: ManualViopPositionCreatePayload }) =>
            updateViopPosition(id, payload),
        onSuccess: invalidate,
    });
    const deleteMut = useMutation({ mutationFn: deleteViopPosition, onSuccess: invalidate });
    const closeMut = useMutation({
        mutationFn: ({ id, price, date }: { id: number; price: number; date: string }) =>
            closeViopPosition(id, { closePrice: price, closeDate: date }),
        onSuccess: invalidate,
    });

    const filteredMarket = useMemo(() => {
        let list = filterViopByCategory(marketRows, marketFilter);
        const q = marketSearch.trim().toLowerCase();
        if (q) {
            list = list.filter(
                (i) =>
                    i.symbol.toLowerCase().includes(q) ||
                    i.displayName.toLowerCase().includes(q),
            );
        }
        return list;
    }, [marketRows, marketFilter, marketSearch]);

    const selectedMarket =
        filteredMarket.find((i) => i.symbol === selectedMarketSymbol) ?? filteredMarket[0] ?? null;

    const openPositions = useMemo(() => rows.filter((r) => r.status === 'OPEN'), [rows]);

    const filteredPositions = useMemo(() => {
        const q = positionSearch.trim().toLowerCase();
        if (!q) return rows;
        return rows.filter(
            (r) =>
                r.symbol.toLowerCase().includes(q) ||
                (r.displayName ?? '').toLowerCase().includes(q),
        );
    }, [rows, positionSearch]);

    const barChartData = useMemo(() => {
        return openPositions.map((r) => {
            let value = 0;
            if (chartMode === 'pnl') value = Math.abs(Number(r.unrealizedPnl ?? 0));
            else if (chartMode === 'risk') value = Math.abs(Number(r.riskExposure ?? 0));
            else value = Math.abs(Number(r.initialMargin ?? 0));
            return { name: r.symbol, value, signed: Number(r.unrealizedPnl ?? 0) };
        });
    }, [openPositions, chartMode]);

    const riskSummary = useMemo(() => {
        if (!openPositions.length) return null;
        const byRisk = [...openPositions].sort(
            (a, b) => Number(b.riskExposure ?? 0) - Number(a.riskExposure ?? 0),
        );
        const long = openPositions.filter((p) => p.direction === 'LONG').length;
        const short = openPositions.length - long;
        const total = long + short;
        const nearest = [...openPositions]
            .filter((p) => p.daysToExpiry != null)
            .sort((a, b) => (a.daysToExpiry ?? 9999) - (b.daysToExpiry ?? 9999))[0];
        return {
            top: byRisk[0],
            longPct: total > 0 ? Math.round((long / total) * 100) : 0,
            shortPct: total > 0 ? Math.round((short / total) * 100) : 0,
            nearest,
        };
    }, [openPositions]);

    const pnlChart = useMemo(
        () =>
            openPositions
                .filter((r) => r.unrealizedPnl != null)
                .map((r) => ({ name: r.symbol, value: Math.abs(Number(r.unrealizedPnl)) })),
        [openPositions],
    );

    const dirChart = useMemo(() => {
        const long = openPositions.filter((r) => r.direction === 'LONG').length;
        const short = openPositions.filter((r) => r.direction === 'SHORT').length;
        return [
            { name: 'LONG', value: long },
            { name: 'SHORT', value: short },
        ].filter((x) => x.value > 0);
    }, [openPositions]);

    const topGainers = useMemo(
        () =>
            [...openPositions]
                .filter((r) => (r.unrealizedPnl ?? 0) > 0)
                .sort((a, b) => Number(b.unrealizedPnl) - Number(a.unrealizedPnl))
                .slice(0, 5),
        [openPositions],
    );

    const topLosers = useMemo(
        () =>
            [...openPositions]
                .filter((r) => (r.unrealizedPnl ?? 0) < 0)
                .sort((a, b) => Number(a.unrealizedPnl) - Number(b.unrealizedPnl))
                .slice(0, 5),
        [openPositions],
    );

    const expiring = useMemo(
        () =>
            [...openPositions]
                .filter((p) => p.daysToExpiry != null && p.daysToExpiry <= 14)
                .sort((a, b) => (a.daysToExpiry ?? 0) - (b.daysToExpiry ?? 0)),
        [openPositions],
    );

    const openAdd = (inst: TerminalListInstrumentVm) => {
        setAddInstrument(inst);
        setEditRow(null);
        setModalOpen(true);
    };

    const handleClose = (row: ManualViopPosition) => {
        const priceStr = window.prompt(
            t('viopBond.promptClosePrice', 'Kapanış fiyatı'),
            String(row.currentPrice ?? row.entryPrice),
        );
        if (!priceStr) return;
        const date = window.prompt(
            t('viopBond.promptCloseDate', 'Kapanış tarihi (YYYY-MM-DD)'),
            new Date().toISOString().slice(0, 10),
        );
        if (!date) return;
        closeMut.mutate({ id: row.id, price: Number(priceStr), date });
    };

    const tabKpis = [
        { label: t('viopBond.viopOpenCount', 'Açık Pozisyon'), value: String(summary?.openPositionCount ?? 0) },
        { label: t('viopBond.viopMargin', 'Toplam Teminat'), value: fmtMoney(summary?.totalInitialMargin, locale) },
        {
            label: t('viopBond.viopPnl', 'Açık K/Z'),
            value: fmtMoney(summary?.totalUnrealizedPnl, locale),
            pos: (summary?.totalUnrealizedPnl ?? 0) >= 0,
        },
        { label: t('viopBond.viopRisk', 'Risk Maruziyeti'), value: fmtMoney(summary?.totalRiskExposure, locale) },
        {
            label: t('viopBond.viopLongShort', 'Long / Short'),
            value: `${summary?.longCount ?? 0} / ${summary?.shortCount ?? 0}`,
        },
        { label: t('viopBond.viopExpiring', 'Vadesi Yakın'), value: String(summary?.expiringSoonCount ?? 0) },
    ];

    const cardStyle = { borderColor: tokens.border, background: tokens.bgCard };

    return (
        <div className="vb-tab-root">
            <div className="vb-tab-kpis">
                {tabKpis.map((k) => (
                    <div
                        key={k.label}
                        className={`vb-tab-kpi pf-stat-card${'pos' in k && k.pos === true ? ' pf-stat-card--pnl-pos' : 'pos' in k && k.pos === false ? ' pf-stat-card--pnl-neg' : ''}`}
                        style={cardStyle}
                    >
                        <div className="vb-tab-kpi-label" style={{ color: tokens.textMuted }}>
                            {k.label}
                        </div>
                        <div className="vb-tab-kpi-value">{k.value}</div>
                    </div>
                ))}
            </div>

            <div className="vb-main-grid">
                <div className="vb-chart-main pf-card-premium" style={cardStyle}>
                    <div className="vb-section-head">
                        <h3>{t('viopBond.viopMainChart', 'VİOP Analiz Grafiği')}</h3>
                        <div className="vb-chart-mode-tabs">
                            {(
                                [
                                    ['pnl', t('viopBond.chartModePnl', 'Açık K/Z')],
                                    ['risk', t('viopBond.chartModeRisk', 'Risk Maruziyeti')],
                                    ['margin', t('viopBond.chartModeMargin', 'Teminat')],
                                ] as const
                            ).map(([id, label]) => (
                                <button
                                    key={id}
                                    type="button"
                                    className={`pf-dash-btn pf-dash-btn--compact${chartMode === id ? ' pf-dash-btn--active' : ''}`}
                                    onClick={() => setChartMode(id)}
                                >
                                    {label}
                                </button>
                            ))}
                        </div>
                    </div>
                    {barChartData.length === 0 ? (
                        <div className="vb-empty-state">
                            <p>{t('viopBond.viopChartEmpty', 'Açık pozisyon ekledikçe grafik dolacaktır.')}</p>
                        </div>
                    ) : (
                        <ResponsiveContainer width="100%" height={260}>
                            <BarChart data={barChartData}>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
                                <XAxis dataKey="name" tick={{ fill: tokens.textMuted, fontSize: 11 }} />
                                <YAxis tick={{ fill: tokens.textMuted, fontSize: 11 }} />
                                <Tooltip />
                                <Bar dataKey="value" radius={[4, 4, 0, 0]}>
                                    {barChartData.map((entry, i) => (
                                        <Cell
                                            key={entry.name}
                                            fill={
                                                chartMode === 'pnl'
                                                    ? entry.signed >= 0
                                                        ? '#22c55e'
                                                        : '#ef4444'
                                                    : CHART_COLORS[i % CHART_COLORS.length]
                                            }
                                        />
                                    ))}
                                </Bar>
                            </BarChart>
                        </ResponsiveContainer>
                    )}
                </div>

                <aside className="vb-risk-card pf-card-premium" style={cardStyle}>
                    <h3>{t('viopBond.viopRiskSummary', 'VİOP Risk Özeti')}</h3>
                    {riskSummary ? (
                        <ul className="vb-risk-list">
                            <li>
                                <span>{t('viopBond.topExposure', 'En yüksek maruziyet')}</span>
                                <strong>{riskSummary.top?.symbol}</strong>
                            </li>
                            <li>
                                <span>{t('viopBond.longRatio', 'Long yoğunluğu')}</span>
                                <strong>%{riskSummary.longPct}</strong>
                            </li>
                            <li>
                                <span>{t('viopBond.shortRatio', 'Short yoğunluğu')}</span>
                                <strong>%{riskSummary.shortPct}</strong>
                            </li>
                            <li>
                                <span>{t('viopBond.nearestExpiry', 'En yakın vade')}</span>
                                <strong>
                                    {riskSummary.nearest
                                        ? `${riskSummary.nearest.symbol} (${riskSummary.nearest.daysToExpiry}g)`
                                        : '—'}
                                </strong>
                            </li>
                        </ul>
                    ) : (
                        <p style={{ color: tokens.textMuted, fontSize: '0.85rem' }}>
                            {t('viopBond.riskEmpty', 'Henüz açık pozisyon yok.')}
                        </p>
                    )}
                    <p className="vb-risk-note">{t('viopBond.viopRiskNote', 'VİOP pozisyonları kaldıraçlı ürünlerdir. Toplam finansal etki ile risk maruziyeti farklı kavramlardır.')}</p>
                </aside>
            </div>

            <section className="vb-section">
                <h3 className="vb-section-title">{t('viopBond.marketViopTitle', 'Piyasa VİOP Kontratları')}</h3>
                <div className="vb-filter-chips">
                    {VIOP_FILTERS.map((f) => (
                        <button
                            key={f}
                            type="button"
                            className={`pf-dash-btn pf-dash-btn--compact${marketFilter === f ? ' pf-dash-btn--active' : ''}`}
                            onClick={() => setMarketFilter(f)}
                        >
                            {viopFilterLabel(f, t)}
                        </button>
                    ))}
                </div>
                <div className="vb-toolbar">
                    <input
                        className="vb-search"
                        placeholder={t('viopBond.searchMarket', 'Kontrat ara…')}
                        value={marketSearch}
                        onChange={(e) => setMarketSearch(e.target.value)}
                    />
                </div>
                <div className={`vb-layout vb-layout--split`}>
                    <div className="pf-card-premium vb-table-wrap" style={{ ...cardStyle, padding: '0.5rem' }}>
                        {marketLoading ? (
                            <p className="vb-pad-muted" style={{ color: tokens.textMuted }}>
                                {t('viopBond.loading', 'Yükleniyor…')}
                            </p>
                        ) : filteredMarket.length === 0 ? (
                            <div className="vb-empty-state">
                                <p>{t('viopBond.marketEmpty', 'Piyasa kontratı bulunamadı.')}</p>
                            </div>
                        ) : (
                            <table className="vb-table">
                                <thead>
                                    <tr>
                                        <th>{t('viopBond.colContract', 'Kontrat')}</th>
                                        <th>{t('viopBond.colViopType', 'Tür')}</th>
                                        <th>{t('viopBond.colCurrentPrice', 'Son fiyat')}</th>
                                        <th>{t('viopBond.colDay', 'Gün')}</th>
                                        <th>{t('viopBond.colWeek', 'Hafta')}</th>
                                        <th>{t('viopBond.colMonth', 'Ay')}</th>
                                        <th>{t('viopBond.colYear', 'Yıl')}</th>
                                        <th>{t('viopBond.colTrend', 'Trend')}</th>
                                        <th />
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredMarket.map((row) => {
                                        const cat = viopCategoryFor(row.symbol);
                                        return (
                                            <tr
                                                key={row.symbol}
                                                className={
                                                    selectedMarket?.symbol === row.symbol ? 'vb-row--selected' : ''
                                                }
                                                onClick={() => setSelectedMarketSymbol(row.symbol)}
                                                style={{ cursor: 'pointer' }}
                                            >
                                                <td>
                                                    <strong>{row.symbol}</strong>
                                                    <div className="vb-cell-sub">{row.displayName}</div>
                                                </td>
                                                <td>{viopCategoryLabel(cat, t)}</td>
                                                <td>{fmtMoney(row.price, locale)}</td>
                                                <td className={pctClass(row.pctDay)}>{fmtPct(row.pctDay, locale)}</td>
                                                <td className={pctClass(row.pctWeek)}>{fmtPct(row.pctWeek, locale)}</td>
                                                <td className={pctClass(row.pctMonth)}>{fmtPct(row.pctMonth, locale)}</td>
                                                <td className={pctClass(row.pctYear)}>{fmtPct(row.pctYear, locale)}</td>
                                                <td>
                                                    <MarketSparkline
                                                        closes={row.sparkline}
                                                        bgColor={tokens.bgCard}
                                                        lineColor={row.trend === 'UP' ? '#22c55e' : '#ef4444'}
                                                    />
                                                </td>
                                                <td onClick={(e) => e.stopPropagation()}>
                                                    <div className="vb-actions">
                                                        <button
                                                            type="button"
                                                            className="pf-dash-btn pf-dash-btn--compact"
                                                            onClick={() => setSelectedMarketSymbol(row.symbol)}
                                                        >
                                                            {t('viopBond.detail', 'Detay')}
                                                        </button>
                                                        <button
                                                            type="button"
                                                            className="pf-dash-btn pf-dash-btn--compact"
                                                            onClick={() =>
                                                                setAlertSymbol({
                                                                    symbol: row.symbol,
                                                                    displayName: row.displayName,
                                                                })
                                                            }
                                                        >
                                                            {t('viopBond.alert', 'Alarm')}
                                                        </button>
                                                        <button
                                                            type="button"
                                                            className="pf-dash-btn pf-dash-btn--compact pf-dash-btn--primary"
                                                            onClick={() => openAdd(row)}
                                                        >
                                                            {t('viopBond.addToPosition', 'Pozisyona Ekle')}
                                                        </button>
                                                    </div>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        )}
                    </div>
                    {selectedMarket ? (
                        <MarketInstrumentDetailPanel
                            instrument={selectedMarket}
                            mode="viop"
                            tokens={tokens}
                            onAddPosition={() => openAdd(selectedMarket)}
                            onSetAlert={() =>
                                setAlertSymbol({
                                    symbol: selectedMarket.symbol,
                                    displayName: selectedMarket.displayName,
                                })
                            }
                        />
                    ) : null}
                </div>
            </section>

            <section className="vb-section">
                <h3 className="vb-section-title">{t('viopBond.viopPositionsTitle', 'VİOP Pozisyonları')}</h3>
                {openPositions.length > 0 ? (
                    <p className="vb-mini-summary" style={{ color: tokens.textMuted }}>
                        {t('viopBond.openPnlTotal', 'Toplam açık K/Z')}: {fmtMoney(summary?.totalUnrealizedPnl, locale)} ·{' '}
                        {t('viopBond.viopMargin', 'Teminat')}: {fmtMoney(summary?.totalInitialMargin, locale)}
                    </p>
                ) : null}
                <div className="vb-toolbar">
                    <input
                        className="vb-search"
                        placeholder={t('viopBond.searchPosition', 'Pozisyon ara…')}
                        value={positionSearch}
                        onChange={(e) => setPositionSearch(e.target.value)}
                    />
                </div>
                <div className="pf-card-premium vb-table-wrap" style={{ ...cardStyle, padding: '0.5rem' }}>
                    {positionsLoading ? (
                        <p className="vb-pad-muted" style={{ color: tokens.textMuted }}>
                            {t('viopBond.loading', 'Yükleniyor…')}
                        </p>
                    ) : filteredPositions.length === 0 ? (
                        <div className="vb-empty-state">
                            <p>
                                {t(
                                    'viopBond.viopEmpty',
                                    'Henüz VİOP pozisyonunuz yok. Piyasadaki kontratlardan birini seçerek long/short pozisyon ekleyebilirsiniz.',
                                )}
                            </p>
                            {filteredMarket[0] ? (
                                <button
                                    type="button"
                                    className="pf-dash-btn pf-dash-btn--primary"
                                    onClick={() => {
                                        setSelectedMarketSymbol(filteredMarket[0]!.symbol);
                                        openAdd(filteredMarket[0]!);
                                    }}
                                >
                                    {t('viopBond.exploreMarket', 'Piyasa kontratlarını incele')}
                                </button>
                            ) : null}
                        </div>
                    ) : (
                        <table className="vb-table">
                            <thead>
                                <tr>
                                    <th>{t('viopBond.colContract', 'Kontrat')}</th>
                                    <th>{t('viopBond.colDirection', 'Yön')}</th>
                                    <th>{t('viopBond.colCount', 'Adet')}</th>
                                    <th>{t('viopBond.colEntryPrice', 'Giriş')}</th>
                                    <th>{t('viopBond.colCurrentPrice', 'Güncel')}</th>
                                    <th>{t('viopBond.colPnl', 'K/Z')}</th>
                                    <th>{t('viopBond.colExposure', 'Maruziyet')}</th>
                                    <th>{t('viopBond.colStatus', 'Durum')}</th>
                                    <th />
                                </tr>
                            </thead>
                            <tbody>
                                {filteredPositions.map((row) => (
                                    <tr
                                        key={row.id}
                                        className={selectedPositionId === row.id ? 'vb-row--selected' : ''}
                                        onClick={() => setSelectedPositionId(row.id)}
                                        style={{ cursor: 'pointer' }}
                                    >
                                        <td>{row.displayName?.trim() || row.symbol}</td>
                                        <td>{row.direction}</td>
                                        <td>{row.contractCount}</td>
                                        <td>{fmtMoney(row.entryPrice, locale)}</td>
                                        <td>{fmtMoney(row.currentPrice, locale)}</td>
                                        <td className={pnlClass(row.unrealizedPnl)}>{fmtMoney(row.unrealizedPnl, locale)}</td>
                                        <td>{fmtMoney(row.riskExposure, locale)}</td>
                                        <td>
                                            <span className={`vb-badge vb-badge--${row.status.toLowerCase()}`}>
                                                {row.status}
                                            </span>
                                        </td>
                                        <td onClick={(e) => e.stopPropagation()}>
                                            <div className="vb-actions">
                                                <button
                                                    type="button"
                                                    className="pf-dash-btn pf-dash-btn--compact"
                                                    onClick={() => setDetailPosition(row)}
                                                >
                                                    {t('viopBond.detail', 'Detay')}
                                                </button>
                                                <button
                                                    type="button"
                                                    className="pf-dash-btn pf-dash-btn--compact"
                                                    onClick={() =>
                                                        setAlertSymbol({
                                                            symbol: row.symbol,
                                                            displayName: row.displayName ?? undefined,
                                                        })
                                                    }
                                                >
                                                    {t('viopBond.alert', 'Alarm')}
                                                </button>
                                                {row.status === 'OPEN' ? (
                                                    <>
                                                        <button
                                                            type="button"
                                                            className="pf-dash-btn pf-dash-btn--compact"
                                                            onClick={() => {
                                                                setEditRow(row);
                                                                setAddInstrument(null);
                                                                setModalOpen(true);
                                                            }}
                                                        >
                                                            {t('viopBond.edit', 'Güncelle')}
                                                        </button>
                                                        <button
                                                            type="button"
                                                            className="pf-dash-btn pf-dash-btn--compact"
                                                            onClick={() => handleClose(row)}
                                                        >
                                                            {t('viopBond.close', 'Kapat')}
                                                        </button>
                                                    </>
                                                ) : null}
                                                <button
                                                    type="button"
                                                    className="pf-dash-btn pf-dash-btn--compact"
                                                    onClick={() => {
                                                        if (window.confirm(t('viopBond.confirmDelete', 'Silinsin mi?'))) {
                                                            deleteMut.mutate(row.id);
                                                        }
                                                    }}
                                                >
                                                    {t('viopBond.delete', 'Sil')}
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}
                </div>
            </section>

            <div className="vb-analysis-grid">
                <AnalysisMiniCard title={t('viopBond.chartPnl', 'Açık K/Z dağılımı')} tokens={tokens}>
                    {pnlChart.length === 0 ? (
                        <p style={{ color: tokens.textMuted, fontSize: '0.82rem' }}>{t('viopBond.noData', 'Veri yok')}</p>
                    ) : (
                        <ResponsiveContainer width="100%" height={160}>
                            <PieChart>
                                <Pie data={pnlChart} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={60} label>
                                    {pnlChart.map((_, i) => (
                                        <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
                                    ))}
                                </Pie>
                                <Tooltip />
                            </PieChart>
                        </ResponsiveContainer>
                    )}
                </AnalysisMiniCard>
                <AnalysisMiniCard title={t('viopBond.chartDir', 'Long / Short')} tokens={tokens}>
                    {dirChart.length === 0 ? (
                        <p style={{ color: tokens.textMuted, fontSize: '0.82rem' }}>{t('viopBond.noData', 'Veri yok')}</p>
                    ) : (
                        <ResponsiveContainer width="100%" height={160}>
                            <PieChart>
                                <Pie data={dirChart} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={60} label>
                                    {dirChart.map((_, i) => (
                                        <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
                                    ))}
                                </Pie>
                                <Tooltip />
                            </PieChart>
                        </ResponsiveContainer>
                    )}
                </AnalysisMiniCard>
                <AnalysisMiniCard title={t('viopBond.topGainers', 'En çok kazandıran')} tokens={tokens}>
                    <RankList items={topGainers.map((r) => ({ label: r.symbol, value: fmtMoney(r.unrealizedPnl, locale) }))} empty={t('viopBond.noData', 'Veri yok')} muted={tokens.textMuted} />
                </AnalysisMiniCard>
                <AnalysisMiniCard title={t('viopBond.topLosers', 'En çok zarar yazan')} tokens={tokens}>
                    <RankList items={topLosers.map((r) => ({ label: r.symbol, value: fmtMoney(r.unrealizedPnl, locale) }))} empty={t('viopBond.noData', 'Veri yok')} muted={tokens.textMuted} />
                </AnalysisMiniCard>
                <AnalysisMiniCard title={t('viopBond.expiringList', 'Yaklaşan vade')} tokens={tokens}>
                    <RankList
                        items={expiring.map((r) => ({
                            label: r.symbol,
                            value: `${r.daysToExpiry} ${t('viopBond.days', 'gün')}`,
                        }))}
                        empty={t('viopBond.noExpiring', 'Yakın vade yok')}
                        muted={tokens.textMuted}
                    />
                </AnalysisMiniCard>
                <AnalysisMiniCard title={t('viopBond.riskDetect', 'Risk tespiti')} tokens={tokens}>
                    {riskSummary ? (
                        <ul className="vb-risk-detect">
                            <li>
                                {t('viopBond.topExposure', 'En yüksek maruziyet')}: <strong>{riskSummary.top?.symbol}</strong>
                            </li>
                            <li>
                                Long: <strong>%{riskSummary.longPct}</strong> · Short: <strong>%{riskSummary.shortPct}</strong>
                            </li>
                        </ul>
                    ) : (
                        <p style={{ color: tokens.textMuted, fontSize: '0.82rem' }}>{t('viopBond.noData', 'Veri yok')}</p>
                    )}
                </AnalysisMiniCard>
            </div>

            <ViopPositionAddModal
                open={modalOpen && (addInstrument != null || editRow != null)}
                onClose={() => {
                    setModalOpen(false);
                    setAddInstrument(null);
                    setEditRow(null);
                }}
                instrument={addInstrument}
                editPosition={editRow}
                onSubmit={async (payload) => {
                    try {
                        if (editRow) {
                            await updateMut.mutateAsync({ id: editRow.id, payload });
                        } else {
                            await createMut.mutateAsync(payload);
                        }
                    } catch (e) {
                        throw new Error(readFinanceApiError(e).message);
                    }
                }}
            />

            {detailPosition ? (
                <div className="vb-modal-backdrop" onClick={() => setDetailPosition(null)} role="presentation">
                    <div className="vb-detail-overlay" onClick={(e) => e.stopPropagation()}>
                        <button
                            type="button"
                            className="pf-dash-btn pf-dash-btn--compact vb-detail-close"
                            onClick={() => setDetailPosition(null)}
                        >
                            {t('viopBond.close', 'Kapat')}
                        </button>
                        <ViopPositionDetailDrawer position={detailPosition} tokens={tokens} />
                    </div>
                </div>
            ) : null}

            <PriceAlertModal
                open={alertSymbol != null}
                onClose={() => setAlertSymbol(null)}
                assetType="VIOP"
                symbol={alertSymbol?.symbol ?? ''}
                displayName={alertSymbol?.displayName}
            />
        </div>
    );
}
