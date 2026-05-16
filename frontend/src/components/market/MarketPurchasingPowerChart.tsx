import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
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
import { marketClient } from '../../api/client';
import { useLanguage } from '../../i18n/LanguageContext';
import { RANGE_TO_DAYS, type ChartRangeId } from './heatmapRange';
import { fetchInterestInflationMacroPanel } from '../../services/marketDataService';
import { buildPurchasingPowerSeries, type DateValue } from '../../utils/marketPurchasingPower';
import { selectMacroSeries } from '../../utils/macroPanelSeries';

type CandlePoint = { time: string; close: number };

type HistoryRow = { buyPrice?: number; sellPrice?: number; timestamp?: string };

type Props = {
    symbol: string;
    range: ChartRangeId;
    candles: CandlePoint[];
    anchorDate: string;
    anchorAmountTry: number;
    tokens: { bgCard: string; border: string; text: string; textMuted: string };
};

function unwrapHistory(res: unknown): HistoryRow[] {
    const r = res as { data?: { data?: HistoryRow[] } | HistoryRow[] };
    const d = r?.data;
    if (Array.isArray(d)) return d;
    if (d && typeof d === 'object' && 'data' in d && Array.isArray((d as { data: HistoryRow[] }).data)) {
        return (d as { data: HistoryRow[] }).data;
    }
    return [];
}

function mid(row: HistoryRow): number | null {
    const b = Number(row.buyPrice ?? 0);
    const s = Number(row.sellPrice ?? 0);
    if (b > 0 && s > 0) return (b + s) / 2;
    if (b > 0) return b;
    if (s > 0) return s;
    return null;
}

export function MarketPurchasingPowerChart({ symbol, range, candles, anchorDate, anchorAmountTry, tokens }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const days = RANGE_TO_DAYS[range] ?? 365;

    const { data: usdTryHistory, isLoading: loadingFx } = useQuery({
        queryKey: ['market', 'usdtry-history', days],
        queryFn: async () => {
            const res = await marketClient.get('/api/market/doviz/history', {
                params: { symbol: 'USDTRY', days },
            });
            return unwrapHistory(res);
        },
        staleTime: 120_000,
    });

    const { data: panel, isLoading: loadingMacro } = useQuery({
        queryKey: ['market', 'macro', 'interest-inflation-panel'],
        queryFn: ({ signal }) => fetchInterestInflationMacroPanel(signal),
        staleTime: 120_000,
    });

    const assetUsd: DateValue[] = useMemo(
        () =>
            candles
                .map((c) => ({ date: c.time.slice(0, 10), value: c.close }))
                .filter((x) => x.date && x.value > 0),
        [candles],
    );

    const usdTry: DateValue[] = useMemo(
        () =>
            (usdTryHistory ?? [])
                .map((h) => {
                    const m = mid(h);
                    const ts = h.timestamp ? String(h.timestamp).slice(0, 10) : '';
                    return m != null && ts ? { date: ts, value: m } : null;
                })
                .filter((x): x is DateValue => x != null),
        [usdTryHistory],
    );

    const cpi: DateValue[] = useMemo(() => {
        const s = selectMacroSeries(panel?.series, 'CPI_TR_INDEX');
        return (s?.observations ?? []).map((o) => ({
            date: String(o.date).slice(0, 10),
            value: Number(o.value),
        }));
    }, [panel?.series]);

    const deposit: DateValue[] = useMemo(() => {
        const s = selectMacroSeries(panel?.series, 'DEPOSIT_RATE_TRY_1M_WEEKLY');
        return (s?.observations ?? []).map((o) => ({
            date: String(o.date).slice(0, 10),
            value: Number(o.value),
        }));
    }, [panel?.series]);

    const series = useMemo(
        () =>
            buildPurchasingPowerSeries({
                anchorDate,
                anchorAmountTry,
                assetUsdByDate: assetUsd,
                usdTryByDate: usdTry,
                cpiIndexByDate: cpi,
                depositRatePctWeekly: deposit,
            }),
        [anchorDate, anchorAmountTry, assetUsd, usdTry, cpi, deposit],
    );

    const loading = loadingFx || loadingMacro;

    const fmtTry = (v: number) =>
        new Intl.NumberFormat(locale, { style: 'currency', currency: 'TRY', maximumFractionDigits: 0 }).format(v);

    return (
        <div className="terminal-chart-wrap terminal-pp-chart">
            <div className="terminal-chart-header">
                <div className="terminal-chart-title">
                    {t('market.ppChartTitle', '{symbol} — TL karşılaştırması').replace('{symbol}', symbol)}
                </div>
                <p className="terminal-chart-subtitle" style={{ color: tokens.textMuted }}>
                    {t(
                        'market.ppChartSubtitle',
                        'Seçilen tarihteki {amount} TL ile: varlık (USD×kur), enflasyona göre reel değer ve aynı tutarın TL 1A mevduat senaryosu.',
                    ).replace('{amount}', fmtTry(anchorAmountTry))}
                </p>
            </div>
            {loading ? (
                <div className="terminal-chart-empty">{t('market.loading', 'Yükleniyor...')}</div>
            ) : series.length < 2 ? (
                <div className="terminal-chart-empty">
                    {t('market.ppChartEmpty', 'Karşılaştırma için yeterli tarihsel veri yok.')}
                </div>
            ) : (
                <ResponsiveContainer width="100%" height={400}>
                    <LineChart data={series} margin={{ top: 8, right: 12, left: 8, bottom: 4 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.2)" />
                        <XAxis dataKey="date" tick={{ fontSize: 9, fill: tokens.textMuted }} />
                        <YAxis
                            tick={{ fontSize: 9, fill: tokens.textMuted }}
                            tickFormatter={(v) => fmtTry(Number(v))}
                            width={72}
                        />
                        <Tooltip
                            formatter={(v: number, name: string) => [fmtTry(v), name]}
                            contentStyle={{
                                background: tokens.bgCard,
                                border: `1px solid ${tokens.border}`,
                                fontSize: 11,
                            }}
                        />
                        <Legend wrapperStyle={{ fontSize: 10 }} />
                        <Line
                            type="monotone"
                            dataKey="assetTry"
                            name={t('market.ppLineAsset', 'Varlık (TRY)')}
                            stroke="#38bdf8"
                            dot={false}
                            strokeWidth={2.5}
                            connectNulls
                        />
                        <Line
                            type="monotone"
                            dataKey="inflationAdjustedTry"
                            name={t('market.ppLineInflation', 'Enflasyona göre reel')}
                            stroke="#f59e0b"
                            dot={false}
                            strokeWidth={2}
                            connectNulls
                        />
                        <Line
                            type="monotone"
                            dataKey="depositTry"
                            name={t('market.ppLineDeposit', 'TL 1A mevduat senaryosu')}
                            stroke="#22c55e"
                            dot={false}
                            strokeWidth={2}
                            connectNulls
                        />
                    </LineChart>
                </ResponsiveContainer>
            )}
        </div>
    );
}
