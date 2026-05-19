import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useLanguage } from '../../i18n/LanguageContext';
import { MarketSparkline } from '../market/MarketSparkline';
import { PriceAlertModal } from '../priceAlert/PriceAlertModal';
import { bondPositionKeys } from '../../queries/bondPositionKeys';
import { viopPositionKeys } from '../../queries/viopPositionKeys';
import {
    createBondPosition,
    deleteBondPosition,
    getBondSummary,
    listBondPositions,
    sellBondPosition,
    updateBondPosition,
} from '../../services/bondPositionApi';
import { readFinanceApiError } from '../../services/manualPortfolioApi';
import type { ManualBondPosition, ManualBondPositionCreatePayload } from '../../types/bondPosition';
import type { TerminalListInstrumentVm } from '../../utils/marketTerminalListVm';
import { bondTypeFromInstrument, fetchAllTerminalInstruments } from './viopBondMarket';
import { fmtMoney, fmtPct } from './formatViopBond';
import { BondPositionAddModal } from './BondPositionAddModal';
import { BondPositionDetailDrawer } from './BondPositionDetailDrawer';
import { MarketInstrumentDetailPanel } from './MarketInstrumentDetailPanel';
import { AnalysisMiniCard, RankList, pctClass, pnlClass } from './vbTabShared';

const CHART_COLORS = ['#22c55e', '#3b82f6', '#f59e0b', '#ef4444', '#a855f7'];
type ChartMode = 'value' | 'pnl' | 'coupon';

type Props = {
    tokens: { border: string; bgCard: string; textMuted: string };
};

