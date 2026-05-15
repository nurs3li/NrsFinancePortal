import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useLanguage } from '../../i18n/LanguageContext';
import { fetchFxEffectiveRates, type FxEffectiveRateResponse, type FxEffectiveRateRow } from '../../services/marketDataService';

export type FxEffectiveRatesComparisonTheme = {
    bg: string;
    bgCard: string;
    border: string;
    text: string;
    textMuted: string;
};

export type FxEffectiveRatesComparisonSectionProps = {
    tokens: FxEffectiveRatesComparisonTheme;
    /** Örn. USDTRY — varsayılan para birimi çıkarımı için */
    selectedSymbol?: string | null;
    /** Test / Storybook: dışarıdan veri */
    rates?: FxEffectiveRateRow[];
    isLoading?: boolean;
    isError?: boolean;
    /** Yalnızca Döviz sekmesinde true; query bu durumda çalışır */
    enabled?: boolean;
};

function inferCurrencyFromSymbol(symbol: string | null | undefined): string {
    const s = String(symbol ?? '').toUpperCase();
    if (s.startsWith('USD')) return 'USD';
    if (s.startsWith('EUR')) return 'EUR';
    if (s.startsWith('GBP')) return 'GBP';
    if (s.startsWith('AZN')) return 'AZN';
    return 'USD';
}

function latestRowFor(rows: FxEffectiveRateRow[], ccy: string): FxEffectiveRateRow | undefined {
    const subset = rows.filter((r) => r.currency === ccy);
    if (subset.length === 0) return undefined;
    return [...subset].sort((a, b) => String(b.date).localeCompare(String(a.date)))[0];
}

function fmtTry4(n: number | null | undefined, locale: string): string {
    if (n == null || !Number.isFinite(Number(n))) return '—';
    return `₺${Number(n).toLocaleString(locale, { minimumFractionDigits: 4, maximumFractionDigits: 4 })}`;
}

function fmtSignedDiff(n: number | null | undefined, locale: string): string {
    if (n == null || !Number.isFinite(Number(n))) return '—';
    const v = Number(n);
    const sign = v > 0 ? '+' : v < 0 ? '-' : '';
    const abs = Math.abs(v).toLocaleString(locale, { minimumFractionDigits: 4, maximumFractionDigits: 4 });
    return `${sign}${abs}`;
}

