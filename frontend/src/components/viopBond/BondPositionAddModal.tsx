import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { X } from 'lucide-react';
import { readApiError } from '../../api/envelope';
import { useLanguage } from '../../i18n/LanguageContext';
import { resolveBondHistoricalPrice } from '../../services/bondPositionApi';
import type {
    BondType,
    CouponFrequency,
    ManualBondPosition,
    ManualBondPositionCreatePayload,
} from '../../types/bondPosition';
import type { PositionHistoricalPriceResolve } from '../../types/historicalPriceResolve';
import type { TerminalListInstrumentVm } from '../../utils/marketTerminalListVm';
import { bondCurrencyFromInstrument, bondTypeFromInstrument } from './viopBondMarket';
import { bondTypeLabel, couponFrequencyLabel } from './bondPositionLabels';
import { inferBondCouponDefaults } from './bondCouponInference';
import { computeBondPositionMetrics } from './viopBondCalculations';
import { fmtLocaleDecimal, fmtMoney, fmtPct, parseLocaleDecimal } from './formatViopBond';
import { HistoricalPriceResolveBanner } from './HistoricalPriceResolveBanner';
import { VbFieldInfo } from './VbFieldInfo';

type Props = {
    open: boolean;
    onClose: () => void;
    instrument: TerminalListInstrumentVm | null;
    manualOnly?: boolean;
    editPosition?: ManualBondPosition | null;
    onSubmit: (payload: ManualBondPositionCreatePayload) => Promise<void>;
};

const FREQUENCIES: CouponFrequency[] = ['NONE', 'ANNUAL', 'SEMI_ANNUAL', 'QUARTERLY'];
const BOND_TYPES: BondType[] = ['GOVERNMENT_BOND', 'TREASURY_BILL', 'CORPORATE_BOND'];

