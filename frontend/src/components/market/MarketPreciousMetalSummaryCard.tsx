import { memo } from 'react';
import { CircleHelp } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import type { PreciousMetalComparisonData } from '../../hooks/usePreciousMetalComparisonData';

type Props = {
    symbol: string;
    displayName?: string;
    data: PreciousMetalComparisonData;
    tokens: { bgCard: string; border: string; text: string; textMuted: string };
};

function fmtTry(locale: string, v: number) {
    return new Intl.NumberFormat(locale, { style: 'currency', currency: 'TRY', maximumFractionDigits: 0 }).format(v);
}

function fmtTrySigned(locale: string, v: number) {
    const s = v >= 0 ? '+' : '';
    return `${s}${fmtTry(locale, v)}`;
}

function fmtAnchorDate(locale: string, ymd: string) {
    if (!ymd || ymd.length < 10) return ymd;
    const [y, m, d] = ymd.split('-').map(Number);
    const dt = new Date(y, (m ?? 1) - 1, d ?? 1);
    return new Intl.DateTimeFormat(locale, { day: '2-digit', month: 'long', year: 'numeric' }).format(dt);
}

function MarketPreciousMetalSummaryCardImpl({ symbol, displayName, data, tokens }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const { comparison, loading, anchorDate, unit } = data;

    const label = displayName?.trim() ? `${symbol} · ${displayName.trim()}` : symbol;
    const anchorLabel = anchorDate ? fmtAnchorDate(locale, anchorDate) : '—';
    const title = t('market.ppSummaryTitleUnit', '{unit} — TL karşılaştırma özeti').replace('{unit}', unit.unitLabel);

    const infoText =
        unit.assetInTry
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

            {loading ? (
                <p style={{ color: tokens.textMuted, fontSize: 12 }}>{t('market.loading', 'Yükleniyor...')}</p>
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
            ) : !comparison ? (
                <p style={{ color: tokens.textMuted, fontSize: 12 }}>
                    {t('market.ppSummaryEmpty', 'Seçilen tarih için yeterli fiyat veya makro veri yok.')}
                </p>
            ) : (
                <dl className="terminal-pp-summary-grid">
                    <div>
                        <dt style={{ color: tokens.textMuted }}>
                            {t('market.ppUnitCostAtDate', 'Seçilen tarihte {unit} (TRY)').replace('{unit}', unit.unitLabel)}
                        </dt>
                        <dd style={{ color: tokens.text }}>{fmtTry(locale, comparison.initialAssetValueTRY)}</dd>
                    </div>
                    <div>
                        <dt style={{ color: tokens.textMuted }}>
                            {t('market.ppUnitToday', 'Bugün {unit} (TRY)').replace('{unit}', unit.unitLabel)}
                        </dt>
                        <dd style={{ color: tokens.text }}>{fmtTry(locale, comparison.currentAssetValueTRY)}</dd>
                    </div>
                    <div>
                        <dt style={{ color: tokens.textMuted }}>{t('market.ppNominalPnL', 'Nominal fark')}</dt>
                        <dd className={comparison.nominalPnL >= 0 ? 'sim-pnl-pos' : 'sim-pnl-neg'}>
                            {fmtTrySigned(locale, comparison.nominalPnL)}
                        </dd>
                    </div>
                    <div>
                        <dt style={{ color: tokens.textMuted }}>
                            {t('market.ppInflationRequired', 'Enflasyona göre gerekli değer')}
                        </dt>
                        <dd style={{ color: tokens.text }}>{fmtTry(locale, comparison.inflationRequiredValueTRY)}</dd>
                    </div>
                    <div>
                        <dt style={{ color: tokens.textMuted }}>
                            {t('market.ppRealPnLDiff', 'Enflasyona göre fark')}
                        </dt>
                        <dd className={comparison.realPnLDiff >= 0 ? 'sim-pnl-pos' : 'sim-pnl-neg'}>
                            {fmtTrySigned(locale, comparison.realPnLDiff)}
                        </dd>
                    </div>
                    <div>
                        <dt style={{ color: tokens.textMuted }}>
                            {comparison.depositUsesHistoricalRates
                                ? t('market.ppDepositToday', 'Mevduata yatırılsaydı bugün')
                                : t('market.ppDepositApprox', 'Yaklaşık mevduat senaryosu (bugün)')}
                        </dt>
                        <dd style={{ color: tokens.text }}>{fmtTry(locale, comparison.depositScenarioValueTRY)}</dd>
                    </div>
                    <div>
                        <dt style={{ color: tokens.textMuted }}>{t('market.ppVsDeposit', 'Varlık − mevduat farkı')}</dt>
                        <dd className={comparison.assetVsDepositDiff >= 0 ? 'sim-pnl-pos' : 'sim-pnl-neg'}>
                            {fmtTrySigned(locale, comparison.assetVsDepositDiff)}
                        </dd>
                    </div>
                </dl>
            )}
        </div>
    );
}

export const MarketPreciousMetalSummaryCard = memo(MarketPreciousMetalSummaryCardImpl);
