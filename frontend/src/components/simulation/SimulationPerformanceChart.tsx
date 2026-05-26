import {
    Area,
    CartesianGrid,
    ComposedChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';
import { useLanguage } from '../../i18n/LanguageContext';
import { CHART_PALETTE } from './constants';
import { SimTermInfo } from './SimTermInfo';
import { SimDualMoney } from './SimDualMoney';
import { simCurrencySymbol } from './simCurrency';
import type { ChartMetricMode, SimDisplayCurrency, SimulationResultItem } from './types';
import {
    buildSimulationChartData,
    chartTooltipValue,
    chartYAxisFormatter,
    seriesChartKey,
    seriesDisplayName,
} from './utils';
import { SimulationLegendChips } from './SimulationLegendChips';

type TooltipPayload = { name?: string; value?: number; color?: string; dataKey?: string };

function estimatedValueFromMetric(
    metricMode: ChartMetricMode,
    metricValue: number | undefined,
    initialAmount: number,
): number | null {
    if (metricValue == null || !Number.isFinite(metricValue) || initialAmount <= 0) {
        return null;
    }
    if (metricMode === 'VALUE_TRY') {
        return metricValue;
    }
    if (metricMode === 'PNL_TRY') {
        return initialAmount + metricValue;
    }
    return initialAmount * (1 + metricValue / 100);
}

type SimulationPerformanceChartProps = {
    visibleResults: SimulationResultItem[];
    allResults: SimulationResultItem[];
    displayCurrency: SimDisplayCurrency;
    usdTryRate?: number | null;
    metricMode: ChartMetricMode;
    onMetricModeChange: (m: ChartMetricMode) => void;
    onlyVisibleOnChart: boolean;
    onOnlyVisibleChange: (v: boolean) => void;
    borderColor: string;
    textMuted: string;
    textColor: string;
    onToggleVisible: (id: string) => void;
};

function SimPerformanceTooltip({
    active,
    label,
    payload,
    metricMode,
    visibleResults,
    locale,
    displayCurrency,
    usdTryRate,
}: {
    active?: boolean;
    label?: string;
    payload?: TooltipPayload[];
    metricMode: ChartMetricMode;
    visibleResults: SimulationResultItem[];
    locale: string;
    displayCurrency: SimDisplayCurrency;
    usdTryRate?: number | null;
}) {
    if (!active || !payload?.length) return null;

    const keyToRes = new Map(visibleResults.map((r) => [seriesChartKey(r), r]));

    return (
        <div className="sim-chart-tooltip">
            <div className="sim-chart-tooltip-title">{label}</div>
            {payload.map((e, i) => {
                const res = e.dataKey ? keyToRes.get(String(e.dataKey)) : undefined;
                const estValue = res ? estimatedValueFromMetric(metricMode, e.value, res.initialAmount) : null;
                const estPnl = res && estValue != null ? estValue - res.initialAmount : null;
                return (
                    <div key={i} className="sim-chart-tooltip-block">
                        <div className="sim-chart-tooltip-row">
                            <span style={{ color: e.color }}>{e.name}</span>
                            <span>{chartTooltipValue(metricMode, locale, e.value, displayCurrency)}</span>
                        </div>
                        {res && estValue != null ? (
                            <div className="sim-chart-tooltip-meta">
                                {estValue != null ? (
                                    <div>
                                        <SimDualMoney locale={locale} value={estValue} res={res} usdTryRate={usdTryRate} />
                                    </div>
                                ) : null}
                                {estPnl != null ? (
                                    <div className={estPnl >= 0 ? 'sim-pnl-pos' : 'sim-pnl-neg'}>
                                        PNL: {estPnl >= 0 ? '+' : ''}
                                        <SimDualMoney locale={locale} value={estPnl} res={res} usdTryRate={usdTryRate} />
                                    </div>
                                ) : null}
                            </div>
                        ) : null}
                    </div>
                );
            })}
        </div>
    );
}

export function SimulationPerformanceChart({
    visibleResults,
    allResults,
    displayCurrency,
    usdTryRate,
    metricMode,
    onMetricModeChange,
    onlyVisibleOnChart,
    onOnlyVisibleChange,
    borderColor,
    textMuted,
    textColor: _textColor,
    onToggleVisible,
}: SimulationPerformanceChartProps) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    const chartData = buildSimulationChartData(visibleResults, metricMode);
    const legendItems = onlyVisibleOnChart ? visibleResults : allResults.filter((r) => r.visible);

    const sym = simCurrencySymbol(displayCurrency);
    const modes: { id: ChartMetricMode; label: string }[] = [
        { id: 'RETURN_PCT', label: t('simulation.chartModeReturn', 'Getiri %') },
        {
            id: 'VALUE_TRY',
            label: t('simulation.chartModeValue', 'Bugünkü Değer {sym}').replace('{sym}', sym),
        },
        {
            id: 'PNL_TRY',
            label: t('simulation.chartModePnl', 'Kazanç/Kayıp {sym}').replace('{sym}', sym),
        },
        { id: 'NORMALIZE', label: t('simulation.chartModeNormalize', 'Normalize') },
    ];

    return (
        <section className="card-premium sim-chart-card">
            <div className="sim-chart-card__head">
                <div>
                    <h2 className="sim-section-title sim-chart-card__title">
                        {t('simulation.performanceChart', 'Karşılaştırmalı Performans')}
                        <SimTermInfo
                            termKey="cumulative-return"
                            title={t('simulation.termCumulativeTitle', 'Kümülatif getiri')}
                            body={t(
                                'simulation.termCumulativeBody',
                                'Yatırımın başlangıç tarihinden itibaren toplam yüzde değişimini gösterir. Farklı varlıkları aynı ölçekte karşılaştırmak için kullanılır.',
                            )}
                            mutedColor={textMuted}
                        />
                    </h2>
                    <p className="sim-lead sim-chart-card__sub" style={{ color: textMuted }}>
                        {t(
                            'simulation.chartSubtitle',
                            'Seçilen başlangıç tarihinden bugüne kadar varlıkların göreli performansı.',
                        )}
                    </p>
                </div>
                <div className="sim-chart-modes" role="tablist">
                    {modes.map((m) => (
                        <button
                            key={m.id}
                            type="button"
                            role="tab"
                            aria-selected={metricMode === m.id}
                            className={`sim-chart-mode-btn${metricMode === m.id ? ' sim-chart-mode-btn--on' : ''}`}
                            onClick={() => onMetricModeChange(m.id)}
                        >
                            {m.label}
                        </button>
                    ))}
                </div>
            </div>
            <label className="sim-chart-only-visible">
                <input type="checkbox" checked={onlyVisibleOnChart} onChange={(e) => onOnlyVisibleChange(e.target.checked)} />
                <span style={{ color: textMuted }}>{t('simulation.onlyVisibleLegend', 'Sadece görünenler')}</span>
            </label>
            <SimulationLegendChips
                items={legendItems}
                locale={locale}
                displayCurrency={displayCurrency}
                usdTryRate={usdTryRate}
                onToggleVisible={onToggleVisible}
            />
            {visibleResults.length === 0 || chartData.length === 0 ? (
                <p className="sim-lead sim-chart-empty" style={{ color: textMuted }}>
                    {allResults.length === 0
                        ? t('simulation.chartEmptyNoSim', 'İlk simülasyonu oluşturduğunda grafik burada görünecek.')
                        : t('simulation.chartEmptyHidden', 'Grafikte göstermek için en az bir simülasyonu görünür yap.')}
                </p>
            ) : (
                <div className="sim-chart-surface" style={{ width: '100%', height: 380 }}>
                    <ResponsiveContainer width="100%" height="100%">
                        <ComposedChart data={chartData} margin={{ top: 10, right: 16, left: 4, bottom: 8 }}>
                            <defs>
                                {visibleResults.map((res, i) => {
                                    const c = CHART_PALETTE[i % CHART_PALETTE.length];
                                    const gid = `simFill-${res.id.replace(/[^a-zA-Z0-9_-]/g, '')}`;
                                    return (
                                        <linearGradient key={res.id} id={gid} x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="0%" stopColor={c} stopOpacity={0.35} />
                                            <stop offset="100%" stopColor={c} stopOpacity={0} />
                                        </linearGradient>
                                    );
                                })}
                            </defs>
                            <CartesianGrid strokeDasharray="3 3" stroke={borderColor} opacity={0.35} />
                            <XAxis dataKey="date" tick={{ fill: textMuted, fontSize: 11 }} stroke={borderColor} />
                            <YAxis
                                tick={{ fill: textMuted, fontSize: 11 }}
                                stroke={borderColor}
                                tickFormatter={(v) => chartYAxisFormatter(metricMode, locale, Number(v), displayCurrency)}
                            />
                            <Tooltip
                                content={
                                    <SimPerformanceTooltip
                                        metricMode={metricMode}
                                        visibleResults={visibleResults}
                                        locale={locale}
                                        displayCurrency={displayCurrency}
                                        usdTryRate={usdTryRate}
                                    />
                                }
                            />
                            {visibleResults.map((res, i) => {
                                const key = seriesChartKey(res);
                                const color = CHART_PALETTE[i % CHART_PALETTE.length];
                                const gid = `simFill-${res.id.replace(/[^a-zA-Z0-9_-]/g, '')}`;
                                return (
                                    <Area
                                        key={res.id}
                                        type="monotone"
                                        dataKey={key}
                                        name={seriesDisplayName(res)}
                                        stroke={color}
                                        strokeWidth={3}
                                        fill={`url(#${gid})`}
                                        fillOpacity={1}
                                        connectNulls={false}
                                        dot={false}
                                        activeDot={{ r: 5, strokeWidth: 2, stroke: color, fill: '#0a0f1a' }}
                                        isAnimationActive={false}
                                    />
                                );
                            })}
                        </ComposedChart>
                    </ResponsiveContainer>
                </div>
            )}
        </section>
    );
}
