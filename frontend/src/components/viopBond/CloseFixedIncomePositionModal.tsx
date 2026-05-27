import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { X } from 'lucide-react';
import { readApiError } from '../../api/envelope';
import { useLanguage } from '../../i18n/LanguageContext';
import { resolveBondHistoricalPrice } from '../../services/bondPositionApi';
import type {
    BondCloseType,
    ManualBondPosition,
    ManualBondPositionSellPayload,
} from '../../types/bondPosition';
import type { PositionHistoricalPriceResolve } from '../../types/historicalPriceResolve';
import { bondTypeLabel, couponFrequencyLabel } from './bondPositionLabels';
import { computeBondClose, holdingDaysBetween } from './viopBondCalculations';
import { fmtLocaleDecimal, fmtMoney, fmtPct, parseLocaleDecimal } from './formatViopBond';
import { HistoricalPriceResolveBanner } from './HistoricalPriceResolveBanner';

type Props = {
    open: boolean;
    position: ManualBondPosition | null;
    onClose: () => void;
    onSubmit: (payload: ManualBondPositionSellPayload) => Promise<void>;
};

function formatMoneyCurrency(
    value: number | null | undefined,
    currency: string,
    locale: string,
    tryEquivalent?: number | null,
): string {
    const base = fmtMoney(value, locale);
    if (!base || base === '—') return base;
    const cur = currency?.toUpperCase() ?? 'TRY';
    const labeled = `${base} ${cur}`;
    if (tryEquivalent != null && Number.isFinite(tryEquivalent) && cur !== 'TRY') {
        return `${labeled} (${fmtMoney(tryEquivalent, locale)} TRY)`;
    }
    return labeled;
}

function maturityStatusLabel(
    position: ManualBondPosition,
    closeDate: string,
    t: (k: string, d: string) => string,
): string {
    if (!position.maturityDate) return '—';
    const mat = new Date(`${position.maturityDate}T12:00:00`);
    const close = new Date(`${closeDate}T12:00:00`);
    if (Number.isNaN(mat.getTime()) || Number.isNaN(close.getTime())) return '—';
    if (close >= mat) return t('viopBond.maturityReached', 'Vade doldu');
    const days = Math.round((mat.getTime() - close.getTime()) / 86_400_000);
    return t('viopBond.maturityDaysLeft', '{days} gün kaldı').replace('{days}', String(days));
}

