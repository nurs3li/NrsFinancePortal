import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { bondPositionKeys } from '../../queries/bondPositionKeys';
import { viopPositionKeys } from '../../queries/viopPositionKeys';
import {
    closeViopPosition,
    createViopPosition,
    deleteViopPosition,
    getViopSummary,
    listViopPositions,
    updateViopPosition,
} from '../../services/viopPositionApi';
import { readFinanceApiError } from '../../services/manualPortfolioApi';
import { useLanguage } from '../../i18n/LanguageContext';
import type { ManualViopPosition, ManualViopPositionCreatePayload } from '../../types/viopPosition';
import type { TerminalListInstrumentVm } from '../../utils/marketTerminalListVm';
import { fetchAllTerminalInstruments } from './viopBondMarket';
import { fmtLeverageX, fmtMoney, fmtRatioPercent } from './formatViopBond';
import { ViopPositionAddModal } from './ViopPositionAddModal';
import { CloseViopPositionModal } from './CloseViopPositionModal';
import { ViopBondToast } from './ViopBondToast';
import type { ManualViopPositionClosePayload } from '../../types/viopPosition';
import { ViopPositionDetailDrawer } from './ViopPositionDetailDrawer';
import { ViopPositionsSection } from './ViopPositionsSection';
import { ViopAnalyticsSection } from './ViopAnalyticsSection';
import { ViopMarketSection } from './ViopMarketSection';
import { PriceAlertModal } from '../priceAlert/PriceAlertModal';

type Props = {
    tokens: { border: string; bgCard: string; textMuted: string; text: string };
};

