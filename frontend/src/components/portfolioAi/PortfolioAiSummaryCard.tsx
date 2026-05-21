import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import type { PortfolioContextSnapshot } from '../../types/portfolioAi';
import { fmtPct, fmtTry } from './portfolioAiFormat';
import type { TranslateFn } from './portfolioAiUiTypes';

type PieSlice = { name: string; value: number; fill: string };

type Props = {
    t: TranslateFn;
    locale: string;
    ctx: PortfolioContextSnapshot | undefined;
    loading: boolean;
    pieData: PieSlice[];
};

export function PortfolioAiSummaryCard({ t, locale, ctx, loading, pieData }: Props) {
    return (
        <section className="pf-ai-dash-card">
            <h2 className="pf-ai-dash-card__title">{t('portfolioAi.summaryCardTitle', 'Portföy Özeti')}</h2>
            {loading ? (
                <p className="pf-ai-muted">{t('common.loading', 'Yükleniyor…')}</p>
            ) : (
                <>
                    <ul className="pf-ai-summary-metrics">
                        <li>
                            <span>{t('portfolioAi.miniValue', 'Portföy toplam değeri')}</span>
                            <strong>{fmtTry(ctx?.totalValueTry, locale)}</strong>
                        </li>
                        <li>
                            <span>{t('portfolioAi.miniNominal', 'Nominal getiri')}</span>
                            <strong className={ctx && (ctx.nominalReturnPct ?? 0) >= 0 ? 'pos' : 'neg'}>
                                {ctx?.nominalReturnPct != null
                                    ? `${ctx.nominalReturnPct >= 0 ? '+' : ''}${fmtPct(ctx.nominalReturnPct, locale)}`
                                    : '—'}
                            </strong>
                        </li>
                        <li>
                            <span>{t('portfolioAi.miniReal', 'Reel getiri')}</span>
                            <strong className="pos">
                                {ctx?.realReturnAvailable && ctx.realReturnPct != null
                                    ? `${ctx.realReturnPct >= 0 ? '+' : ''}${fmtPct(ctx.realReturnPct, locale)}`
                                    : '—'}
                            </strong>
                        </li>
                        <li>
                            <span>{t('portfolioAi.miniLargest', 'En büyük pozisyon')}</span>
                            <strong>
                                {ctx?.largestPositionSymbol
                                    ? `${ctx.largestPositionSymbol} (${fmtPct(ctx.largestPositionWeightPct, locale)})`
                                    : '—'}
                            </strong>
                        </li>
                        <li className="pf-ai-summary-metrics__health">
                            <span>{t('portfolioAi.miniHealth', 'Portföy sağlık skoru')}</span>
                            <div className="pf-ai-health-inline">
                                <strong>{ctx?.healthScore != null ? `${ctx.healthScore}/100` : '—'}</strong>
                                {ctx?.healthScore != null ? (
                                    <div className="pf-ai-progress pf-ai-progress--sm">
                                        <span style={{ width: `${ctx.healthScore}%` }} />
                                    </div>
                                ) : null}
                            </div>
                        </li>
                    </ul>
                    {pieData.length > 0 ? (
                        <div className="pf-ai-summary-donut">
                            <h4>{t('portfolioAi.allocationChart', 'Varlık dağılımı')}</h4>
                            <div className="pf-ai-summary-donut__chart">
                                <ResponsiveContainer width="100%" height={160}>
                                    <PieChart>
                                        <Pie
                                            data={pieData}
                                            dataKey="value"
                                            nameKey="name"
                                            innerRadius={42}
                                            outerRadius={64}
                                            paddingAngle={2}
                                        >
                                            {pieData.map((entry, i) => (
                                                <Cell key={i} fill={entry.fill} />
                                            ))}
                                        </Pie>
                                        <Tooltip
                                            formatter={(v) =>
                                                typeof v === 'number' ? `${v.toFixed(2)}%` : ''
                                            }
                                        />
                                    </PieChart>
                                </ResponsiveContainer>
                                <ul className="pf-ai-legend pf-ai-legend--compact">
                                    {pieData.map((d) => (
                                        <li key={d.name}>
                                            <i style={{ background: d.fill }} />
                                            {d.name} {d.value.toFixed(1)}%
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        </div>
                    ) : null}
                </>
            )}
        </section>
    );
}
