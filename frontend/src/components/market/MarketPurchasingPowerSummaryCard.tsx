import { memo, useEffect, useRef } from 'react';
import { AnimatedTryValue } from '../common/AnimatedTryValue';
import { useLanguage } from '../../i18n/LanguageContext';
import type { PurchasingPowerData, PurchasingPowerSnapshot } from '../../hooks/usePurchasingPowerData';

type Props = {
    symbol: string;
    displayName?: string;
    unitLabel: string;
    data: PurchasingPowerData;
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
            {Array.from({ length: 6 }, (_, i) => (
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
    snapshot,
    unitLabel,
    locale,
    tokens,
    refreshing,
    t,
}: {
    snapshot: PurchasingPowerSnapshot;
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
                    <AnimatedTryValue value={snapshot.lotCostTry} locale={locale} />
                </dd>
            </div>
            <div>
                <dt style={{ color: tokens.textMuted }}>
                    {t('market.ppUnitToday', 'Bugün {unit} (TRY)').replace('{unit}', unitLabel)}
                </dt>
                <dd style={{ color: tokens.text }}>
                    <AnimatedTryValue value={snapshot.assetTryToday} locale={locale} />
                </dd>
            </div>
            <div>
                <dt style={{ color: tokens.textMuted }}>
                    {t('market.ppInflationErosion', 'Enflasyon kaybı (satın alma gücü)')}
                </dt>
                <dd className="sim-pnl-neg">
                    <AnimatedTryValue value={-snapshot.inflationErosionTry} locale={locale} signed />
                </dd>
            </div>
            <div>
                <dt style={{ color: tokens.textMuted }}>
                    {t('market.ppInflationBreakEven', 'Zarar etmemek için bugün gerekli')}
                </dt>
                <dd style={{ color: tokens.text }}>
                    <AnimatedTryValue value={snapshot.inflationBreakEvenToday} locale={locale} />
                </dd>
            </div>
            <div>
                <dt style={{ color: tokens.textMuted }}>
                    {t('market.ppDepositToday', 'Mevduata yatırılsaydı bugün')}
                </dt>
                <dd style={{ color: tokens.text }}>
                    <AnimatedTryValue value={snapshot.depositTryToday} locale={locale} />
                </dd>
            </div>
            <div>
                <dt style={{ color: tokens.textMuted }}>{t('market.ppVsDeposit', 'Varlık − mevduat farkı')}</dt>
                <dd className={snapshot.assetVsDepositDiff >= 0 ? 'sim-pnl-pos' : 'sim-pnl-neg'}>
                    <AnimatedTryValue value={snapshot.assetVsDepositDiff} locale={locale} signed />
                </dd>
            </div>
        </dl>
    );
}

function MarketPurchasingPowerSummaryCardImpl({ symbol, displayName, unitLabel, data, tokens }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const { snapshot, loading, isRefreshing, anchorDate } = data;

    const lastSnapshotRef = useRef<PurchasingPowerSnapshot | null>(null);
    useEffect(() => {
        if (snapshot) lastSnapshotRef.current = snapshot;
    }, [snapshot]);

    const displaySnapshot = snapshot ?? lastSnapshotRef.current;
    const refreshing = Boolean(isRefreshing && displaySnapshot);

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

            {displaySnapshot ? (
                <SummaryGrid
                    snapshot={displaySnapshot}
                    unitLabel={unitLabel}
                    locale={locale}
                    tokens={tokens}
                    refreshing={refreshing}
                    t={t}
                />
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

export const MarketPurchasingPowerSummaryCard = memo(MarketPurchasingPowerSummaryCardImpl);