export function ViopAnalysisTab({ tokens }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const queryClient = useQueryClient();

    const [selectedMarketSymbol, setSelectedMarketSymbol] = useState<string | null>(null);
    const [addInstrument, setAddInstrument] = useState<TerminalListInstrumentVm | null>(null);
    const [editRow, setEditRow] = useState<ManualViopPosition | null>(null);
    const [modalOpen, setModalOpen] = useState(false);
    const [alertSymbol, setAlertSymbol] = useState<{
        symbol: string;
        displayName?: string;
        referencePrice?: number | null;
    } | null>(null);
    const [detailPosition, setDetailPosition] = useState<ManualViopPosition | null>(null);
    const [closePosition, setClosePosition] = useState<ManualViopPosition | null>(null);
    const [toast, setToast] = useState<{ message: string; variant: 'success' | 'error' } | null>(null);

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
        mutationFn: ({ id, payload }: { id: number; payload: ManualViopPositionClosePayload }) =>
            closeViopPosition(id, payload),
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

    const openAdd = (inst: TerminalListInstrumentVm) => {
        setAddInstrument(inst);
        setEditRow(null);
        setModalOpen(true);
    };

    const handleClose = (row: ManualViopPosition) => {
        setClosePosition(row);
    };

    const expiringSoon = summary?.expiringSoonCount ?? 0;
    const hasMissingFx = summary?.hasMissingFxRate ?? false;

    const tabKpis: {
        label: string;
        value: string;
        hint: string;
        pos?: boolean;
        title?: string;
    }[] = [
        {
            label: t('viopBond.viopOpenCount', 'Açık Pozisyon'),
            value: String(summary?.openPositionCount ?? 0),
            hint: t('viopBond.kpiViopOpenHint', 'Aktif VİOP sözleşmesi'),
        },
        {
            label: t('viopBond.viopMargin', 'Toplam Teminat'),
            value: fmtMoney(summary?.totalInitialMargin, locale),
            hint: `${t('viopBond.kpiViopMarginHint', 'Pozisyonlar için ayrılan teminat')}${
                summary?.marginRatio != null
                    ? ` · ${t('viopBond.marginRatioShort', 'Teminat oranı')}: ${fmtRatioPercent(summary.marginRatio, locale)}`
                    : ''
            }`,
            title: t(
                'viopBond.marginRatioTooltip',
                'Teminat oranı, toplam sözleşme büyüklüğünün ne kadarının teminat olarak ayrıldığını gösterir.',
            ),
        },
        {
            label: t('viopBond.viopPnl', 'Açık K/Z'),
            value: fmtMoney(summary?.totalUnrealizedPnl, locale),
            hint: t('viopBond.kpiViopPnlHint', 'Güncel fiyatlara göre anlık kâr/zarar'),
            pos: (summary?.totalUnrealizedPnl ?? 0) >= 0,
        },
        {
            label: t('viopBond.viopRisk', 'Risk Maruziyeti'),
            value: `${fmtMoney(summary?.totalRiskExposure, locale)} ₺`,
            hint: hasMissingFx
                ? t(
                      'viopBond.kpiViopExposureFxWarn',
                      'TRY karşılığı · Bazı pozisyonlar için kur eksik',
                  )
                : t('viopBond.kpiViopExposureTryHint', 'TRY karşılığı · Kaldıraçlı sözleşme büyüklüğü'),
            title: t(
                'viopBond.exposureTooltip',
                'Maruziyet portföy değeri değildir; sözleşmenin kaldıraçlı nominal büyüklüğünü gösterir. USD/EUR kontratlar USDTRY ile çevrilir.',
            ),
        },
        {
            label: t('viopBond.viopLeverage', 'Kaldıraç Etkisi'),
            value: fmtLeverageX(summary?.portfolioLeverage, locale),
            hint: t('viopBond.kpiViopLeverageHint', 'Teminata göre taşınan sözleşme büyüklüğü'),
            title: t(
                'viopBond.leverageTooltip',
                'Kaldıraç etkisi, yatırılan teminatın kaç katı büyüklüğünde fiyat riskine maruz kalındığını gösterir.',
            ),
        },
        {
            label: t('viopBond.viopPnlToMargin', 'K/Z / Teminat'),
            value: fmtRatioPercent(summary?.pnlToMarginRatio, locale),
            hint: t('viopBond.kpiViopPnlMarginHint', 'Açık K/Z’nin teminata oranı'),
            title: t(
                'viopBond.pnlMarginTooltip',
                'VİOP’ta kâr/zararın teminata oranı, pozisyonun teminat üzerindeki etkisini gösterir.',
            ),
        },
        {
            label: t('viopBond.viopLongShort', 'Long / Short'),
            value: `${summary?.longCount ?? 0} / ${summary?.shortCount ?? 0}`,
            hint: t('viopBond.kpiViopDirHint', 'Uzun / kısa yön dağılımı'),
        },
        {
            label: t('viopBond.viopExpiring', 'Vadesi Yakın'),
            value: expiringSoon > 0 ? String(expiringSoon) : t('viopBond.noExpiringShort', 'Yok'),
            hint:
                expiringSoon > 0
                    ? t('viopBond.kpiViopExpiringHint', '14 gün içinde vadesi dolacak')
                    : t('viopBond.viopNoExpiring14', '14 gün içinde vade yok'),
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
                        title={k.title}
                    >
                        <div className="vb-tab-kpi-label" style={{ color: tokens.textMuted }}>
                            {k.label}
                        </div>
                        <div className="vb-tab-kpi-value">{k.value}</div>
                        <div className="vb-tab-kpi-hint" style={{ color: tokens.textMuted }}>
                            {k.hint}
                        </div>
                    </div>
                ))}
            </div>

            <ViopPositionsSection
                rows={rows}
                loading={positionsLoading}
                tokens={tokens}
                onEdit={(row) => {
                    setEditRow(row);
                    setAddInstrument(null);
                    setModalOpen(true);
                }}
                onClose={handleClose}
                onDelete={(id) => deleteMut.mutate(id)}
                onDetail={setDetailPosition}
                onAlert={(row) =>
                    setAlertSymbol({
                        symbol: row.symbol,
                        displayName: row.displayName ?? undefined,
                        referencePrice:
                            row.currentPrice != null && Number.isFinite(Number(row.currentPrice))
                                ? Number(row.currentPrice)
                                : null,
                    })
                }
            />

            <ViopAnalyticsSection
                openPositions={openPositions}
                summary={summary}
                riskSummary={riskSummary}
                tokens={tokens}
            />

            <ViopMarketSection
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
                        referencePrice:
                            Number.isFinite(row.price) && row.price > 0 ? row.price : null,
                    })
                }
                onPositionDetail={setDetailPosition}
            />

            <CloseViopPositionModal
                open={closePosition != null}
                position={closePosition}
                onClose={() => setClosePosition(null)}
                onSubmit={async (payload) => {
                    if (!closePosition) return;
                    try {
                        await closeMut.mutateAsync({ id: closePosition.id, payload });
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

            <ViopPositionAddModal
                open={modalOpen && (addInstrument != null || editRow != null)}
                onClose={() => {
                    setModalOpen(false);
                    setAddInstrument(null);
                    setEditRow(null);
                }}
                instrument={addInstrument}
                editPosition={editRow}
                openPositions={openPositions}
                onSubmit={async (payload, action = 'create', mergeId) => {
                    try {
                        if (action === 'merge' && mergeId != null) {
                            await updateMut.mutateAsync({ id: mergeId, payload });
                        } else if (editRow) {
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
                        <ViopPositionDetailDrawer
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
                assetType="VIOP"
                symbol={alertSymbol?.symbol ?? ''}
                displayName={alertSymbol?.displayName}
                referencePrice={alertSymbol?.referencePrice}
                priceCurrency="TRY"
            />
        </div>
    );
}
