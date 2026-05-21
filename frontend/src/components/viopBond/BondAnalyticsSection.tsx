import { useMemo } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useLanguage } from '../../i18n/LanguageContext';
import { useTheme } from '../../theme/ThemeContext';
import { chartGridStroke } from '../../lib/chartTheme';
import type { BondPositionSummary } from '../../types/bondPosition';
import type { ManualBondPosition } from '../../types/bondPosition';
import { countMaturityDistribution, maturityBucketLabel } from './bondAnalysisHelpers';
import { fmtPct } from './formatViopBond';
import { AnalysisMiniCard, RankList } from './vbTabShared';

const CHART_COLORS = ['#22c55e', '#3b82f6', '#f59e0b', '#ef4444'];

type ChartMode = 'value' | 'pnl' | 'coupon';

type Props = {
    openPositions: ManualBondPosition[];
    summary: BondPositionSummary | undefined;
    chartMode: ChartMode;
    onChartModeChange: (m: ChartMode) => void;
    bondRisk: {
        byNominal?: ManualBondPosition;
        best?: ManualBondPosition;
        nearest?: ManualBondPosition;
        euroPct: number;
        euroCount: number;
    } | null;
    tokens: { border: string; bgCard: string; textMuted: string };
};

export function BondAnalyticsSection({
    openPositions,
    summary,
    chartMode,
    onChartModeChange,
    bondRisk,
    tokens,
}: Props) {
    const { t, lang } = useLanguage();
    const { theme } = useTheme();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    const barChartData = useMemo(() => {
        return openPositions.map((r) => {
            let value = 0;
            if (chartMode === 'value') value = Math.abs(Number(r.currentValue ?? 0));
            else if (chartMode === 'pnl') value = Math.abs(Number(r.pnl ?? 0));
            else value = Math.abs(Number(r.annualCoupon ?? r.couponRate ?? 0));
            return { name: r.symbol, value, signed: Number(r.pnl ?? 0) };
        });
    }, [openPositions, chartMode]);

    const maturityChart = useMemo(() => {
        const dist = countMaturityDistribution(openPositions);
        return (['0-1Y', '1-3Y', '3Y+'] as const).map((k) => ({
            name: maturityBucketLabel(k, t),
            value: dist[k],
        }));
    }, [openPositions, t]);

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

    const cardStyle = { borderColor: tokens.border, background: tokens.bgCard };

    return (
        <div className="vb-analytics-block">
            <div className="vb-compact-chart-row">
                <div className="pf-card-premium vb-chart-compact" style={cardStyle}>
                    <div className="vb-section-head">
                        <h3>{t('viopBond.positionChartTitle', 'Pozisyon analizi')}</h3>
                        <div className="vb-chart-mode-tabs">
                            {(
                                [
                                    ['value', t('viopBond.chartModeValue', 'Güncel değer')],
                                    ['pnl', t('viopBond.chartModePnl', 'Fiyat K/Z')],
                                    ['coupon', t('viopBond.chartModeCoupon', 'Kupon')],
                                ] as const
                            ).map(([id, label]) => (
                                <button
                                    key={id}
                                    type="button"
                                    className={`pf-dash-btn pf-dash-btn--compact${chartMode === id ? ' pf-dash-btn--active' : ''}`}
                                    onClick={() => onChartModeChange(id)}
                                >
                                    {label}
                                </button>
                            ))}
                        </div>
                    </div>
                    <p className="vb-chart-caption" style={{ color: tokens.textMuted }}>
                        {chartMode === 'value'
                            ? t('viopBond.chartCaptionValue', 'Açık pozisyonların güncel piyasa değeri (nominal × fiyat/100).')
                            : chartMode === 'pnl'
                              ? t('viopBond.chartCaptionPnl', 'Alış ile güncel fiyat farkından fiyat K/Z.')
                              : t('viopBond.chartCaptionCoupon', 'Tahmini yıllık kupon nakit akışı.')}
                    </p>
                    {barChartData.length === 0 ? (
                        <div className="vb-empty-state vb-empty-state--sm">
                            <p>{t('viopBond.bondChartEmpty', 'Pozisyon ekledikçe grafik dolacaktır.')}</p>
                        </div>
                    ) : (
                        <ResponsiveContainer width="100%" height={180}>
                            <BarChart data={barChartData}>
                                <CartesianGrid strokeDasharray="3 3" stroke={chartGridStroke(theme)} />
                                <XAxis dataKey="name" tick={{ fill: tokens.textMuted, fontSize: 10 }} />
                                <YAxis tick={{ fill: tokens.textMuted, fontSize: 10 }} />
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
                <aside className="pf-card-premium vb-risk-card" style={cardStyle}>
                    <h3>{t('viopBond.bondRiskSummary', 'Tahvil risk özeti')}</h3>
                    {bondRisk ? (
                        <ul className="vb-risk-list">
                            <li>
                                <span>{t('viopBond.largestNominal', 'En büyük nominal')}</span>
                                <strong>{bondRisk.byNominal?.symbol ?? '—'}</strong>
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
                                <span>{t('viopBond.eurobondRatio', 'Eurobond payı')}</span>
                                <strong>
                                    %{bondRisk.euroPct}
                                    {bondRisk.euroCount === 0 ? (
                                        <span className="vb-detail-muted">
                                            {' '}
                                            — {t('viopBond.eurobondEmptyHint', 'Henüz eurobond pozisyonu yok')}
                                        </span>
                                    ) : null}
                                </strong>
                            </li>
                        </ul>
                    ) : (
                        <p style={{ color: tokens.textMuted, fontSize: '0.85rem' }}>
                            {t('viopBond.bondRiskEmpty', 'Henüz tahvil pozisyonu yok.')}
                        </p>
                    )}
                </aside>
            </div>
            <div className="vb-analysis-grid">
                <AnalysisMiniCard title={t('viopBond.maturityDist', 'Vade dağılımı')} tokens={tokens}>
                    {openPositions.length === 0 ? (
                        <p style={{ color: tokens.textMuted, fontSize: '0.82rem' }}>{t('viopBond.noData', 'Veri yok')}</p>
                    ) : (
                        <ResponsiveContainer width="100%" height={140}>
                            <BarChart data={maturityChart}>
                                <XAxis dataKey="name" tick={{ fill: tokens.textMuted, fontSize: 10 }} />
                                <YAxis allowDecimals={false} tick={{ fill: tokens.textMuted, fontSize: 10 }} />
                                <Tooltip />
                                <Bar dataKey="value" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                            </BarChart>
                        </ResponsiveContainer>
                    )}
                </AnalysisMiniCard>
                <AnalysisMiniCard title={t('viopBond.chartCurrency', 'Para birimi')} tokens={tokens}>
                    {currencyChart.length === 0 ? (
                        <p style={{ color: tokens.textMuted, fontSize: '0.82rem' }}>{t('viopBond.noData', 'Veri yok')}</p>
                    ) : (
                        <ResponsiveContainer width="100%" height={140}>
                            <PieChart>
                                <Pie data={currencyChart} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={50} label>
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
                        empty={t('viopBond.noExpiring', '30 gün içinde vade yok')}
                        muted={tokens.textMuted}
                    />
                </AnalysisMiniCard>
            </div>
        </div>
    );
}
