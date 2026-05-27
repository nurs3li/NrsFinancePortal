import { memo, useEffect, useRef } from 'react';
import { CircleHelp } from 'lucide-react';
import { AnimatedTryValue } from '../common/AnimatedTryValue';
import { useLanguage } from '../../i18n/LanguageContext';
import type { PreciousMetalComparisonData } from '../../hooks/usePreciousMetalComparisonData';
import type { AssetInflationDepositComparison } from '../../utils/preciousMetalComparison';

type Props = {
    symbol: string;
    displayName?: string;
    data: PreciousMetalComparisonData;
    tokens: { bgCard: string; border: string; text: string; textMuted: string };
};

function fmtAnchorDate(locale: string, ymd: string) {
    if (!ymd || ymd.length < 10) return ymd;
    const [y, m, d] = ymd.split('-').map(Number);
    const dt = new Date(y, (m ?? 1) - 1, d ?? 1);
    return new Intl.DateTimeFormat(locale, { day: '2-digit', month: 'long', year: 'numeric' }).format(dt);
}

function SummarySkeleton({ tokens }: { tokens: Props['tokens'] }) {
    return (
        <dl className="terminal-pp-summary-grid terminal-pp-summary-grid--pending" aria-busy="true">
            {Array.from({ length: 7 }, (_, i) => (
                <div key={i}>
                    <dt style={{ color: tokens.textMuted }}>&nbsp;</dt>
                    <dd className="terminal-pp-summary-skeleton" style={{ color: tokens.textMuted }}>
                        —
                    </dd>
                </div>
            ))}
        </dl>
    );
}

function SummaryGrid({
    comparison,
    unitLabel,
    locale,
    tokens,
    refreshing,
    t,
}: {
    comparison: AssetInflationDepositComparison;
    unitLabel: string;
    locale: string;
    tokens: Props['tokens'];
    refreshing: boolean;
    t: (key: string, fallback: string) => string;
}) {
    return (
        <dl
            className={`terminal-pp-summary-grid${refreshing ? ' terminal-pp-summary-grid--refreshing' : ''}`}
            aria-busy={refreshing}
        >
            <div>
                <dt style={{ color: tokens.textMuted }}>
                    {t('market.ppUnitCostAtDate', 'Seçilen tarihte {unit} (TRY)').replace('{unit}', unitLabel)}
                </dt>
                <dd style={{ color: tokens.text }}>
                    <AnimatedTryValue value={comparison.initialAssetValueTRY} locale={locale} />
                </dd>
            </div>
            <div>
                <dt style={{ color: tokens.textMuted }}>
                    {t('market.ppUnitToday', 'Bugün {unit} (TRY)').replace('{unit}', unitLabel)}
                </dt>
                <dd style={{ color: tokens.text }}>
                    <AnimatedTryValue value={comparison.currentAssetValueTRY} locale={locale} />
                </dd>
            </div>
            <div>
                <dt style={{ color: tokens.textMuted }}>{t('market.ppNominalPnL', 'Nominal fark')}</dt>
                <dd className={comparison.nominalPnL >= 0 ? 'sim-pnl-pos' : 'sim-pnl-neg'}>
                    <AnimatedTryValue value={comparison.nominalPnL} locale={locale} signed />
                </dd>
            </div>
            <div>
                <dt style={{ color: tokens.textMuted }}>
                    {t('market.ppInflationRequired', 'Enflasyona göre gerekli değer')}
                </dt>
                <dd style={{ color: tokens.text }}>
                    <AnimatedTryValue value={comparison.inflationRequiredValueTRY} locale={locale} />
                </dd>
            </div>
            <div>
                <dt style={{ color: tokens.textMuted }}>
                    {t('market.ppRealPnLDiff', 'Enflasyona göre fark')}
                </dt>
                <dd className={comparison.realPnLDiff >= 0 ? 'sim-pnl-pos' : 'sim-pnl-neg'}>
                    <AnimatedTryValue value={comparison.realPnLDiff} locale={locale} signed />
                </dd>
            </div>
            <div>
                <dt style={{ color: tokens.textMuted }}>
                    {comparison.depositUsesHistoricalRates
                        ? t('market.ppDepositToday', 'Mevduata yatırılsaydı bugün')
                        : t('market.ppDepositApprox', 'Yaklaşık mevduat senaryosu (bugün)')}
                </dt>
                <dd style={{ color: tokens.text }}>
                    <AnimatedTryValue value={comparison.depositScenarioValueTRY} locale={locale} />
                </dd>
            </div>
            <div>
                <dt style={{ color: tokens.textMuted }}>{t('market.ppVsDeposit', 'Varlık − mevduat farkı')}</dt>
                <dd className={comparison.assetVsDepositDiff >= 0 ? 'sim-pnl-pos' : 'sim-pnl-neg'}>
                    <AnimatedTryValue value={comparison.assetVsDepositDiff} locale={locale} signed />
                </dd>
            </div>
        </dl>
    );
}