export function BondAnalysisTab({ tokens }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const queryClient = useQueryClient();

    const [marketSearch, setMarketSearch] = useState('');
    const [selectedMarketSymbol, setSelectedMarketSymbol] = useState<string | null>(null);
    const [positionSearch, setPositionSearch] = useState('');
    const [chartMode, setChartMode] = useState<ChartMode>('value');
    const [addInstrument, setAddInstrument] = useState<TerminalListInstrumentVm | null>(null);
    const [manualModal, setManualModal] = useState(false);
    const [editRow, setEditRow] = useState<ManualBondPosition | null>(null);
    const [modalOpen, setModalOpen] = useState(false);
    const [alertSymbol, setAlertSymbol] = useState<{ symbol: string; displayName?: string } | null>(null);
    const [detailPosition, setDetailPosition] = useState<ManualBondPosition | null>(null);

    const { data: rows = [], isLoading: positionsLoading } = useQuery({
        queryKey: bondPositionKeys.list(),
        queryFn: listBondPositions,
    });

    const { data: summary } = useQuery({
        queryKey: bondPositionKeys.summary(),
        queryFn: getBondSummary,
    });

    const { data: marketRows = [], isLoading: marketLoading } = useQuery({
        queryKey: ['viop-bond', 'market', 'BOND'],
        queryFn: () => fetchAllTerminalInstruments('BOND'),
        staleTime: 60_000,
    });

    const invalidate = () => {
        void queryClient.invalidateQueries({ queryKey: bondPositionKeys.all });
        void queryClient.invalidateQueries({ queryKey: viopPositionKeys.all });
        void queryClient.invalidateQueries({ queryKey: bondPositionKeys.combined() });
    };

    const createMut = useMutation({ mutationFn: createBondPosition, onSuccess: invalidate });
    const updateMut = useMutation({
        mutationFn: ({ id, payload }: { id: number; payload: ManualBondPositionCreatePayload }) =>
            updateBondPosition(id, payload),
        onSuccess: invalidate,
    });
    const deleteMut = useMutation({ mutationFn: deleteBondPosition, onSuccess: invalidate });
    const sellMut = useMutation({
        mutationFn: ({ id, price, date }: { id: number; price: number; date: string }) =>
            sellBondPosition(id, { sellPrice: price, sellDate: date }),
        onSuccess: invalidate,
    });

    const filteredMarket = useMemo(() => {
        const q = marketSearch.trim().toLowerCase();
        if (!q) return marketRows;
        return marketRows.filter(
            (i) => i.symbol.toLowerCase().includes(q) || i.displayName.toLowerCase().includes(q),
        );
    }, [marketRows, marketSearch]);

    const selectedMarket =
        filteredMarket.find((i) => i.symbol === selectedMarketSymbol) ?? filteredMarket[0] ?? null;

    const openPositions = useMemo(() => rows.filter((r) => r.status === 'OPEN'), [rows]);

    const filteredPositions = useMemo(() => {
        const q = positionSearch.trim().toLowerCase();
        if (!q) return rows;
        return rows.filter(
            (r) => r.symbol.toLowerCase().includes(q) || (r.displayName ?? '').toLowerCase().includes(q),
        );
    }, [rows, positionSearch]);

    const barChartData = useMemo(() => {
        return openPositions.map((r) => {
            let value = 0;
            if (chartMode === 'value') value = Math.abs(Number(r.currentValue ?? 0));
            else if (chartMode === 'pnl') value = Math.abs(Number(r.pnl ?? 0));
            else value = Math.abs(Number(r.annualCoupon ?? r.couponRate ?? 0));
            return { name: r.symbol, value, signed: Number(r.pnl ?? 0) };
        });
    }, [openPositions, chartMode]);

    const bondRisk = useMemo(() => {
        if (!openPositions.length) return null;
        const byNominal = [...openPositions].sort((a, b) => Number(b.nominalValue) - Number(a.nominalValue));
        const byReturn = [...openPositions]
            .filter((p) => p.returnPct != null)
            .sort((a, b) => Number(b.returnPct) - Number(a.returnPct));
        const nearest = [...openPositions]
            .filter((p) => p.daysToMaturity != null)
            .sort((a, b) => (a.daysToMaturity ?? 9999) - (b.daysToMaturity ?? 9999))[0];
        const euro = openPositions.filter((p) => p.bondType === 'EUROBOND').length;
        return { byNominal: byNominal[0], best: byReturn[0], nearest, euroPct: Math.round((euro / openPositions.length) * 100) };
    }, [openPositions]);

    const currencyChart = useMemo(() => {
        if (!summary?.currencyBreakdown) return [];
        return Object.entries(summary.currencyBreakdown).map(([name, value]) => ({ name, value }));
    }, [summary]);

    const topReturns = useMemo(
        () =>
            [...openPositions]
                .filter((r) => r.returnPct != null)
                .sort((a, b) => Number(b.returnPct) - Number(a.returnPct))
                .slice(0, 5),
        [openPositions],
    );

    const expiring = useMemo(
        () =>
            [...openPositions]
                .filter((p) => p.daysToMaturity != null && p.daysToMaturity <= 30)
                .sort((a, b) => (a.daysToMaturity ?? 0) - (b.daysToMaturity ?? 0)),
        [openPositions],
    );

    const openAdd = (inst: TerminalListInstrumentVm) => {
        setAddInstrument(inst);
        setManualModal(false);
        setEditRow(null);
        setModalOpen(true);
    };

    const handleSell = (row: ManualBondPosition) => {
        const priceStr = window.prompt(t('viopBond.promptSellPrice', 'Satış fiyatı'), String(row.currentPrice ?? row.buyPrice));
        if (!priceStr) return;
        const date = window.prompt(t('viopBond.promptSellDate', 'Satış tarihi'), new Date().toISOString().slice(0, 10));
        if (!date) return;
        sellMut.mutate({ id: row.id, price: Number(priceStr), date });
    };

    const tabKpis = [
        { label: t('viopBond.bondOpen', 'Açık Pozisyon'), value: String(summary?.openPositionCount ?? 0) },
        { label: t('viopBond.bondNominal', 'Toplam Nominal'), value: fmtMoney(summary?.totalNominalValue, locale) },
        { label: t('viopBond.bondCurrent', 'Güncel Değer'), value: fmtMoney(summary?.totalCurrentValue, locale) },
        {
            label: t('viopBond.bondPnl', 'Fiyat K/Z'),
            value: fmtMoney(summary?.totalPnl, locale),
            pos: (summary?.totalPnl ?? 0) >= 0,
        },
        { label: t('viopBond.bondCoupon', 'Yıllık Kupon'), value: fmtMoney(summary?.annualCouponEstimate, locale) },
        { label: t('viopBond.bondExpiring', 'Yaklaşan Vade'), value: String(summary?.expiringSoonCount ?? 0) },
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
                        <h3>{t('viopBond.bondMainChart', 'Tahvil Analiz Grafiği')}</h3>
                        <div className="vb-chart-mode-tabs">
                            {(
                                [
                                    ['value', t('viopBond.chartModeValue', 'Güncel Değer')],
                                    ['pnl', t('viopBond.chartModePnl', 'Fiyat K/Z')],
                                    ['coupon', t('viopBond.chartModeCoupon', 'Kupon')],
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
                            <p>{t('viopBond.bondChartEmpty', 'Tahvil pozisyonu ekledikçe grafik dolacaktır.')}</p>
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
                    <h3>{t('viopBond.bondRiskSummary', 'Tahvil Risk Özeti')}</h3>
                    {bondRisk ? (
                        <ul className="vb-risk-list">
                            <li>
                                <span>{t('viopBond.largestNominal', 'En büyük nominal')}</span>
                                <strong>{bondRisk.byNominal?.symbol}</strong>
                            </li>
                            <li>
                                <span>{t('viopBond.bestReturn', 'En yüksek getiri')}</span>
                                <strong>
                                    {bondRisk.best
                                        ? `${bondRisk.best.symbol} (${fmtPct(bondRisk.best.returnPct, locale)})`
                                        : '—'}
                                </strong>
                            </li>
                            <li>
                                <span>{t('viopBond.nearestExpiry', 'En yakın vade')}</span>
                                <strong>
                                    {bondRisk.nearest
                                        ? `${bondRisk.nearest.symbol} (${bondRisk.nearest.daysToMaturity}g)`
                                        : '—'}
                                </strong>
                            </li>
                            <li>
                                <span>{t('viopBond.eurobondRatio', 'Eurobond oranı')}</span>
                                <strong>%{bondRisk.euroPct}</strong>
                            </li>
                        </ul>
                    ) : (
                        <p style={{ color: tokens.textMuted, fontSize: '0.85rem' }}>
                            {t('viopBond.bondRiskEmpty', 'Henüz tahvil pozisyonu yok.')}
                        </p>
                    )}
                </aside>
            </div>

            <section className="vb-section">
                <h3 className="vb-section-title">{t('viopBond.marketBondTitle', 'Piyasa Tahvil / Eurobond')}</h3>
                <div className="vb-toolbar">
                    <input
                        className="vb-search"
                        placeholder={t('viopBond.searchMarket', 'Enstrüman ara…')}
                        value={marketSearch}
                        onChange={(e) => setMarketSearch(e.target.value)}
                    />
                    <button
                        type="button"
                        className="pf-dash-btn pf-dash-btn--primary"
                        onClick={() => {
                            setManualModal(true);
                            setAddInstrument(null);
                            setEditRow(null);
                            setModalOpen(true);
                        }}
                    >
                        <Plus size={14} />
                        {t('viopBond.addBondManual', 'Manuel Tahvil Ekle')}
                    </button>
                </div>
                <div className="vb-layout vb-layout--split">
                    <div className="pf-card-premium vb-table-wrap" style={{ ...cardStyle, padding: '0.5rem' }}>
                        {marketLoading ? (
                            <p className="vb-pad-muted" style={{ color: tokens.textMuted }}>
                                {t('viopBond.loading', 'Yükleniyor…')}
                            </p>
                        ) : filteredMarket.length === 0 ? (
                            <div className="vb-empty-state">
                                <p>{t('viopBond.bondMarketEmpty', 'Piyasa tahvil verisi bulunamadı. Manuel ekleyebilirsiniz.')}</p>
                                <button
                                    type="button"
                                    className="pf-dash-btn pf-dash-btn--primary"
                                    onClick={() => {
                                        setManualModal(true);
                                        setModalOpen(true);
                                    }}
                                >
                                    {t('viopBond.addBondManual', 'Manuel Tahvil Ekle')}
                                </button>
                            </div>
                        ) : (
                            <table className="vb-table">
                                <thead>
                                    <tr>
                                        <th>{t('viopBond.colInstrument', 'Enstrüman')}</th>
                                        <th>{t('viopBond.colBondType', 'Tür')}</th>
                                        <th>{t('viopBond.colCurrency', 'Döviz')}</th>
                                        <th>{t('viopBond.colCurrentPrice', 'Fiyat')}</th>
                                        <th>{t('viopBond.colMaturity', 'Vade')}</th>
                                        <th>{t('viopBond.colCouponRate', 'Kupon')}</th>
                                        <th>{t('viopBond.colTrend', 'Trend')}</th>
                                        <th />
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredMarket.map((row) => (
                                        <tr
                                            key={row.symbol}
                                            className={selectedMarket?.symbol === row.symbol ? 'vb-row--selected' : ''}
                                            onClick={() => setSelectedMarketSymbol(row.symbol)}
                                            style={{ cursor: 'pointer' }}
                                        >
                                            <td>
                                                <strong>{row.symbol}</strong>
                                                <div className="vb-cell-sub">{row.displayName}</div>
                                            </td>
                                            <td>{bondTypeFromInstrument(row.symbol, row.displayName)}</td>
                                            <td>{row.currency ?? 'TRY'}</td>
                                            <td>{fmtMoney(row.price, locale)}</td>
                                            <td>{row.maturityDate ?? '—'}</td>
                                            <td>{row.couponRate != null ? fmtPct(row.couponRate, locale) : '—'}</td>
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
                                                            setAlertSymbol({ symbol: row.symbol, displayName: row.displayName })
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
                                    ))}
                                </tbody>
                            </table>
                        )}
                    </div>
                    {selectedMarket ? (
                        <MarketInstrumentDetailPanel
                            instrument={selectedMarket}
                            mode="bond"
                            tokens={tokens}
                            onAddPosition={() => openAdd(selectedMarket)}
                            onSetAlert={() =>
                                setAlertSymbol({ symbol: selectedMarket.symbol, displayName: selectedMarket.displayName })
                            }
                        />
                    ) : null}
                </div>
            </section>

            <section className="vb-section">
                <h3 className="vb-section-title">{t('viopBond.bondPositionsTitle', 'Tahvil Pozisyonları')}</h3>
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
                                    'viopBond.bondEmpty',
                                    'Henüz tahvil/eurobond pozisyonunuz yok. Mevcut enstrümanlardan seçim yapabilir veya manuel tahvil ekleyebilirsiniz.',
                                )}
                            </p>
                            <button
                                type="button"
                                className="pf-dash-btn pf-dash-btn--primary"
                                onClick={() => {
                                    setManualModal(true);
                                    setModalOpen(true);
                                }}
                            >
                                {t('viopBond.addBondManual', 'Tahvil Ekle')}
                            </button>
                        </div>
                    ) : (
                        <table className="vb-table">
                            <thead>
                                <tr>
                                    <th>{t('viopBond.colInstrument', 'Enstrüman')}</th>
                                    <th>{t('viopBond.colCurrency', 'Döviz')}</th>
                                    <th>{t('viopBond.colNominal', 'Nominal')}</th>
                                    <th>{t('viopBond.colValue', 'Değer')}</th>
                                    <th>{t('viopBond.colPnl', 'K/Z')}</th>
                                    <th>{t('viopBond.colReturn', 'Getiri')}</th>
                                    <th>{t('viopBond.colStatus', 'Durum')}</th>
                                    <th />
                                </tr>
                            </thead>
                            <tbody>
                                {filteredPositions.map((row) => (
                                    <tr key={row.id} style={{ cursor: 'pointer' }}>
                                        <td>{row.displayName?.trim() || row.symbol}</td>
                                        <td>{row.currency}</td>
                                        <td>{fmtMoney(row.nominalValue, locale)}</td>
                                        <td>{fmtMoney(row.currentValue, locale)}</td>
                                        <td className={pnlClass(row.pnl)}>{fmtMoney(row.pnl, locale)}</td>
                                        <td className={pctClass(row.returnPct)}>{fmtPct(row.returnPct, locale)}</td>
                                        <td>{row.status}</td>
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
                                                                setManualModal(false);
                                                                setModalOpen(true);
                                                            }}
                                                        >
                                                            {t('viopBond.edit', 'Güncelle')}
                                                        </button>
                                                        <button
                                                            type="button"
                                                            className="pf-dash-btn pf-dash-btn--compact"
                                                            onClick={() => handleSell(row)}
                                                        >
                                                            {t('viopBond.sold', 'Satıldı')}
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
                <AnalysisMiniCard title={t('viopBond.chartCurrency', 'Para birimi')} tokens={tokens}>
                    {currencyChart.length === 0 ? (
                        <p style={{ color: tokens.textMuted, fontSize: '0.82rem' }}>{t('viopBond.noData', 'Veri yok')}</p>
                    ) : (
                        <ResponsiveContainer width="100%" height={160}>
                            <PieChart>
                                <Pie data={currencyChart} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={60} label>
                                    {currencyChart.map((_, i) => (
                                        <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
                                    ))}
                                </Pie>
                                <Tooltip />
                            </PieChart>
                        </ResponsiveContainer>
                    )}
                </AnalysisMiniCard>
                <AnalysisMiniCard title={t('viopBond.topReturns', 'En yüksek getiri')} tokens={tokens}>
                    <RankList
                        items={topReturns.map((r) => ({
                            label: r.symbol,
                            value: fmtPct(r.returnPct, locale),
                        }))}
                        empty={t('viopBond.noData', 'Veri yok')}
                        muted={tokens.textMuted}
                    />
                </AnalysisMiniCard>
                <AnalysisMiniCard title={t('viopBond.expiringList', 'Yaklaşan vade')} tokens={tokens}>
                    <RankList
                        items={expiring.map((r) => ({
                            label: r.symbol,
                            value: `${r.daysToMaturity} ${t('viopBond.days', 'gün')}`,
                        }))}
                        empty={t('viopBond.noExpiring', 'Yakın vade yok')}
                        muted={tokens.textMuted}
                    />
                </AnalysisMiniCard>
                <AnalysisMiniCard title={t('viopBond.riskDetect', 'Risk tespiti')} tokens={tokens}>
                    {bondRisk ? (
                        <ul className="vb-risk-detect">
                            <li>
                                {t('viopBond.largestNominal', 'En büyük nominal')}: <strong>{bondRisk.byNominal?.symbol}</strong>
                            </li>
                            <li>
                                Eurobond: <strong>%{bondRisk.euroPct}</strong>
                            </li>
                        </ul>
                    ) : (
                        <p style={{ color: tokens.textMuted, fontSize: '0.82rem' }}>{t('viopBond.noData', 'Veri yok')}</p>
                    )}
                </AnalysisMiniCard>
            </div>

            <BondPositionAddModal
                open={modalOpen}
                onClose={() => {
                    setModalOpen(false);
                    setAddInstrument(null);
                    setEditRow(null);
                    setManualModal(false);
                }}
                instrument={addInstrument}
                manualOnly={manualModal && !editRow}
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
                        <BondPositionDetailDrawer position={detailPosition} tokens={tokens} />
                    </div>
                </div>
            ) : null}

            <PriceAlertModal
                open={alertSymbol != null}
                onClose={() => setAlertSymbol(null)}
                assetType="BOND"
                symbol={alertSymbol?.symbol ?? ''}
                displayName={alertSymbol?.displayName}
            />
        </div>
    );
}
