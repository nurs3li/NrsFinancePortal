import { useEffect, useMemo, useState } from 'react';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useLanguage } from '../../../i18n/LanguageContext';
import { useTheme } from '../../../theme/ThemeContext';
import { chartGridStroke, chartTooltipContentStyle } from '../../../lib/chartTheme';
import {
    fetchInflationCompare,
    fetchInflationLatest,
    type InflationCompareRow,
    type InflationLatestResponse,
    type MacroPanelDerivedMetrics,
} from '../../../services/marketDataService';
import { formatIndex2, formatPercent2 } from '../../../utils/macroPanelSeries';
import { useInfoTerm } from '../education/InfoTermProvider';
import type { MacroIntelligencePanel } from '../hooks/useMacroIntelligenceData';
import { MACRO_CHART_COLORS } from '../MacroTheme';
import type { MacroTheme } from '../MacroTheme';
import { ChartCard } from '../primitives/ChartCard';
import { InsightCard } from '../primitives/InsightCard';
import { KpiCard } from '../primitives/KpiCard';
import { MacroSection } from '../primitives/MacroSection';

function defaultInflationChartFromYm(): string {
    const d = new Date();
    d.setFullYear(d.getFullYear() - 3);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function defaultInflationChartToYm(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

type Props = {
    panel: MacroIntelligencePanel;
    derived: MacroPanelDerivedMetrics | undefined;
    indexChartData: { period: string; cpi?: number; ppi?: number }[];
    panelInflationFromPanel: boolean;
    panelLoading: boolean;
    locale: string;
    tokens: MacroTheme;
};

export function MacroInflationSection({
    derived,
    indexChartData,
    panelInflationFromPanel,
    panelLoading,
    locale,
    tokens,
}: Props) {
    const { t } = useLanguage();
    const { theme } = useTheme();
    const { openTerm } = useInfoTerm();
    const [latest, setLatest] = useState<InflationLatestResponse | null | undefined>(undefined);
    const [compareRows, setCompareRows] = useState<InflationCompareRow[] | null | undefined>(undefined);

    useEffect(() => {
        if (panelLoading) return;
        const ac = new AbortController();
        (async () => {
            setLatest(undefined);
            setCompareRows(undefined);
            const [l, cmp] = await Promise.all([
                fetchInflationLatest(ac.signal),
                fetchInflationCompare(defaultInflationChartFromYm(), defaultInflationChartToYm(), ac.signal),
            ]);
            if (ac.signal.aborted) return;
            setLatest(l);
            setCompareRows(cmp?.rows ?? null);
        })();
        return () => ac.abort();
    }, [panelLoading]);

    const awaiting = '—';
    const fmt = (v: number | null | undefined) =>
        v == null || !Number.isFinite(Number(v)) ? awaiting : formatPercent2(v, locale);

    const yoyChart = useMemo(() => {
        if (!compareRows?.length) return [];
        return compareRows.map((r) => ({
            period: String(r.month ?? '').slice(0, 7),
            cpiYoY: r.cpiAnnualChangePercent,
            ppiYoY: r.ppiAnnualChangePercent,
        }));
    }, [compareRows]);

    const hasIndex = indexChartData.some(
        (d) => (d.cpi != null && Number.isFinite(Number(d.cpi))) || (d.ppi != null && Number.isFinite(Number(d.ppi))),
    );
    const hasYoY = yoyChart.some(
        (d) => (d.cpiYoY != null && Number.isFinite(Number(d.cpiYoY))) || (d.ppiYoY != null && Number.isFinite(Number(d.ppiYoY))),
    );

    const kpis = panelInflationFromPanel
        ? [
              {
                  titleKey: 'macro.inflation.kpi.cpiMoM',
                  titleFb: 'TÜFE Aylık',
                  value: fmt(derived?.cpiMoM),
                  termId: 'mom' as const,
                  infoKey: 'macro.inflation.info.cpiMoM',
                  infoFb: 'TÜFE aylık değişim',
              },
              {
                  titleKey: 'macro.inflation.kpi.cpiYoY',
                  titleFb: 'TÜFE Yıllık',
                  value: fmt(derived?.cpiYoY),
                  termId: 'yoy' as const,
                  infoKey: 'macro.inflation.info.cpiYoY',
                  infoFb: 'TÜFE yıllık değişim',
              },
              {
                  titleKey: 'macro.inflation.kpi.ppiMoM',
                  titleFb: 'Yİ-ÜFE Aylık',
                  value: fmt(derived?.ppiMoM),
                  termId: 'ppi' as const,
                  infoKey: 'macro.inflation.info.ppiMoM',
                  infoFb: 'Yİ-ÜFE aylık',
              },
              {
                  titleKey: 'macro.inflation.kpi.ppiYoY',
                  titleFb: 'Yİ-ÜFE Yıllık',
                  value: fmt(derived?.ppiYoY),
                  termId: 'yoy' as const,
                  infoKey: 'macro.inflation.info.ppiYoY',
                  infoFb: 'Yİ-ÜFE yıllık',
              },
          ]
        : [
              {
                  titleKey: 'macro.inflation.kpi.cpiMoM',
                  titleFb: 'TÜFE Aylık',
                  value: formatPercent2(latest?.cpi?.monthlyChangePercent, locale),
                  termId: 'mom' as const,
                  infoKey: 'macro.inflation.info.cpiMoM',
                  infoFb: 'TÜFE aylık',
              },
              {
                  titleKey: 'macro.inflation.kpi.cpiYoY',
                  titleFb: 'TÜFE Yıllık',
                  value: formatPercent2(latest?.cpi?.annualChangePercent, locale),
                  termId: 'yoy' as const,
                  infoKey: 'macro.inflation.info.cpiYoY',
                  infoFb: 'TÜFE yıllık',
              },
              {
                  titleKey: 'macro.inflation.kpi.ppiMoM',
                  titleFb: 'Yİ-ÜFE Aylık',
                  value: formatPercent2(latest?.ppi?.monthlyChangePercent, locale),
                  termId: 'ppi' as const,
                  infoKey: 'macro.inflation.info.ppiMoM',
                  infoFb: 'Yİ-ÜFE aylık',
              },
              {
                  titleKey: 'macro.inflation.kpi.ppiYoY',
                  titleFb: 'Yİ-ÜFE Yıllık',
                  value: formatPercent2(latest?.ppi?.annualChangePercent, locale),
                  termId: 'yoy' as const,
                  infoKey: 'macro.inflation.info.ppiYoY',
                  infoFb: 'Yİ-ÜFE yıllık',
              },
          ];

    return (
        <MacroSection
            id="macro-inflation"
            title={t('macro.inflation.title', 'Enflasyon')}
            summary={t('macro.inflation.summary', 'TÜFE ve Yİ-ÜFE endeks ile enflasyon hızı.')}
            termId="cpi"
            infoAriaLabel={t('macro.inflation.infoSection', 'Enflasyon bölümü hakkında bilgi')}
            tokens={tokens}
        >
            <div className="macro-grid macro-grid--4">
                {kpis.map((k) => (
                    <KpiCard
                        key={k.titleKey}
                        title={t(k.titleKey, k.titleFb)}
                        value={k.value}
                        termId={k.termId}
                        infoAriaLabel={t(k.infoKey, k.infoFb)}
                        tokens={tokens}
                        loading={panelLoading || (!panelInflationFromPanel && latest === undefined)}
                    />
                ))}
            </div>

            <div className="macro-grid macro-grid--2">
                <ChartCard
                    title={t('macro.inflation.chart.index', 'TÜFE ve Yİ-ÜFE endeks trendi')}
                    termId="indexLevelChart"
                    infoAriaLabel={t('macro.inflation.chart.indexInfo', 'Endeks seviyesi grafiği hakkında bilgi')}
                    empty={!hasIndex}
                    tokens={tokens}
                >
                    <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={indexChartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke={chartGridStroke(theme)} />
                            <XAxis dataKey="period" tick={{ fontSize: 9, fill: tokens.textMuted }} />
                            <YAxis tick={{ fontSize: 9, fill: tokens.textMuted }} tickFormatter={(v) => formatIndex2(Number(v), locale)} width={48} />
                            <Tooltip
                                formatter={(v) => [formatIndex2(Number(v), locale), '']}
                                contentStyle={chartTooltipContentStyle(tokens)}
                            />
                            <Legend wrapperStyle={{ fontSize: 10, color: tokens.textMuted }} />
                            <Line
                                type="monotone"
                                dataKey="cpi"
                                name={t('macro.inflation.legend.cpi', 'TÜFE')}
                                stroke={MACRO_CHART_COLORS.blue}
                                dot={false}
                                strokeWidth={2}
                                connectNulls
                            />
                            <Line
                                type="monotone"
                                dataKey="ppi"
                                name={t('macro.inflation.legend.ppi', 'Yİ-ÜFE')}
                                stroke={MACRO_CHART_COLORS.violet}
                                dot={false}
                                strokeWidth={2}
                                connectNulls
                            />
                        </LineChart>
                    </ResponsiveContainer>
                </ChartCard>

                <ChartCard
                    title={t('macro.inflation.chart.yoy', 'Yıllık enflasyon trendi')}
                    termId="yoy"
                    infoAriaLabel={t('macro.inflation.chart.yoyInfo', 'Yıllık enflasyon grafiği hakkında bilgi')}
                    empty={!hasYoY}
                    emptyHint={t('macro.inflation.chart.yoyEmpty', 'Yıllık enflasyon serisi henüz yüklenemedi.')}
                    tokens={tokens}
                >
                    {hasYoY ? (
                        <ResponsiveContainer width="100%" height="100%">
                            <LineChart data={yoyChart} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke={chartGridStroke(theme)} />
                                <XAxis dataKey="period" tick={{ fontSize: 9, fill: tokens.textMuted }} />
                                <YAxis tick={{ fontSize: 9, fill: tokens.textMuted }} tickFormatter={(v) => `${v}%`} width={44} />
                                <Tooltip
                                    formatter={(v) => [`${Number(v).toFixed(2)}%`, '']}
                                    contentStyle={chartTooltipContentStyle(tokens)}
                                />
                                <Legend wrapperStyle={{ fontSize: 10, color: tokens.textMuted }} />
                                <Line
                                    type="monotone"
                                    dataKey="cpiYoY"
                                    name={t('macro.inflation.legend.cpiYoY', 'TÜFE Yıllık')}
                                    stroke={MACRO_CHART_COLORS.blue}
                                    dot={false}
                                    strokeWidth={2}
                                    connectNulls
                                />
                                <Line
                                    type="monotone"
                                    dataKey="ppiYoY"
                                    name={t('macro.inflation.legend.ppiYoY', 'Yİ-ÜFE Yıllık')}
                                    stroke={MACRO_CHART_COLORS.violet}
                                    dot={false}
                                    strokeWidth={2}
                                    connectNulls
                                />
                            </LineChart>
                        </ResponsiveContainer>
                    ) : null}
                </ChartCard>
            </div>

            <InsightCard title={t('macro.inflation.insight.title', "100 TL'nin alım gücü")} tokens={tokens} accent="violet">
                <p>{t('macro.inflation.insight.body', 'Enflasyon arttıkça aynı 100 TL daha az ürün alır.')}</p>
                <button type="button" className="macro-link-btn" onClick={() => openTerm('purchasingPower')}>
                    {t('macro.inflation.insight.link', 'Bu ne demek?')}
                </button>
            </InsightCard>
        </MacroSection>
    );
}