export function CloseFixedIncomePositionModal({ open, position, onClose, onSubmit }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    const [closeType, setCloseType] = useState<BondCloseType>('SALE');
    const [closeDate, setCloseDate] = useState(() => new Date().toISOString().slice(0, 10));
    const [nominalInput, setNominalInput] = useState('');
    const [closePriceInput, setClosePriceInput] = useState('');
    const [couponCollectedInput, setCouponCollectedInput] = useState('');
    const [feeInput, setFeeInput] = useState('');
    const [note, setNote] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [priceResolve, setPriceResolve] = useState<PositionHistoricalPriceResolve | null>(null);
    const [resolvingPrice, setResolvingPrice] = useState(false);

    const currency = position?.currency ?? 'TRY';
    const showDirtyPriceHint = currency !== 'TRY';

    useEffect(() => {
        if (!open || !position) return;
        setCloseType('SALE');
        setCloseDate(new Date().toISOString().slice(0, 10));
        setNominalInput(fmtLocaleDecimal(position.nominalValue, locale, 0));
        const defaultPrice = position.currentPrice ?? position.buyPrice;
        setClosePriceInput(defaultPrice != null ? fmtLocaleDecimal(defaultPrice, locale) : '');
        setCouponCollectedInput('');
        setFeeInput('');
        setNote(position.note ?? '');
        setError(null);
        setPriceResolve(null);
    }, [open, position, locale]);

    useEffect(() => {
        if (!open || !position) return;
        if (closeType === 'REDEMPTION') {
            setClosePriceInput(fmtLocaleDecimal(100, locale));
        } else if (position.currentPrice != null) {
            setClosePriceInput(fmtLocaleDecimal(position.currentPrice, locale));
        }
    }, [closeType, open, position, locale]);

    const closePriceNum = parseLocaleDecimal(closePriceInput, locale);
    const nominalNum = parseLocaleDecimal(nominalInput, locale);
    const couponNum = parseLocaleDecimal(couponCollectedInput, locale) ?? 0;
    const feeNum = parseLocaleDecimal(feeInput, locale) ?? 0;

    const live = useMemo(() => {
        if (!position || closePriceNum == null || closePriceNum <= 0 || nominalNum == null || nominalNum <= 0) {
            return computeBondClose({
                nominalValue: position?.nominalValue ?? 0,
                buyPrice: position?.buyPrice ?? 0,
                closePrice: 0,
                collectedCouponAmount: couponNum,
                fee: feeNum,
            });
        }
        return computeBondClose({
            nominalValue: nominalNum,
            buyPrice: position.buyPrice,
            closePrice: closePriceNum,
            collectedCouponAmount: couponNum,
            fee: feeNum,
        });
    }, [position, closePriceNum, nominalNum, couponNum, feeNum]);

    const holdingDays = position ? holdingDaysBetween(position.buyDate, closeDate) : null;

    const formatOnBlur = (value: string, setter: (v: string) => void, fractionDigits = 2) => {
        const n = parseLocaleDecimal(value, locale);
        if (n != null) setter(fmtLocaleDecimal(n, locale, fractionDigits));
    };

    const handleResolveClosePrice = async () => {
        if (!position?.symbol.trim() || !closeDate || closeType === 'REDEMPTION') return;
        setResolvingPrice(true);
        setPriceResolve(null);
        try {
            const res = await resolveBondHistoricalPrice(position.symbol, closeDate);
            setPriceResolve(res);
            if (res.matchType !== 'NOT_FOUND' && res.price != null) {
                setClosePriceInput(fmtLocaleDecimal(res.price, locale));
            }
        } catch {
            setPriceResolve({
                symbol: position.symbol,
                requestedDate: closeDate,
                matchType: 'NOT_FOUND',
                price: null,
                source: null,
                message: t('viopBond.priceResolveFailed', 'Fiyat bulunamadı'),
            });
        } finally {
            setResolvingPrice(false);
        }
    };

    const useCurrentPrice = () => {
        const cp = position?.currentPrice;
        if (closeType !== 'SALE' || cp == null || !Number.isFinite(cp) || cp <= 0) return;
        setClosePriceInput(fmtLocaleDecimal(cp, locale));
        setPriceResolve(null);
    };

    const canSave =
        closePriceNum != null &&
        closePriceNum > 0 &&
        nominalNum != null &&
        nominalNum > 0 &&
        closeDate.length > 0;

    if (!open || !position) return null;

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        if (!canSave || closePriceNum == null) return;
        setSaving(true);
        setError(null);
        try {
            await onSubmit({
                sellPrice: closePriceNum,
                sellDate: closeDate,
                closeType,
                collectedCouponAmount: couponNum > 0 ? couponNum : undefined,
                fee: feeNum > 0 ? feeNum : undefined,
                note: note.trim() || undefined,
            });
            onClose();
        } catch (err) {
            setError(readApiError(err).message || t('viopBond.saveFailed', 'Kayıt başarısız'));
        } finally {
            setSaving(false);
        }
    };

    const priceLabel = showDirtyPriceHint
        ? `${t('viopBond.colClosePrice', 'Kapanış fiyatı')} (${t('viopBond.priceDirtyLabel', 'Kirli fiyat')}, 100 nominal)`
        : `${t('viopBond.colClosePrice', 'Kapanış fiyatı')} (100 nominal üzerinden)`;

    return (
        <div className="vb-modal-backdrop" onClick={onClose} role="presentation">
            <div className="vb-modal vb-modal--wide pf-card-premium" onClick={(e) => e.stopPropagation()} role="dialog">
                <div className="vb-modal-header">
                    <h3>{t('viopBond.closeBondTitle', 'Sabit Getirili Pozisyonu Kapat')}</h3>
                    <button type="button" className="vb-icon-btn" onClick={onClose} aria-label={t('viopBond.close', 'Kapat')}>
                        <X size={18} />
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="vb-modal-body">
                    <div className="vb-modal-grid">
                        <section className="vb-modal-section">
                            <h4>{t('viopBond.sectionInstrument', 'Enstrüman Bilgileri')}</h4>
                            <div className="vb-readonly-grid">
                                <div>
                                    <span>{t('viopBond.colInstrument', 'Enstrüman')}</span>
                                    <strong>{position.symbol}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colType', 'Tür')}</span>
                                    <strong>{bondTypeLabel(position.bondType, t)}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colCurrency', 'Döviz')}</span>
                                    <strong>{currency}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.bondNominal', 'Nominal değer')}</span>
                                    <strong>{formatMoneyCurrency(position.nominalValue, currency, locale)}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colBuyPrice', 'Alış fiyatı')}</span>
                                    <strong>{fmtLocaleDecimal(position.buyPrice, locale)}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colCurrentPrice', 'Güncel fiyat')}</span>
                                    <strong>
                                        {fmtLocaleDecimal(position.currentPrice, locale)}
                                        {showDirtyPriceHint ? (
                                            <span className="vb-tag-default"> ({t('viopBond.priceDirtyLabel', 'Kirli fiyat')})</span>
                                        ) : null}
                                    </strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colMaturity', 'Vade')}</span>
                                    <strong>{position.maturityDate ?? '—'}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colCouponRate', 'Kupon oranı')}</span>
                                    <strong>
                                        {position.couponRate != null ? `${position.couponRate}%` : '—'}
                                    </strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colCouponFreq', 'Kupon sıklığı')}</span>
                                    <strong>
                                        {couponFrequencyLabel(position.couponFrequency ?? 'NONE', t)}
                                    </strong>
                                </div>
                            </div>
                        </section>

                        <section className="vb-modal-section">
                            <h4>{t('viopBond.sectionCloseInfo', 'Kapanış Bilgileri')}</h4>
                            <div className="vb-form-fields">
                                <label className="vb-field vb-field--full">
                                    {t('viopBond.colCloseType', 'Kapanış tipi')}
                                    <select
                                        value={closeType}
                                        onChange={(e) => setCloseType(e.target.value as BondCloseType)}
                                    >
                                        <option value="SALE">{t('viopBond.closeTypeSale', 'Satış')}</option>
                                        <option value="REDEMPTION">
                                            {t('viopBond.closeTypeRedemption', 'Vade sonu / itfa')}
                                        </option>
                                    </select>
                                </label>
                                <label className="vb-field">
                                    {t('viopBond.colCloseDate', 'Kapanış tarihi')}
                                    <input
                                        type="date"
                                        required
                                        value={closeDate}
                                        onChange={(e) => {
                                            setCloseDate(e.target.value);
                                            setPriceResolve(null);
                                        }}
                                    />
                                </label>
                                <label className="vb-field">
                                    {t('viopBond.bondNominal', 'Nominal değer')}
                                    <input
                                        type="text"
                                        inputMode="decimal"
                                        required
                                        value={nominalInput}
                                        onChange={(e) => setNominalInput(e.target.value)}
                                        onBlur={() => formatOnBlur(nominalInput, setNominalInput, 0)}
                                    />
                                </label>
                                <label className="vb-field vb-field--full">
                                    {priceLabel}
                                    <div className="vb-field-actions">
                                        <input
                                            type="text"
                                            inputMode="decimal"
                                            required
                                            value={closePriceInput}
                                            disabled={closeType === 'REDEMPTION'}
                                            onChange={(e) => {
                                                setClosePriceInput(e.target.value);
                                                setPriceResolve(null);
                                            }}
                                            onBlur={() => formatOnBlur(closePriceInput, setClosePriceInput)}
                                        />
                                        {closeType === 'SALE' ? (
                                            <>
                                                <button
                                                    type="button"
                                                    className="pf-dash-btn pf-dash-btn--compact"
                                                    disabled={resolvingPrice || !position.symbol.trim() || !closeDate}
                                                    onClick={() => void handleResolveClosePrice()}
                                                >
                                                    {t('viopBond.resolveClosePrice', 'Kapanış fiyatını otomatik bul')}
                                                </button>
                                                <button
                                                    type="button"
                                                    className="pf-dash-btn pf-dash-btn--compact"
                                                    disabled={position.currentPrice == null}
                                                    onClick={useCurrentPrice}
                                                >
                                                    {t('viopBond.useCurrentAsClose', 'Güncel fiyatı kullan')}
                                                </button>
                                            </>
                                        ) : null}
                                    </div>
                                    {closeType === 'SALE' ? (
                                        <HistoricalPriceResolveBanner
                                            resolve={priceResolve}
                                            resolving={resolvingPrice}
                                            locale={locale}
                                            t={t}
                                        />
                                    ) : null}
                                </label>
                                <label className="vb-field vb-field--full">
                                    {t('viopBond.colCouponCollected', 'Tahsil edilen kupon toplamı')}
                                    <input
                                        type="text"
                                        inputMode="decimal"
                                        value={couponCollectedInput}
                                        onChange={(e) => setCouponCollectedInput(e.target.value)}
                                        onBlur={() => formatOnBlur(couponCollectedInput, setCouponCollectedInput)}
                                        placeholder="0"
                                    />
                                </label>
                                <label className="vb-field">
                                    {t('viopBond.colFee', 'Masraf')}
                                    <input
                                        type="text"
                                        inputMode="decimal"
                                        value={feeInput}
                                        onChange={(e) => setFeeInput(e.target.value)}
                                        onBlur={() => formatOnBlur(feeInput, setFeeInput)}
                                        placeholder="0"
                                    />
                                </label>
                                <label className="vb-field vb-field--full">
                                    {t('viopBond.colNote', 'Not')}
                                    <textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} />
                                </label>
                            </div>
                        </section>

                        <section className="vb-modal-section vb-live-summary">
                            <h4>{t('viopBond.sectionCloseSummary', 'Canlı Kapanış Özeti')}</h4>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colBuyValue', 'Alış değeri')}</span>
                                <strong>{formatMoneyCurrency(live.buyValue, currency, locale)}</strong>
                            </div>
                            <div className="vb-live-row">
                                <span>
                                    {closeType === 'REDEMPTION'
                                        ? t('viopBond.colRedemptionValue', 'İtfa değeri')
                                        : t('viopBond.colSaleValue', 'Satış değeri')}
                                </span>
                                <strong>{formatMoneyCurrency(live.closeValue, currency, locale)}</strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colCouponIncome', 'Kupon geliri')}</span>
                                <strong>{formatMoneyCurrency(live.couponIncome, currency, locale)}</strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colFee', 'Masraf')}</span>
                                <strong>{formatMoneyCurrency(live.fee, currency, locale)}</strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colTotalPnl', 'Toplam K/Z')}</span>
                                <strong className={live.totalPnl != null && live.totalPnl >= 0 ? 'vb-pos' : 'vb-neg'}>
                                    {formatMoneyCurrency(live.totalPnl, currency, locale)}
                                </strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colReturnPct', 'Getiri %')}</span>
                                <strong>{fmtPct(live.returnPercent, locale)}</strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colHoldingPeriod', 'Elde tutma süresi')}</span>
                                <strong>
                                    {holdingDays != null
                                        ? t('viopBond.holdingDays', '{days} gün').replace('{days}', String(holdingDays))
                                        : '—'}
                                </strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colMaturityStatus', 'Vade durumu')}</span>
                                <strong>{maturityStatusLabel(position, closeDate, t)}</strong>
                            </div>
                        </section>
                    </div>

                    {error ? <p className="vb-error">{error}</p> : null}
                    <div className="vb-modal-footer">
                        <button type="button" className="pf-dash-btn" onClick={onClose}>
                            {t('viopBond.cancel', 'İptal')}
                        </button>
                        <button
                            type="submit"
                            className="pf-dash-btn pf-dash-btn--primary"
                            disabled={saving || !canSave}
                        >
                            {saving ? t('viopBond.saving', 'Kaydediliyor…') : t('viopBond.confirmClose', 'Kapatmayı Onayla')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
