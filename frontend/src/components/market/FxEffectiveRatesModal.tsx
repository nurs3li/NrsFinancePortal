import { useMemo, type CSSProperties, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { X } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import { fetchFxEffectiveRates, type FxEffectiveRateRow } from '../../services/marketDataService';
import { fxMeta } from '../../utils/instrumentMeta';
import '../../pages/Portfolio.css';

const FX_CURRENCIES = ['USD', 'EUR', 'GBP'] as const;

const CURRENCY_TO_SYMBOL: Record<string, string> = {
    USD: 'USDTRY',
    EUR: 'EURTRY',
    GBP: 'GBPTRY',
};

export type FxEffectiveRatesModalTheme = {
    bg: string;
    bgCard: string;
    border: string;
    text: string;
    textMuted: string;
};

export type FxEffectiveRatesModalProps = {
    open: boolean;
    onClose: () => void;
    tokens: FxEffectiveRatesModalTheme;
};

function latestRowFor(rows: FxEffectiveRateRow[], ccy: string): FxEffectiveRateRow | undefined {
    const subset = rows.filter((r) => r.currency === ccy);
    if (subset.length === 0) return undefined;
    return [...subset].sort((a, b) => String(b.date).localeCompare(String(a.date)))[0];
}

function fmtTry4(n: number | null | undefined, locale: string): string {
    if (n == null || !Number.isFinite(Number(n))) return '—';
    return `₺${Number(n).toLocaleString(locale, { minimumFractionDigits: 4, maximumFractionDigits: 4 })}`;
}

function currencyDisplayName(ccy: string): string {
    const sym = CURRENCY_TO_SYMBOL[ccy];
    const meta = sym ? fxMeta[sym] : undefined;
    if (meta?.baseName) return meta.baseName;
    return ccy;
}

function computeFxSpread(row: FxEffectiveRateRow): number | null {
    if (row.fxSpread != null && Number.isFinite(row.fxSpread)) return row.fxSpread;
    if (
        row.fxBuying != null &&
        row.fxSelling != null &&
        Number.isFinite(row.fxBuying) &&
        Number.isFinite(row.fxSelling)
    ) {
        return row.fxSelling - row.fxBuying;
    }
    return null;
}

function computeCashSpread(row: FxEffectiveRateRow): number | null {
    if (row.cashSpread != null && Number.isFinite(row.cashSpread)) return row.cashSpread;
    if (
        row.cashBuying != null &&
        row.cashSelling != null &&
        Number.isFinite(row.cashBuying) &&
        Number.isFinite(row.cashSelling)
    ) {
        return row.cashSelling - row.cashBuying;
    }
    return null;
}

function computeSellDiff(row: FxEffectiveRateRow): number | null {
    if (row.cashVsFxSellingDiff != null && Number.isFinite(row.cashVsFxSellingDiff)) {
        return row.cashVsFxSellingDiff;
    }
    if (
        row.cashSelling != null &&
        row.fxSelling != null &&
        Number.isFinite(row.cashSelling) &&
        Number.isFinite(row.fxSelling)
    ) {
        return row.cashSelling - row.fxSelling;
    }
    return null;
}

function formatDateYmd(date: string | null | undefined, locale: string): string {
    if (!date) return '—';
    const d = new Date(`${date}T12:00:00`);
    if (Number.isNaN(d.getTime())) return date;
    return d.toLocaleDateString(locale, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function FxEffectiveRatesModal({ open, onClose, tokens }: FxEffectiveRatesModalProps) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-GB' : 'tr-TR';

    const q = useQuery({
        queryKey: ['market', 'fx', 'effective-rates', 'modal'],
        queryFn: async ({ signal }) => {
            const r = await fetchFxEffectiveRates(signal);
            if (r == null) {
                throw new Error('FX_EFFECTIVE_RATES_UNAVAILABLE');
            }
            return r;
        },
        enabled: open,
        staleTime: 60_000,
    });

    const tableRows = useMemo(() => {
        const rates = q.data?.rates ?? [];
        return FX_CURRENCIES.map((ccy) => latestRowFor(rates, ccy)).filter((r): r is FxEffectiveRateRow => r != null);
    }, [q.data?.rates]);

    const cardSurface: CSSProperties = {
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        color: tokens.text,
    };

    if (!open) return null;

    return (
        <ModalBackdrop onClose={onClose}>
            <div
                className="mia-modal fx-effective-rates-modal"
                role="dialog"
                aria-modal
                aria-labelledby="fx-effective-rates-modal-title"
                onMouseDown={(ev) => ev.stopPropagation()}
                style={{
                    ...cardSurface,
                    maxWidth: 1040,
                    width: 'min(96vw, 1040px)',
                    maxHeight: '90vh',
                    overflow: 'auto',
                    padding: 16,
                }}
            >
                <div
                    style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'flex-start',
                        gap: 12,
                        marginBottom: 8,
                    }}
                >
                    <div style={{ minWidth: 0 }}>
                        <h2 id="fx-effective-rates-modal-title" style={{ margin: 0, fontSize: '1.05rem', fontWeight: 700 }}>
                            {t('market.fxEffectiveModal.title', 'TCMB Döviz & Efektif Kurlar')}
                        </h2>
                        <p style={{ margin: '6px 0 0', fontSize: 12, color: tokens.textMuted, lineHeight: 1.45 }}>
                            {t(
                                'market.fxEffectiveModal.subtitle',
                                'USD, EUR ve GBP için EVDS/TCMB döviz alış-satış ve efektif alış-satış kurları.',
                            )}
                        </p>
                    </div>
                    <button type="button" className="mia-icon-btn" aria-label={t('market.drawerClose', 'Kapat')} onClick={onClose}>
                        <X size={18} />
                    </button>
                </div>

                {q.isPending ? (
                    <div className="fx-effective-rates-modal__skeleton" aria-busy="true">
                        {[0, 1, 2].map((i) => (
                            <div key={i} className="fx-effective-rates-modal__skeleton-row" />
                        ))}
                    </div>
                ) : q.isError ? (
                    <p className="fx-effective-rates-modal__message" style={{ color: tokens.textMuted }}>
                        {t('market.fxEffectiveModal.error', 'Efektif kur verileri şu anda yüklenemedi.')}
                    </p>
                ) : tableRows.length === 0 ? (
                    <p className="fx-effective-rates-modal__message" style={{ color: tokens.textMuted }}>
                        {t('market.fxEffectiveModal.empty', 'Efektif kur verisi bulunamadı.')}
                    </p>
                ) : (
                    <div className="fx-effective-rates-modal__table-wrap">
                        <table className="terminal-data-table fx-effective-rates-modal__table">
                            <thead>
                                <tr>
                                    <th scope="col">{t('market.fxEffectiveModal.colCurrency', 'Para Birimi')}</th>
                                    <th scope="col">{t('market.fxEffectiveModal.colSymbol', 'Sembol')}</th>
                                    <th scope="col">{t('market.fxEffectiveModal.colFxBuy', 'Döviz Alış')}</th>
                                    <th scope="col">{t('market.fxEffectiveModal.colFxSell', 'Döviz Satış')}</th>
                                    <th scope="col">{t('market.fxEffectiveModal.colCashBuy', 'Efektif Alış')}</th>
                                    <th scope="col">{t('market.fxEffectiveModal.colCashSell', 'Efektif Satış')}</th>
                                    <th scope="col">{t('market.fxEffectiveModal.colFxSpread', 'Döviz Makası')}</th>
                                    <th scope="col">{t('market.fxEffectiveModal.colCashSpread', 'Efektif Makası')}</th>
                                    <th scope="col">{t('market.fxEffectiveModal.colSellDiff', 'Efektif Satış Farkı')}</th>
                                    <th scope="col">{t('market.fxEffectiveModal.colDate', 'Tarih')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {tableRows.map((row) => {
                                    const ccy = row.currency;
                                    const symbol = CURRENCY_TO_SYMBOL[ccy] ?? `${ccy}TRY`;
                                    return (
                                        <tr key={ccy}>
                                            <td>{currencyDisplayName(ccy)}</td>
                                            <td>
                                                <strong>{symbol}</strong>
                                            </td>
                                            <td>{fmtTry4(row.fxBuying, locale)}</td>
                                            <td>{fmtTry4(row.fxSelling, locale)}</td>
                                            <td>{fmtTry4(row.cashBuying, locale)}</td>
                                            <td>{fmtTry4(row.cashSelling, locale)}</td>
                                            <td>{fmtTry4(computeFxSpread(row), locale)}</td>
                                            <td>{fmtTry4(computeCashSpread(row), locale)}</td>
                                            <td>{fmtTry4(computeSellDiff(row), locale)}</td>
                                            <td>{formatDateYmd(row.date, locale)}</td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}

                <div
                    className="fx-effective-rates-modal__explain"
                    style={{
                        marginTop: 14,
                        padding: '10px 12px',
                        borderRadius: 8,
                        border: `1px solid ${tokens.border}`,
                        background: tokens.bg,
                        fontSize: 11,
                        color: tokens.textMuted,
                        lineHeight: 1.45,
                    }}
                >
                    {t(
                        'market.fxEffectiveModal.explain',
                        'Efektif kur, fiziki banknot işlemlerinde kullanılan alış/satış kurudur. Döviz kuru ise standart TCMB döviz alış/satış kurudur.',
                    )}
                </div>
            </div>
        </ModalBackdrop>
    );
}

function ModalBackdrop({ children, onClose }: { children: ReactNode; onClose: () => void }) {
    return (
        <div className="mia-modal-backdrop" role="presentation" onMouseDown={onClose}>
            {children}
        </div>
    );
}