export function BondPositionAddModal({
    open,
    onClose,
    instrument,
    manualOnly = false,
    editPosition,
    onSubmit,
}: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    const [symbol, setSymbol] = useState('');
    const [displayName, setDisplayName] = useState('');
    const [bondType, setBondType] = useState<BondType>('GOVERNMENT_BOND');
    const [currency, setCurrency] = useState('TRY');
    const [nominalValue, setNominalValue] = useState('');
    const [buyPriceInput, setBuyPriceInput] = useState('');
    const [buyDate, setBuyDate] = useState(() => new Date().toISOString().slice(0, 10));
    const [manualCurrentPriceInput, setManualCurrentPriceInput] = useState('');
    const [maturityDate, setMaturityDate] = useState('');
    const [couponRate, setCouponRate] = useState('');
    const [couponFrequency, setCouponFrequency] = useState<CouponFrequency>('NONE');
    const [note, setNote] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [priceResolve, setPriceResolve] = useState<PositionHistoricalPriceResolve | null>(null);
    const [resolvingPrice, setResolvingPrice] = useState(false);

    const fromMarket = instrument != null && !manualOnly;
    const marketPrice = fromMarket && instrument.price > 0 ? instrument.price : editPosition?.currentPrice ?? null;
    const marketPriceValid = marketPrice != null && Number.isFinite(marketPrice) && marketPrice > 0;
    const parsedManualCurrent = parseLocaleDecimal(manualCurrentPriceInput, locale);
    const effectiveCurrent = marketPriceValid ? marketPrice! : parsedManualCurrent;
    const currentPriceMissing = !marketPriceValid;

    useEffect(() => {
        if (!open) return;
        const sym = instrument?.symbol ?? editPosition?.symbol ?? '';
        setSymbol(sym);
        setDisplayName(instrument?.displayName ?? editPosition?.displayName ?? sym);
        setBondType(editPosition?.bondType ?? bondTypeFromInstrument(sym, instrument?.displayName));
        setCurrency(editPosition?.currency ?? bondCurrencyFromInstrument(sym, instrument?.displayName));
        setNominalValue(editPosition?.nominalValue != null ? fmtLocaleDecimal(editPosition.nominalValue, locale, 0) : '');
        setBuyPriceInput(
            editPosition?.buyPrice != null ? fmtLocaleDecimal(editPosition.buyPrice, locale) : '',
        );
        setBuyDate(editPosition?.buyDate ?? new Date().toISOString().slice(0, 10));
        setManualCurrentPriceInput(
            editPosition?.currentPrice != null && currentPriceMissing
                ? fmtLocaleDecimal(editPosition.currentPrice, locale)
                : marketPriceValid
                  ? fmtLocaleDecimal(marketPrice!, locale)
                  : '',
        );
        setMaturityDate(editPosition?.maturityDate ?? instrument?.maturityDate?.slice(0, 10) ?? '');
        const couponDefaults = inferBondCouponDefaults({
            couponRate: editPosition?.couponRate ?? instrument?.couponRate,
            couponFrequencyPerYear: instrument?.couponFrequencyPerYear,
            couponFrequency: editPosition?.couponFrequency ?? undefined,
            bondType: editPosition?.bondType ?? bondTypeFromInstrument(sym, instrument?.displayName),
        });
        setCouponRate(
            couponDefaults.couponRate != null && couponDefaults.couponRate > 0
                ? String(couponDefaults.couponRate)
                : '',
        );
        setCouponFrequency(couponDefaults.couponFrequency);
        setNote(editPosition?.note ?? '');
        setError(null);
        setPriceResolve(null);
    }, [open, instrument, editPosition, fromMarket, currentPriceMissing, marketPriceValid, marketPrice, locale]);

    const buyPriceNum = parseLocaleDecimal(buyPriceInput, locale) ?? 0;
    const nominalNum = parseLocaleDecimal(nominalValue, locale) ?? 0;

    const couponRateNum = couponFrequency === 'NONE' ? 0 : couponRate ? Number(couponRate) : null;
    const couponDisabled = couponFrequency === 'NONE';

    const live = useMemo(
        () =>
            computeBondPositionMetrics({
                nominalValue: nominalNum,
                buyPrice: buyPriceNum,
                currentPrice: effectiveCurrent,
                buyDate,
                couponRate: couponRateNum,
                couponFrequency,
            }),
        [nominalNum, buyPriceNum, effectiveCurrent, buyDate, couponRateNum, couponFrequency],
    );

    const summaryReady = nominalNum > 0 && buyPriceNum > 0;

    const handleCouponFrequencyChange = (freq: CouponFrequency) => {
        setCouponFrequency(freq);
        if (freq === 'NONE') {
            setCouponRate('0');
        } else if (!couponRate || Number(couponRate) === 0) {
            const fromInst = instrument?.couponRate ?? editPosition?.couponRate;
            if (fromInst != null && fromInst > 0) setCouponRate(String(fromInst));
        }
    };

    const formatOnBlur = (value: string, setter: (v: string) => void) => {
        const n = parseLocaleDecimal(value, locale);
        if (n != null) setter(fmtLocaleDecimal(n, locale));
    };

    const handleResolveBuyPrice = async () => {
        if (!symbol.trim() || !buyDate) return;
        setResolvingPrice(true);
        setError(null);
        try {
            const r = await resolveBondHistoricalPrice(symbol.trim(), buyDate);
            setPriceResolve(r);
            if (r.matchType !== 'NOT_FOUND' && r.price != null && Number.isFinite(r.price)) {
                setBuyPriceInput(fmtLocaleDecimal(r.price, locale));
            }
        } catch (err) {
            setError(readApiError(err).message || t('viopBond.priceResolveFailed', 'Fiyat bulunamadı'));
        } finally {
            setResolvingPrice(false);
        }
    };

    if (!open) return null;

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        setSaving(true);
        setError(null);
        try {
            await onSubmit({
                symbol: symbol.trim().toUpperCase(),
                displayName: displayName.trim() || undefined,
                bondType,
                currency,
                nominalValue: nominalNum,
                buyPrice: buyPriceNum,
                buyDate,
                currentPrice: effectiveCurrent ?? undefined,
                maturityDate: maturityDate || undefined,
                couponRate: couponDisabled ? 0 : couponRateNum != null && couponRateNum > 0 ? couponRateNum : undefined,
                couponFrequency,
                note: note.trim() || undefined,
            });
            onClose();
        } catch (err) {
            setError(readApiError(err).message || t('viopBond.saveFailed', 'Kayıt başarısız'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="vb-modal-backdrop" onClick={onClose} role="presentation">
            <div className="vb-modal vb-modal--wide pf-card-premium" onClick={(e) => e.stopPropagation()} role="dialog">
                <div className="vb-modal-header">
                    <h3>
                        {editPosition
                            ? t('viopBond.editBond', 'Tahvil Güncelle')
                            : fromMarket
                              ? t('viopBond.addBondFromMarket', 'Pozisyona Ekle')
                              : t('viopBond.addBondManual', 'Manuel Tahvil Ekle')}
                    </h3>
                    <button type="button" className="vb-icon-btn" onClick={onClose}>
                        <X size={18} />
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="vb-modal-body">
                    <p className="vb-bond-hint">
                        {t('viopBond.bondPriceHint', 'Tahvil fiyatları 100 nominal değer üzerinden değerlendirilir.')}
                    </p>
                    <div className="vb-modal-grid">
                        <section className="vb-modal-section">
                            <h4>{t('viopBond.sectionInstrument', 'Enstrüman Bilgileri')}</h4>
                            {fromMarket || editPosition ? (
                                <div className="vb-readonly-grid">
                                    <div>
                                        <span>{t('viopBond.colInstrument', 'Enstrüman')}</span>
                                        <strong>{symbol}</strong>
                                    </div>
                                    <div>
                                        <span>{t('viopBond.colBondType', 'Tür')}</span>
                                        <strong>{bondTypeLabel(bondType, t)}</strong>
                                    </div>
                                    <div>
                                        <span>{t('viopBond.colCurrency', 'Döviz')}</span>
                                        <strong>{currency}</strong>
                                    </div>
                                    <div>
                                        <span>{t('viopBond.colCurrentPrice', 'Güncel fiyat')}</span>
                                        <strong>
                                            {marketPriceValid ? fmtMoney(effectiveCurrent, locale) : '—'}
                                        </strong>
                                    </div>
                                    <div>
                                        <span>{t('viopBond.colMaturity', 'Vade')}</span>
                                        <strong>{maturityDate || '—'}</strong>
                                    </div>
                                </div>
                            ) : (
                                <div className="vb-form-fields">
                                    <label className="vb-field">
                                        {t('viopBond.colInstrument', 'Enstrüman / ISIN')}
                                        <input value={symbol} onChange={(e) => setSymbol(e.target.value)} required />
                                    </label>
                                    <label className="vb-field">
                                        {t('viopBond.colDisplayName', 'Görünen ad')}
                                        <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
                                    </label>
                                    <label className="vb-field">
                                        {t('viopBond.colBondType', 'Tür')}
                                        <select value={bondType} onChange={(e) => setBondType(e.target.value as BondType)}>
                                            {BOND_TYPES.map((bt) => (
                                                <option key={bt} value={bt}>
                                                    {bondTypeLabel(bt, t)}
                                                </option>
                                            ))}
                                        </select>
                                    </label>
                                    <label className="vb-field">
                                        {t('viopBond.colCurrency', 'Para birimi')}
                                        <select value={currency} onChange={(e) => setCurrency(e.target.value)}>
                                            <option value="TRY">TRY</option>
                                            <option value="USD">USD</option>
                                            <option value="EUR">EUR</option>
                                        </select>
                                    </label>
                                </div>
                            )}
                            {currentPriceMissing ? (
                                <label className="vb-field">
                                    {t('viopBond.manualCurrentPrice', 'Manuel güncel fiyat')}
                                    <input
                                        type="text"
                                        inputMode="decimal"
                                        value={manualCurrentPriceInput}
                                        onChange={(e) => setManualCurrentPriceInput(e.target.value)}
                                        onBlur={() => formatOnBlur(manualCurrentPriceInput, setManualCurrentPriceInput)}
                                        placeholder={locale.startsWith('tr') ? '0,00' : '0.00'}
                                    />
                                </label>
                            ) : null}
                        </section>

                        <section className="vb-modal-section">
                            <h4>{t('viopBond.sectionPosition', 'Pozisyon Bilgileri')}</h4>
                            <div className="vb-form-fields">
                                <label className="vb-field">
                                    <span className="vb-field-label-row">
                                        {t('viopBond.colNominal', 'Nominal değer')}
                                        <VbFieldInfo
                                            text={t(
                                                'viopBond.tipNominal',
                                                'Tahvilin vade sonunda geri ödenecek ana para tutarıdır.',
                                            )}
                                        />
                                    </span>
                                    <input
                                        type="text"
                                        inputMode="decimal"
                                        required
                                        value={nominalValue}
                                        onChange={(e) => setNominalValue(e.target.value)}
                                        onBlur={() => formatOnBlur(nominalValue, setNominalValue)}
                                        placeholder={locale.startsWith('tr') ? '0' : '0'}
                                    />
                                </label>
                                <label className="vb-field">
                                    {t('viopBond.colBuyDate', 'Alış tarihi')}
                                    <input
                                        type="date"
                                        required
                                        value={buyDate}
                                        onChange={(e) => {
                                            setBuyDate(e.target.value);
                                            setPriceResolve(null);
                                        }}
                                    />
                                </label>
                                <label className="vb-field vb-field--full">
                                    <span className="vb-field-label-row">
                                        {t('viopBond.colBuyPrice', 'Alış fiyatı (100)')}
                                        <VbFieldInfo
                                            text={t(
                                                'viopBond.tipBuyPrice100',
                                                'Tahvil fiyatları genellikle 100 nominal değer üzerinden gösterilir. 95 iskontolu, 105 primli fiyat anlamına gelir.',
                                            )}
                                        />
                                    </span>
                                    <div className="vb-field-actions">
                                        <input
                                            type="text"
                                            inputMode="decimal"
                                            required
                                            value={buyPriceInput}
                                            onChange={(e) => {
                                                setBuyPriceInput(e.target.value);
                                                setPriceResolve(null);
                                            }}
                                            onBlur={() => formatOnBlur(buyPriceInput, setBuyPriceInput)}
                                            placeholder={locale.startsWith('tr') ? '0,00' : '0.00'}
                                        />
                                        <button
                                            type="button"
                                            className="pf-dash-btn pf-dash-btn--compact"
                                            disabled={resolvingPrice || !symbol.trim() || !buyDate}
                                            onClick={() => void handleResolveBuyPrice()}
                                        >
                                            {t('viopBond.resolveBuyPrice', 'Alış fiyatını otomatik bul')}
                                        </button>
                                    </div>
                                    <HistoricalPriceResolveBanner
                                        resolve={priceResolve}
                                        resolving={resolvingPrice}
                                        locale={locale}
                                        t={t}
                                    />
                                </label>
                                {!fromMarket && !editPosition ? (
                                    <label className="vb-field">
                                        {t('viopBond.colMaturity', 'Vade tarihi')}
                                        <input type="date" value={maturityDate} onChange={(e) => setMaturityDate(e.target.value)} />
                                    </label>
                                ) : null}
                                <label className="vb-field">
                                    <span className="vb-field-label-row">
                                        {t('viopBond.colCouponRate', 'Kupon oranı %')}
                                        <VbFieldInfo
                                            text={t(
                                                'viopBond.tipCouponRate',
                                                'Tahvilin yıllık faiz ödeme oranıdır. Kupon oranı piyasa getirisiyle aynı şey değildir.',
                                            )}
                                        />
                                    </span>
                                    <input
                                        type="number"
                                        min="0"
                                        step="any"
                                        value={couponRate}
                                        disabled={couponDisabled}
                                        onChange={(e) => setCouponRate(e.target.value)}
                                    />
                                </label>
                                <label className="vb-field">
                                    <span className="vb-field-label-row">
                                        {t('viopBond.colCouponFreq', 'Kupon sıklığı')}
                                        <VbFieldInfo
                                            text={t(
                                                'viopBond.tipCouponFreq',
                                                'Tahvilin yılda kaç kez kupon ödemesi yaptığını gösterir. EVDS’de kupon oranı bulunan DİBS’ler için varsayılan 6 ayda birdir.',
                                            )}
                                        />
                                    </span>
                                    <select
                                        value={couponFrequency}
                                        onChange={(e) => handleCouponFrequencyChange(e.target.value as CouponFrequency)}
                                    >
                                        {FREQUENCIES.map((f) => (
                                            <option key={f} value={f}>
                                                {couponFrequencyLabel(f, t)}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                                <label className="vb-field vb-field--full">
                                    {t('viopBond.colNote', 'Not')}
                                    <textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} />
                                </label>
                            </div>
                        </section>

                        <section className="vb-modal-section vb-live-summary">
                            <h4>{t('viopBond.sectionLive', 'Canlı Hesap Özeti')}</h4>
                            {!summaryReady ? (
                                <p className="vb-muted-sm">
                                    {t(
                                        'viopBond.summaryEmpty',
                                        'Nominal değer ve alış fiyatı girildiğinde tahvil hesap özeti burada görünecek.',
                                    )}
                                </p>
                            ) : (
                                <>
                                    <div className="vb-live-row">
                                        <span>{t('viopBond.colBuyValue', 'Alış değeri')}</span>
                                        <strong>{fmtMoney(live.buyValue, locale)}</strong>
                                    </div>
                                    <div className="vb-live-row">
                                        <span>{t('viopBond.colValue', 'Güncel değer')}</span>
                                        <strong>{fmtMoney(live.currentValue, locale)}</strong>
                                    </div>
                                    <div className="vb-live-row">
                                        <span className="vb-field-label-row">
                                            {t('viopBond.colPricePnl', 'Fiyat K/Z')}
                                            <VbFieldInfo
                                                text={t(
                                                    'viopBond.tipPricePnl',
                                                    'Alış fiyatı ile güncel fiyat arasındaki değer değişimidir. Kupon geliri bu kaleme dahil değildir.',
                                                )}
                                            />
                                        </span>
                                        <strong
                                            className={
                                                live.pricePnl != null && live.pricePnl >= 0 ? 'vb-pos' : 'vb-neg'
                                            }
                                        >
                                            {fmtMoney(live.pricePnl, locale)}
                                        </strong>
                                    </div>
                                    <div className="vb-live-row">
                                        <span className="vb-field-label-row">
                                            {t('viopBond.colCollectedCoupon', 'Tahmini tahsil edilen kupon')}
                                            <VbFieldInfo
                                                text={t(
                                                    'viopBond.tipCollectedCoupon',
                                                    'Alış tarihinden bugüne tamamlanan kupon dönemleri için yaklaşık hesap. Gerçek ödeme takvimi yoksa tahminidir.',
                                                )}
                                            />
                                        </span>
                                        <strong>{fmtMoney(live.collectedCoupon, locale)}</strong>
                                    </div>
                                    {live.estimatedAccruedCoupon != null && live.estimatedAccruedCoupon > 0 ? (
                                        <div className="vb-live-row">
                                            <span>{t('viopBond.colAccruedCoupon', 'Tahmini birikmiş kupon')}</span>
                                            <strong>{fmtMoney(live.estimatedAccruedCoupon, locale)}</strong>
                                        </div>
                                    ) : null}
                                    {live.periodicCoupon != null && couponFrequency !== 'NONE' ? (
                                        <div className="vb-live-row vb-live-meta">
                                            <span>
                                                {t('viopBond.bondCoupon', 'Yıllık kupon')}:{' '}
                                                {fmtMoney(live.annualCoupon, locale)} ·{' '}
                                                {couponFrequency === 'SEMI_ANNUAL'
                                                    ? t('viopBond.semiAnnualCoupon', '6 aylık kupon')
                                                    : t('viopBond.periodicCoupon', 'Dönemsel kupon')}
                                                : {fmtMoney(live.periodicCoupon, locale)}
                                            </span>
                                        </div>
                                    ) : null}
                                    <div className="vb-live-row">
                                        <span className="vb-field-label-row">
                                            {t('viopBond.colTotalReturn', 'Toplam getiri')}
                                            <VbFieldInfo
                                                text={t(
                                                    'viopBond.tipTotalReturn',
                                                    'Fiyat K/Z ve tahsil edilen kupon gelirinin toplamıdır. Birikmiş kupon ayrıca eklenmez.',
                                                )}
                                            />
                                        </span>
                                        <strong
                                            className={
                                                live.totalReturn != null && live.totalReturn >= 0
                                                    ? 'vb-pos'
                                                    : 'vb-neg'
                                            }
                                        >
                                            {fmtMoney(live.totalReturn, locale)}
                                        </strong>
                                    </div>
                                    <div className="vb-live-row">
                                        <span>{t('viopBond.colTotalReturnPct', 'Toplam getiri %')}</span>
                                        <strong>{fmtPct(live.totalReturnPercent, locale)}</strong>
                                    </div>
                                </>
                            )}
                        </section>
                    </div>

                    {error ? <p className="vb-error">{error}</p> : null}
                    <div className="vb-modal-footer">
                        <button type="button" className="pf-dash-btn" onClick={onClose}>
                            {t('viopBond.cancel', 'İptal')}
                        </button>
                        <button type="submit" className="pf-dash-btn pf-dash-btn--primary" disabled={saving}>
                            {saving ? t('viopBond.saving', 'Kaydediliyor…') : t('viopBond.save', 'Kaydet')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