export function FxEffectiveRatesComparisonSection({
    tokens,
    selectedSymbol,
    rates: ratesProp,
    isLoading: loadingProp,
    isError: errorProp,
    enabled = true,
}: FxEffectiveRatesComparisonSectionProps) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-GB' : 'tr-TR';

    const external = ratesProp != null;
    const q = useQuery<FxEffectiveRateResponse>({
        queryKey: ['market', 'fx', 'effective-rates'],
        queryFn: async ({ signal }) => {
            const r = await fetchFxEffectiveRates(signal);
            return r ?? { generatedAt: '', source: 'EVDS', frequency: 'DAILY', rates: [], notes: [] };
        },
        enabled: enabled && !external,
        staleTime: 60_000,
    });

    const rates = external ? (ratesProp ?? []) : (q.data?.rates ?? []);
    const isLoading = external ? Boolean(loadingProp) : q.isPending;
    const isError = external ? Boolean(errorProp) : q.isError;

    const [selectedCcy, setSelectedCcy] = useState(() => inferCurrencyFromSymbol(selectedSymbol));

    useEffect(() => {
        setSelectedCcy(inferCurrencyFromSymbol(selectedSymbol));
    }, [selectedSymbol]);

    const chipCurrencies = useMemo(() => {
        const fromData = new Set(rates.map((r) => r.currency).filter(Boolean));
        const ordered: string[] = ['USD', 'EUR', 'GBP'];
        if (fromData.has('AZN')) ordered.push('AZN');
        for (const c of [...fromData].sort()) {
            if (!ordered.includes(c)) ordered.push(c);
        }
        return ordered;
    }, [rates]);

    const latest = useMemo(() => latestRowFor(rates, selectedCcy), [rates, selectedCcy]);

    const fxMakas =
        latest?.fxSpread != null && Number.isFinite(latest.fxSpread)
            ? latest.fxSpread
            : latest != null &&
                latest.fxBuying != null &&
                latest.fxSelling != null &&
                Number.isFinite(latest.fxBuying) &&
                Number.isFinite(latest.fxSelling)
              ? latest.fxSelling - latest.fxBuying
              : null;

    const cashMakas =
        latest?.cashSpread != null && Number.isFinite(latest.cashSpread)
            ? latest.cashSpread
            : latest != null &&
                latest.cashBuying != null &&
                latest.cashSelling != null &&
                Number.isFinite(latest.cashBuying) &&
                Number.isFinite(latest.cashSelling)
              ? latest.cashSelling - latest.cashBuying
              : null;

    const sellDiff =
        latest?.cashVsFxSellingDiff != null && Number.isFinite(latest.cashVsFxSellingDiff)
            ? latest.cashVsFxSellingDiff
            : latest != null &&
                latest.cashSelling != null &&
                latest.fxSelling != null &&
                Number.isFinite(latest.cashSelling) &&
                Number.isFinite(latest.fxSelling)
              ? latest.cashSelling - latest.fxSelling
              : null;

    const buyDiff =
        latest?.cashVsFxBuyingDiff != null && Number.isFinite(latest.cashVsFxBuyingDiff)
            ? latest.cashVsFxBuyingDiff
            : latest != null &&
                latest.cashBuying != null &&
                latest.fxBuying != null &&
                Number.isFinite(latest.cashBuying) &&
                Number.isFinite(latest.fxBuying)
              ? latest.cashBuying - latest.fxBuying
              : null;

    const spreadDiff =
        cashMakas != null && fxMakas != null && Number.isFinite(cashMakas) && Number.isFinite(fxMakas)
            ? cashMakas - fxMakas
            : null;

    const showEmpty =
        !isLoading &&
        (isError || rates.length === 0 || !latest || (latest.fxBuying == null && latest.cashBuying == null && latest.fxSelling == null && latest.cashSelling == null));

    const diffClass = (n: number | null | undefined) => {
        if (n == null || !Number.isFinite(n)) return undefined;
        if (n > 0) return 'terminal-pct-pos';
        if (n < 0) return 'terminal-pct-neg';
        return undefined;
    };

    return (
        <div
            className="terminal-card"
            style={{
                marginTop: 14,
                border: `1px solid ${tokens.border}`,
                background: tokens.bgCard,
                padding: '14px 16px',
            }}
        >
            <h3 style={{ margin: 0, fontSize: 15, fontWeight: 700, color: tokens.text }}>
                {t('market.fxEffective.sectionTitle', 'TCMB Döviz / Efektif Kur Karşılaştırması')}
            </h3>
            <p style={{ margin: '8px 0 14px', fontSize: 11, color: tokens.textMuted, lineHeight: 1.45, maxWidth: 920 }}>
                {t(
                    'market.fxEffective.sectionIntro',
                    'Döviz kuru kaydi/dijital işlemleri, efektif kur fiziki nakit döviz işlemlerini temsil eder. Bu veriler banka kuru değil, TCMB/EVDS gösterge kurudur.',
                )}
            </p>

            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
                {chipCurrencies.map((c) => (
                    <button
                        key={c}
                        type="button"
                        className={`terminal-btn${selectedCcy === c ? ' active' : ''}`}
                        onClick={() => setSelectedCcy(c)}
                    >
                        {c}
                    </button>
                ))}
            </div>

            {isLoading ? (
                <div style={{ height: 120, borderRadius: 6, background: 'rgba(148,163,184,0.12)' }} aria-busy />
            ) : showEmpty ? (
                <p style={{ margin: 0, fontSize: 12, color: tokens.textMuted }}>
                    {t('market.fxEffective.empty', 'TCMB/EVDS efektif kur verisi bekleniyor.')}
                </p>
            ) : (
                <>
                    <div style={{ overflowX: 'auto' }}>
                        <table className="terminal-data-table" style={{ fontSize: 12, minWidth: 480 }}>
                            <thead>
                                <tr>
                                    <th scope="col" />
                                    <th scope="col">{t('market.fxEffective.colBuy', 'Alış')}</th>
                                    <th scope="col">{t('market.fxEffective.colSell', 'Satış')}</th>
                                    <th scope="col">{t('market.fxEffective.colSpread', 'Makas')}</th>
                                    <th scope="col">{t('market.fxEffective.colNote', 'Açıklama')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td>
                                        <strong>{t('market.fxEffective.rowFx', 'Döviz')}</strong>
                                    </td>
                                    <td>{fmtTry4(latest?.fxBuying ?? null, locale)}</td>
                                    <td>{fmtTry4(latest?.fxSelling ?? null, locale)}</td>
                                    <td>{fmtTry4(fxMakas, locale)}</td>
                                    <td style={{ color: tokens.textMuted, fontSize: 11 }}>
                                        {t('market.fxEffective.rowFxNote', 'Kaydi/dijital kur')}
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <strong>{t('market.fxEffective.rowCash', 'Efektif')}</strong>
                                    </td>
                                    <td>{fmtTry4(latest?.cashBuying ?? null, locale)}</td>
                                    <td>{fmtTry4(latest?.cashSelling ?? null, locale)}</td>
                                    <td>{fmtTry4(cashMakas, locale)}</td>
                                    <td style={{ color: tokens.textMuted, fontSize: 11 }}>
                                        {t('market.fxEffective.rowCashNote', 'Fiziki nakit kur')}
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>

                    <div
                        style={{
                            display: 'grid',
                            gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
                            gap: 10,
                            marginTop: 14,
                        }}
                    >
                        <div
                            style={{
                                border: `1px solid ${tokens.border}`,
                                borderRadius: 8,
                                padding: 10,
                                background: tokens.bg,
                            }}
                        >
                            <div style={{ fontSize: 10, color: tokens.textMuted, marginBottom: 4 }}>
                                {t('market.fxEffective.boxSellDiff', 'Efektif satış farkı')}
                            </div>
                            <div className={diffClass(sellDiff)} style={{ fontSize: 14, fontWeight: 700 }}>
                                {fmtSignedDiff(sellDiff, locale)}
                            </div>
                            <div style={{ fontSize: 9, color: tokens.textMuted, marginTop: 4, lineHeight: 1.35 }}>
                                {t('market.fxEffective.boxSellDiffHint', 'Efektif satış − döviz satış')}
                            </div>
                        </div>
                        <div
                            style={{
                                border: `1px solid ${tokens.border}`,
                                borderRadius: 8,
                                padding: 10,
                                background: tokens.bg,
                            }}
                        >
                            <div style={{ fontSize: 10, color: tokens.textMuted, marginBottom: 4 }}>
                                {t('market.fxEffective.boxBuyDiff', 'Efektif alış farkı')}
                            </div>
                            <div className={diffClass(buyDiff)} style={{ fontSize: 14, fontWeight: 700 }}>
                                {fmtSignedDiff(buyDiff, locale)}
                            </div>
                            <div style={{ fontSize: 9, color: tokens.textMuted, marginTop: 4, lineHeight: 1.35 }}>
                                {t('market.fxEffective.boxBuyDiffHint', 'Efektif alış − döviz alış')}
                            </div>
                        </div>
                        <div
                            style={{
                                border: `1px solid ${tokens.border}`,
                                borderRadius: 8,
                                padding: 10,
                                background: tokens.bg,
                            }}
                        >
                            <div style={{ fontSize: 10, color: tokens.textMuted, marginBottom: 4 }}>
                                {t('market.fxEffective.boxSpreadDiff', 'Makas farkı')}
                            </div>
                            <div className={diffClass(spreadDiff)} style={{ fontSize: 14, fontWeight: 700 }}>
                                {fmtSignedDiff(spreadDiff, locale)}
                            </div>
                            <div style={{ fontSize: 9, color: tokens.textMuted, marginTop: 4, lineHeight: 1.35 }}>
                                {t('market.fxEffective.boxSpreadDiffHint', 'Efektif makas − döviz makası')}
                            </div>
                        </div>
                        <div
                            style={{
                                border: `1px solid ${tokens.border}`,
                                borderRadius: 8,
                                padding: 10,
                                background: tokens.bg,
                            }}
                        >
                            <div style={{ fontSize: 10, color: tokens.textMuted, marginBottom: 4 }}>
                                {t('market.fxEffective.lastDate', 'Son gözlem tarihi')}
                            </div>
                            <div style={{ fontSize: 14, fontWeight: 700, color: tokens.text }}>
                                {latest?.date
                                    ? new Date(`${latest.date}T12:00:00`).toLocaleDateString(locale, {
                                          year: 'numeric',
                                          month: 'short',
                                          day: 'numeric',
                                      })
                                    : '—'}
                            </div>
                        </div>
                    </div>

                    <p
                        style={{
                            margin: '14px 0 0',
                            fontSize: 10,
                            color: tokens.textMuted,
                            lineHeight: 1.45,
                            borderTop: `1px solid ${tokens.border}`,
                            paddingTop: 10,
                        }}
                    >
                        {t(
                            'market.fxEffective.bankDisclaimer',
                            'Bu değerler banka kuru değildir. TCMB/EVDS gösterge kurlarıdır; işlem anındaki piyasa fiyatından farklılık gösterebilir.',
                        )}
                    </p>
                </>
            )}
        </div>
    );
}
