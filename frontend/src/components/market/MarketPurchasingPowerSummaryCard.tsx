import { memo } from 'react';
import { useLanguage } from '../../i18n/LanguageContext';
import type { PurchasingPowerData } from '../../hooks/usePurchasingPowerData';

type Props = {
    symbol: string;
    displayName?: string;
    unitLabel: string;
    data: PurchasingPowerData;
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

function MarketPurchasingPowerSummaryCardImpl({ symbol, displayName, unitLabel, data, tokens }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const { snapshot, loading, anchorDate } = data;

    const label = displayName?.trim() ? `${symbol} · ${displayName.trim()}` : symbol;
    const anchorLabel = anchorDate ? fmtAnchorDate(locale, anchorDate) : '—';

    return (
        <div
            className="terminal-card terminal-pp-summary-card"
            style={{ borderColor: tokens.border, background: tokens.bgCard }}
        >
            <h3 className="terminal-macro-card__title" style={{ color: tokens.text }}>
                {t('market.ppSummaryTitleUnit', '{unit} — TL karşılaştırma özeti').replace('{unit}', unitLabel)}
            </h3>
            <p className="terminal-macro-card__muted" style={{ color: tokens.textMuted, marginBottom: 4 }}>
                {label}
            </p>
            <p className="terminal-pp-summary-anchor" style={{ color: tokens.textMuted, marginBottom: 12 }}>
                {t('market.ppAnchorFromChart', 'Grafik referans tarihi')}:{' '}
                <strong style={{ color: tokens.text, fontWeight: 600 }}>{anchorLabel}</strong>
            </p>

            {loading ? (
                <p style={{ color: tokens.textMuted, fontSize: 12 }}>{t('market.loading', 'Yükleniyor...')}</p>
            ) : !snapshot ? (
                <p style={{ color: tokens.textMuted, fontSize: 12 }}>
                    {t('market.ppSummaryEmpty', 'Seçilen tarih için yeterli fiyat veya makro veri yok.')}
                </p>
            ) : (
                <dl className="terminal-pp-summary-grid">
                    <div>
                        <dt style={{ color: tokens.textMuted }}>
                            {t('market.ppUnitCostAtDate', 'Seçilen tarihte {unit} (TRY)').replace('{unit}', unitLabel)}
                        </dt>
                        <dd style={{ color: tokens.text }}>{fmtTry(locale, snapshot.lotCostTry)}</dd>
                    </div>
                    <div>
                        <dt style={{ color: tokens.textMuted }}>
                            {t('market.ppUnitToday', 'Bugün {unit} (TRY)').replace('{unit}', unitLabel)}
                        </dt>
                        <dd style={{ color: tokens.text }}>{fmtTry(locale, snapshot.assetTryToday)}</dd>
                    </div>
                    <div>
                        <dt style={{ color: tokens.textMuted }}>
                            {t('market.ppInflationErosion', 'Enflasyon kaybı (satın alma gücü)')}
                        </dt>
                        <dd className="sim-pnl-neg">{fmtTrySigned(locale, -snapshot.inflationErosionTry)}</dd>
                    </div>
                    <div>
                        <dt style={{ color: tokens.textMuted }}>
                            {t('market.ppInflationBreakEven', 'Zarar etmemek için bugün gerekli')}
                        </dt>
                        <dd style={{ color: tokens.text }}>{fmtTry(locale, snapshot.inflationBreakEvenToday)}</dd>
                    </div>
                    <div>
                        <dt style={{ color: tokens.textMuted }}>
                            {t('market.ppDepositToday', 'Mevduata yatırılsaydı bugün')}
                        </dt>
                        <dd style={{ color: tokens.text }}>{fmtTry(locale, snapshot.depositTryToday)}</dd>
                    </div>
                    <div>
                        <dt style={{ color: tokens.textMuted }}>{t('market.ppVsDeposit', 'Varlık − mevduat farkı')}</dt>
                        <dd className={snapshot.assetVsDepositDiff >= 0 ? 'sim-pnl-pos' : 'sim-pnl-neg'}>
                            {fmtTrySigned(locale, snapshot.assetVsDepositDiff)}
                        </dd>
                    </div>
                </dl>
            )}
        </div>
    );
}

export const MarketPurchasingPowerSummaryCard = memo(MarketPurchasingPowerSummaryCardImpl);