function MarketPreciousMetalSummaryCardImpl({ symbol, displayName, data, tokens }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const { comparison, loading, isRefreshing, anchorDate, unit } = data;

    const lastComparisonRef = useRef<AssetInflationDepositComparison | null>(null);
    useEffect(() => {
        if (comparison && !comparison.missingUsdTry && !comparison.missingCpi) {
            lastComparisonRef.current = comparison;
        }
    }, [comparison]);

    const displayComparison =
        comparison && !comparison.missingUsdTry && !comparison.missingCpi
            ? comparison
            : lastComparisonRef.current;
    const refreshing = Boolean(isRefreshing && displayComparison);

    const label = displayName?.trim() ? `${symbol} · ${displayName.trim()}` : symbol;
    const anchorLabel = anchorDate ? fmtAnchorDate(locale, anchorDate) : '—';
    const title = t('market.ppSummaryTitleUnit', '{unit} — TL karşılaştırma özeti').replace('{unit}', unit.unitLabel);

    const infoText = unit.assetInTry
        ? t(
              'market.ppInfoGram',
              'Gram altın TL fiyatı, ons altın ve USD/TRY kurunun birlikte etkisiyle oluşur.',
          )
        : t(
              'market.ppInfoOunce',
              'Ons bazlı kıymetli madenler USD/ons fiyatlanır. TL karşılaştırması yapılırken ilgili tarihin USD/TRY kuru ile çevrilir.',
          );

    return (
        <div
            className="terminal-card terminal-pp-summary-card"
            style={{ borderColor: tokens.border, background: tokens.bgCard }}
        >
            <h3 className="terminal-macro-card__title" style={{ color: tokens.text }}>
                {title}
            </h3>
            <p className="terminal-macro-card__muted" style={{ color: tokens.textMuted, marginBottom: 4 }}>
                {label}
            </p>
            <p
                className="terminal-pp-summary-info"
                style={{ color: tokens.textMuted, fontSize: 11, lineHeight: 1.35, marginBottom: 8, display: 'flex', gap: 6 }}
            >
                <CircleHelp size={13} style={{ flexShrink: 0, marginTop: 1 }} aria-hidden />
                <span>{infoText}</span>
            </p>
            <p className="terminal-pp-summary-anchor" style={{ color: tokens.textMuted, marginBottom: 12 }}>
                {t('market.ppAnchorFromChart', 'Grafik referans tarihi')}:{' '}
                <strong style={{ color: tokens.text, fontWeight: 600 }}>{anchorLabel}</strong>
            </p>

            {displayComparison ? (
                <SummaryGrid
                    comparison={displayComparison}
                    unitLabel={unit.unitLabel}
                    locale={locale}
                    tokens={tokens}
                    refreshing={refreshing}
                    t={t}
                />
            ) : comparison?.missingUsdTry ? (
                <p style={{ color: tokens.textMuted, fontSize: 12 }}>
                    {t(
                        'market.ppMissingUsdTry',
                        'Geçmiş USD/TRY verisi olmadığı için TL karşılaştırması oluşturulamıyor.',
                    )}
                </p>
            ) : comparison?.missingCpi ? (
                <p style={{ color: tokens.textMuted, fontSize: 12 }}>
                    {t('market.ppMissingCpi', 'TÜFE endeks verisi olmadığı için enflasyon karşılaştırması yapılamıyor.')}
                </p>
            ) : loading ? (
                <SummarySkeleton tokens={tokens} />
            ) : (
                <p style={{ color: tokens.textMuted, fontSize: 12 }}>
                    {t('market.ppSummaryEmpty', 'Seçilen tarih için yeterli fiyat veya makro veri yok.')}
                </p>
            )}
        </div>
    );
}

export const MarketPreciousMetalSummaryCard = memo(MarketPreciousMetalSummaryCardImpl);
