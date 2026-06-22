import { memo, useEffect, useMemo, useRef } from 'react';
import {
    CartesianGrid,
    Legend,
    Line,
    LineChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';
import { useLanguage } from '../../i18n/LanguageContext';
import { useTheme } from '../../theme/ThemeContext';
import { chartGridStroke, chartTooltipContentStyle } from '../../lib/chartTheme';
import type { PreciousMetalComparisonData } from '../../hooks/usePreciousMetalComparisonData';

type Props = {
    data: PreciousMetalComparisonData;
    tokens: { bgCard: string; border: string; text: string; textMuted: string };
};

function fmtTry(locale: string, v: number) {
    return new Intl.NumberFormat(locale, { style: 'currency', currency: 'TRY', maximumFractionDigits: 0 }).format(v);
}

function MarketPreciousMetalCompareChartImpl({ data, tokens }: Props) {
    const { t, lang } = useLanguage();
    const { theme } = useTheme();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const { chartSeries, comparison, loading, isRefreshing, unit } = data;

    const fmtTryStable = useMemo(() => (v: number) => fmtTry(locale, v), [locale]);
    const lastChartRef = useRef(chartSeries);
    useEffect(() => {
        if (chartSeries.length >= 2) lastChartRef.current = chartSeries;
    }, [chartSeries]);

    const seriesToRender = chartSeries.length >= 2 ? chartSeries : lastChartRef.current;
    const showChart = seriesToRender.length >= 2;
    const refreshing = Boolean(isRefreshing && showChart);

    const title = t('market.ppChartTitleUnit', '{unit} — varlık vs enflasyon vs mevduat').replace('{unit}', unit.unitLabel);
    const initialCost = comparison?.initialAssetValueTRY ?? null;

    const chartInfo = t(
        'market.ppChartInfoMetals',
        'Seçilen tarihteki {unit} TL değeri referans alınır. Mavi: varlık TL değeri; turuncu: enflasyona göre gerekli değer (TÜFE endeksi, günlük interpolasyon); yeşil: TL mevduat senaryosu.',
    ).replace('{unit}', unit.unitLabel);

    return (
        <div className="terminal-card terminal-macro-chart-card terminal-pp-compare-chart" style={{ borderColor: tokens.border }}>
            <h3 className="terminal-macro-card__title" style={{ color: tokens.text }}>
                {title}
            </h3>
            <p className="terminal-macro-card__muted" style={{ color: tokens.textMuted, margin: '0 0 6px' }}>
                {chartInfo}
            </p>
            <p className="terminal-macro-card__muted" style={{ color: tokens.textMuted, margin: '0 0 10px' }}>
                {t('market.ppChartSubtitleLot', 'Referans alım maliyeti ({cost}).').replace(
                    '{cost}',
                    initialCost != null ? fmtTryStable(initialCost) : '—',
                )}
            </p>
            {loading && !showChart ? (
                <div className="terminal-chart-empty">{t('market.loading', 'Yükleniyor...')}</div>
            ) : comparison?.missingUsdTry ? (
                <div className="terminal-chart-empty">
                    {t(
                        'market.ppMissingUsdTry',
                        'Geçmiş USD/TRY verisi olmadığı için TL karşılaştırması oluşturulamıyor.',
                    )}
                </div>
            ) : comparison?.missingCpi ? (
                <div className="terminal-chart-empty">
                    {t('market.ppMissingCpi', 'TÜFE endeks verisi olmadığı için enflasyon karşılaştırması yapılamıyor.')}
                </div>
            ) : !showChart ? (
                <div className="terminal-chart-empty">
                    {t('market.ppChartEmpty', 'Karşılaştırma için yeterli tarihsel veri yok.')}
                </div>
            ) : (
                <div
                    className={`terminal-pp-compare-chart__plot${refreshing ? ' terminal-pp-compare-chart__plot--refreshing' : ''}`}
                    aria-busy={refreshing}
                >
                <ResponsiveContainer width="100%" height={260}>
                    <LineChart data={seriesToRender} margin={{ top: 8, right: 12, left: 8, bottom: 4 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke={chartGridStroke(theme)} />
                        <XAxis dataKey="date" tick={{ fontSize: 9, fill: tokens.textMuted }} minTickGap={28} />
                        <YAxis
                            tick={{ fontSize: 9, fill: tokens.textMuted }}
                            tickFormatter={(v) => fmtTryStable(Number(v))}
                            width={72}
                        />
                        <Tooltip
                            formatter={(v, name) => [fmtTryStable(Number(v ?? 0)), String(name ?? '')]}
                            contentStyle={{ ...chartTooltipContentStyle(tokens), fontSize: 11 }}
                            isAnimationActive={false}
                        />
                        <Legend wrapperStyle={{ fontSize: 10, color: tokens.textMuted }} />
                        <Line
                            type="monotone"
                            dataKey="assetValueTRY"
                            name={t('market.ppLineAssetUnit', '{unit} varlık (TRY)').replace('{unit}', unit.unitLabel)}
                            stroke="#38bdf8"
                            dot={false}
                            strokeWidth={2.5}
                            connectNulls
                            isAnimationActive={false}
                        />
                        <Line
                            type="monotone"
                            dataKey="inflationAdjustedValueTRY"
                            name={t('market.ppLineInflationRequired', 'Enflasyona göre gerekli değer')}
                            stroke="#f59e0b"
                            dot={false}
                            strokeWidth={2}
                            connectNulls
                            isAnimationActive={false}
                        />
                        <Line
                            type="monotone"
                            dataKey="depositValueTRY"
                            name={
                                comparison?.depositUsesHistoricalRates
                                    ? t('market.ppLineDeposit', 'TL 1A mevduat senaryosu')
                                    : t('market.ppLineDepositApprox', 'Yaklaşık TL mevduat senaryosu')
                            }
                            stroke="#166534"
                            dot={false}
                            strokeWidth={2}
                            connectNulls
                            isAnimationActive={false}
                        />
                    </LineChart>
                </ResponsiveContainer>
                </div>
            )}
        </div>
    );
}

export const MarketPreciousMetalCompareChart = memo(
    MarketPreciousMetalCompareChartImpl,
    (prev, next) =>
        prev.tokens === next.tokens &&
        prev.data.chartSeries === next.data.chartSeries &&
        prev.data.comparison === next.data.comparison &&
        prev.data.loading === next.data.loading &&
        prev.data.isRefreshing === next.data.isRefreshing,
);
