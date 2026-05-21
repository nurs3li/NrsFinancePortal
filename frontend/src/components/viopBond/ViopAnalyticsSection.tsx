import { useMemo } from 'react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { useLanguage } from '../../i18n/LanguageContext';
import type { ManualViopPosition, ViopPositionSummary } from '../../types/viopPosition';
import {
    buildViopRiskReasons,
    countViopPnlBuckets,
    topExposurePositions,
} from './viopAnalysisHelpers';
import { viopDirectionLabel } from './viopPositionLabels';
import { fmtMoney } from './formatViopBond';
import { AnalysisMiniCard, RankList } from './vbTabShared';

const CHART_COLORS = ['#22c55e', '#ef4444', '#64748b', '#3b82f6', '#f59e0b'];

type Props = {
    openPositions: ManualViopPosition[];
    summary: ViopPositionSummary | undefined;
    riskSummary: {
        top?: ManualViopPosition;
        longPct: number;
        shortPct: number;
        nearest?: ManualViopPosition;
    } | null;
    tokens: { border: string; bgCard: string; textMuted: string };
};

export function ViopAnalyticsSection({ openPositions, summary, riskSummary, tokens }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    const pnlBuckets = useMemo(() => countViopPnlBuckets(openPositions), [openPositions]);
    const totalPnl = summary?.totalUnrealizedPnl ?? 0;
    const hasMeaningfulPnl = pnlBuckets.profit > 0 || pnlBuckets.loss > 0;

    const exposureList = useMemo(() => topExposurePositions(openPositions, 5), [openPositions]);
    const totalExposure = summary?.totalRiskExposure ?? 0;

    const dirChart = useMemo(() => {
        const long = openPositions.filter((r) => r.direction === 'LONG').length;
        const short = openPositions.filter((r) => r.direction === 'SHORT').length;
        return [
            { name: t('viopBond.dirLong', 'Uzun'), value: long },
            { name: t('viopBond.dirShort', 'Kısa'), value: short },
        ].filter((x) => x.value > 0);
    }, [openPositions, t]);

    const expiring = useMemo(
        () =>
            [...openPositions]
                .filter((p) => p.daysToExpiry != null && p.daysToExpiry <= 14)
                .sort((a, b) => (a.daysToExpiry ?? 0) - (b.daysToExpiry ?? 0)),
        [openPositions],
    );

    const riskReasons = useMemo(
        () => buildViopRiskReasons(openPositions, summary, riskSummary, t),
        [openPositions, summary, riskSummary, t],
    );

    return (
        <div className="vb-analytics-block">
            <div className="vb-analysis-grid">
                <AnalysisMiniCard title={t('viopBond.viopPnlDist', 'Açık K/Z dağılımı')} tokens={tokens}>
                    <p style={{ margin: '0 0 0.5rem', fontSize: '0.9rem' }}>
                        {t('viopBond.total', 'Toplam')}:{' '}
                        <strong className={totalPnl >= 0 ? 'portfolio-pnl-pos' : 'portfolio-pnl-neg'}>
                            {fmtMoney(totalPnl, locale)}
                        </strong>
                    </p>
                    {openPositions.length === 0 ? (
                        <p style={{ color: tokens.textMuted, fontSize: '0.82rem' }}>
                            {t('viopBond.viopChartEmpty', 'Açık pozisyon ekledikçe analiz dolacaktır.')}
                        </p>
                    ) : !hasMeaningfulPnl ? (
                        <p style={{ color: tokens.textMuted, fontSize: '0.82rem' }}>
                            {t(
                                'viopBond.viopPnlNeutral',
                                'Pozisyonlarınızda henüz fiyat farkından oluşan açık K/Z yok.',
                            )}
                        </p>
                    ) : (
                        <ul className="vb-risk-detect" style={{ margin: 0 }}>
                            <li>
                                {t('viopBond.inProfit', 'Kârda')}: <strong>{pnlBuckets.profit}</strong>
                            </li>
                            <li>
                                {t('viopBond.inLoss', 'Zararda')}: <strong>{pnlBuckets.loss}</strong>
                            </li>
                            <li>
                                {t('viopBond.neutral', 'Nötr')}: <strong>{pnlBuckets.neutral}</strong>
                            </li>
                        </ul>
                    )}
                </AnalysisMiniCard>

                <AnalysisMiniCard title={t('viopBond.exposureFocus', 'Maruziyet yoğunluğu')} tokens={tokens}>
                    <p style={{ margin: '0 0 0.5rem', fontSize: '0.82rem', color: tokens.textMuted }}>
                        {t('viopBond.totalExposure', 'Toplam')}: {fmtMoney(totalExposure, locale)}
                    </p>
                    <RankList
                        items={exposureList.map((r) => ({
                            label: r.symbol,
                            value: fmtMoney(r.riskExposure, locale),
                        }))}
                        empty={t('viopBond.noData', 'Veri yok')}
                        muted={tokens.textMuted}
                    />
                </AnalysisMiniCard>

                <AnalysisMiniCard title={t('viopBond.chartDir', 'Long / Short dengesi')} tokens={tokens}>
                    {dirChart.length === 0 ? (
                        <p style={{ color: tokens.textMuted, fontSize: '0.82rem' }}>{t('viopBond.noData', 'Veri yok')}</p>
                    ) : (
                        <>
                            <p style={{ margin: '0 0 0.35rem', fontSize: '0.82rem' }}>
                                {t('viopBond.dirLong', 'Uzun')}: {openPositions.filter((p) => p.direction === 'LONG').length}{' '}
                                · {t('viopBond.dirShort', 'Kısa')}:{' '}
                                {openPositions.filter((p) => p.direction === 'SHORT').length}
                            </p>
                            {riskSummary ? (
                                <p style={{ margin: '0 0 0.5rem', fontSize: '0.78rem', color: tokens.textMuted }}>
                                    {t('viopBond.directionBias', 'Yön yoğunluğu')}: %{riskSummary.longPct}{' '}
                                    {viopDirectionLabel('LONG', t)}
                                </p>
                            ) : null}
                            <ResponsiveContainer width="100%" height={120}>
                                <PieChart>
                                    <Pie data={dirChart} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={45} label>
                                        {dirChart.map((_, i) => (
                                            <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
                                        ))}
                                    </Pie>
                                    <Tooltip />
                                </PieChart>
                            </ResponsiveContainer>
                        </>
                    )}
                </AnalysisMiniCard>

                <AnalysisMiniCard title={t('viopBond.expiryTrack', 'Vade takibi')} tokens={tokens}>
                    {riskSummary?.nearest ? (
                        <p style={{ margin: '0 0 0.5rem', fontSize: '0.82rem' }}>
                            {t('viopBond.nearestExpiry', 'En yakın vade')}: <strong>{riskSummary.nearest.symbol}</strong>
                            {riskSummary.nearest.daysToExpiry != null
                                ? ` (${riskSummary.nearest.daysToExpiry} ${t('viopBond.days', 'gün')})`
                                : ''}
                        </p>
                    ) : null}
                    <RankList
                        items={expiring.map((r) => ({
                            label: r.symbol,
                            value: `${r.daysToExpiry} ${t('viopBond.days', 'gün')}`,
                        }))}
                        empty={t('viopBond.viopNoExpiring14', '14 gün içinde vade yok')}
                        muted={tokens.textMuted}
                    />
                </AnalysisMiniCard>

                <AnalysisMiniCard title={t('viopBond.riskDetect', 'Risk tespiti')} tokens={tokens}>
                    {riskReasons.length > 0 ? (
                        <ul className="vb-risk-detect">
                            {riskReasons.map((r) => (
                                <li key={r}>{r}</li>
                            ))}
                        </ul>
                    ) : (
                        <p style={{ color: tokens.textMuted, fontSize: '0.82rem' }}>{t('viopBond.noData', 'Veri yok')}</p>
                    )}
                </AnalysisMiniCard>
            </div>
        </div>
    );
}
