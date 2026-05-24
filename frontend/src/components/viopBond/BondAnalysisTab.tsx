import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
import { useLanguage } from '../../i18n/LanguageContext';
import type { ManualBondPosition, ManualBondPositionCreatePayload } from '../../types/bondPosition';
import type { TerminalListInstrumentVm } from '../../utils/marketTerminalListVm';
import { fetchAllTerminalInstruments } from './viopBondMarket';
import { fmtMoney } from './formatViopBond';
import { BondPositionAddModal } from './BondPositionAddModal';
import { CloseFixedIncomePositionModal } from './CloseFixedIncomePositionModal';
import { ViopBondToast } from './ViopBondToast';
import type { ManualBondPositionSellPayload } from '../../types/bondPosition';
import { BondPositionDetailDrawer } from './BondPositionDetailDrawer';
import { BondPositionsSection } from './BondPositionsSection';
import { BondAnalyticsSection } from './BondAnalyticsSection';
import { BondMarketSection } from './BondMarketSection';
import { PriceAlertModal } from '../priceAlert/PriceAlertModal';

type ChartMode = 'value' | 'pnl' | 'coupon';

type Props = {
    tokens: { border: string; bgCard: string; textMuted: string; text: string };
};

export function BondAnalysisTab({ tokens }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const queryClient = useQueryClient();

    const [selectedMarketSymbol, setSelectedMarketSymbol] = useState<string | null>(null);
    const [chartMode, setChartMode] = useState<ChartMode>('value');
    const [addInstrument, setAddInstrument] = useState<TerminalListInstrumentVm | null>(null);
    const [manualModal, setManualModal] = useState(false);
    const [editRow, setEditRow] = useState<ManualBondPosition | null>(null);
    const [modalOpen, setModalOpen] = useState(false);
    const [alertSymbol, setAlertSymbol] = useState<{
        symbol: string;
        displayName?: string;
        referencePrice?: number | null;
    } | null>(null);
    const [detailPosition, setDetailPosition] = useState<ManualBondPosition | null>(null);
    const [closePosition, setClosePosition] = useState<ManualBondPosition | null>(null);
    const [toast, setToast] = useState<{ message: string; variant: 'success' | 'error' } | null>(null);

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
        mutationFn: ({ id, payload }: { id: number; payload: ManualBondPositionSellPayload }) =>
            sellBondPosition(id, payload),
        onSuccess: () => {
            invalidate();
            setClosePosition(null);
            setToast({
                message: t('viopBond.closeSuccess', 'Pozisyon başarıyla kapatıldı.'),
                variant: 'success',
            });
        },
    });

    const openPositions = useMemo(() => rows.filter((r) => r.status === 'OPEN'), [rows]);

    const bondRisk = useMemo(() => {
        if (!openPositions.length) return null;
        const byNominal = [...openPositions].sort((a, b) => Number(b.nominalValue) - Number(a.nominalValue));
        const byReturn = [...openPositions]
            .filter((p) => p.returnPct != null)
            .sort((a, b) => Number(b.returnPct) - Number(a.returnPct));
        const nearest = [...openPositions]
            .filter((p) => p.daysToMaturity != null)
            .sort((a, b) => (a.daysToMaturity ?? 9999) - (b.daysToMaturity ?? 9999))[0];
        return { byNominal: byNominal[0], best: byReturn[0], nearest };
    }, [openPositions]);

    const openAdd = (inst: TerminalListInstrumentVm) => {
        setAddInstrument(inst);
        setManualModal(false);
        setEditRow(null);
        setModalOpen(true);
    };

    const openManual = () => {
        setManualModal(true);
        setAddInstrument(null);
        setEditRow(null);
        setModalOpen(true);
    };

    const handleSell = (row: ManualBondPosition) => {
        setClosePosition(row);
    };

    const tabKpis = [
        { label: t('viopBond.bondOpen', 'Açık Pozisyon'), value: String(summary?.openPositionCount ?? 0) },
        { label: t('viopBond.bondNominal', 'Toplam Nominal'), value: fmtMoney(summary?.totalNominalValue, locale) },
        { label: t('viopBond.bondCurrent', 'Güncel Değer'), value: fmtMoney(summary?.totalCurrentValue, locale) },
        {
            label: t('viopBond.bondPricePnl', 'Fiyat K/Z'),
            value: fmtMoney(summary?.totalPricePnl ?? summary?.totalPnl, locale),
            hint: t('viopBond.bondPricePnlHint', 'Sadece fiyat değişiminden gelen K/Z'),
            pos: (summary?.totalPricePnl ?? summary?.totalPnl ?? 0) >= 0,
        },
        {
            label: t('viopBond.bondCouponIncome', 'Kupon geliri'),
            value: fmtMoney(summary?.totalCollectedCoupon, locale),
            hint: t('viopBond.bondCouponIncomeHint', 'Tahmini tahsil edilmiş kupon toplamı'),
        },
        {
            label: t('viopBond.bondTotalReturn', 'Toplam getiri'),
            value: fmtMoney(summary?.totalPnl, locale),
            hint: t(
                'viopBond.bondTotalReturnHint',
                'Fiyat K/Z + tahsil edilen kupon. Üst karttaki toplam K/Z bu kalemi de içerir.',
            ),
            pos: (summary?.totalPnl ?? 0) >= 0,
        },
        { label: t('viopBond.bondCoupon', 'Yıllık kupon (tahmini)'), value: fmtMoney(summary?.annualCouponEstimate, locale) },
        {
            label: t('viopBond.bondExpiring', 'Yaklaşan Vade'),
            value:
                (summary?.expiringSoonCount ?? 0) > 0
                    ? String(summary?.expiringSoonCount)
                    : t('viopBond.noExpiringShort', 'Yok'),
            hint:
                (summary?.expiringSoonCount ?? 0) > 0
                    ? t('viopBond.bondExpiringHint', '30 gün içinde vadesi dolacak')
                    : t('viopBond.noExpiringDetail', '30 gün içinde vadesi dolacak tahvil yok'),
        },
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
                        title={'hint' in k ? k.hint : undefined}
                    >
                        <div className="vb-tab-kpi-label" style={{ color: tokens.textMuted }}>
                            {k.label}
                        </div>
                        <div className="vb-tab-kpi-value">{k.value}</div>
                    </div>
                ))}
            </div>

            <BondPositionsSection
                rows={rows}
                loading={positionsLoading}
                tokens={tokens}
                onManualAdd={openManual}
                onEdit={(row) => {
                    setEditRow(row);
                    setAddInstrument(null);
                    setManualModal(false);
                    setModalOpen(true);
                }}
                onSell={handleSell}
                onDelete={(id) => deleteMut.mutate(id)}
                onDetail={setDetailPosition}
                onAlert={(row) =>
                    setAlertSymbol({
                        symbol: row.symbol,
                        displayName: row.displayName ?? undefined,
                    })
                }
            />

            <BondAnalyticsSection
                openPositions={openPositions}
                summary={summary}
                chartMode={chartMode}
                onChartModeChange={setChartMode}
                bondRisk={bondRisk}
                tokens={tokens}
            />

            <BondMarketSection
                rows={marketRows}
                loading={marketLoading}
                positions={rows}
                tokens={tokens}
                selectedSymbol={selectedMarketSymbol}
                onSelect={setSelectedMarketSymbol}
                onAdd={openAdd}
                onAlert={(row) =>
                    setAlertSymbol({
                        symbol: row.symbol,
                        displayName: row.displayName,
                        referencePrice: Number.isFinite(row.price) && row.price > 0 ? row.price : null,
                    })
                }
            />

            <CloseFixedIncomePositionModal
                open={closePosition != null}
                position={closePosition}
                onClose={() => setClosePosition(null)}
                onSubmit={async (payload) => {
                    if (!closePosition) return;
                    try {
                        await sellMut.mutateAsync({ id: closePosition.id, payload });
                    } catch (e) {
                        setToast({
                            message: readFinanceApiError(e).message,
                            variant: 'error',
                        });
                        throw e;
                    }
                }}
            />

            {toast ? (
                <ViopBondToast
                    message={toast.message}
                    variant={toast.variant}
                    onDismiss={() => setToast(null)}
                />
            ) : null}

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
                    <div className="vb-detail-drawer-mobile vb-detail-overlay" onClick={(e) => e.stopPropagation()}>
                        <BondPositionDetailDrawer
                            position={detailPosition}
                            tokens={tokens}
                            onClose={() => setDetailPosition(null)}
                        />
                    </div>
                </div>
            ) : null}

            <PriceAlertModal
                open={alertSymbol != null}
                onClose={() => setAlertSymbol(null)}
                assetType="BOND"
                symbol={alertSymbol?.symbol ?? ''}
                displayName={alertSymbol?.displayName}
                referencePrice={alertSymbol?.referencePrice}
                priceCurrency="TRY"
            />
        </div>
    );
}
