import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { X } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import { resolveViopHistoricalPrice } from '../../services/viopPositionApi';
import type { ManualViopPosition, ManualViopPositionClosePayload, ViopCloseReason } from '../../types/viopPosition';
import type { PositionHistoricalPriceResolve } from '../../types/historicalPriceResolve';
import { viopCategoryLabel, viopDirectionLabel } from './viopPositionLabels';
import { resolveViopExpiry } from './viopContractMeta';
import { computeViopClose } from './viopBondCalculations';
import { fmtLocaleDecimal, fmtMoney, fmtPct, parseLocaleDecimal } from './formatViopBond';
import { HistoricalPriceResolveBanner } from './HistoricalPriceResolveBanner';

type Props = {
    open: boolean;
    position: ManualViopPosition | null;
    onClose: () => void;
    onSubmit: (payload: ManualViopPositionClosePayload) => Promise<void>;
};

const CLOSE_REASONS: ViopCloseReason[] = ['MANUAL_CLOSE', 'TAKE_PROFIT', 'STOP_LOSS', 'EXPIRY'];

function closeReasonLabel(reason: ViopCloseReason, t: (k: string, d: string) => string): string {
    switch (reason) {
        case 'TAKE_PROFIT':
            return t('viopBond.closeReasonTakeProfit', 'Kar al');
        case 'STOP_LOSS':
            return t('viopBond.closeReasonStopLoss', 'Zarar kes');
        case 'EXPIRY':
            return t('viopBond.closeReasonExpiry', 'Vade sonu');
        case 'MANUAL_CLOSE':
        default:
            return t('viopBond.closeReasonManual', 'Manuel kapatma');
    }
}

export function CloseViopPositionModal({ open, position, onClose, onSubmit }: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    const [closeDate, setCloseDate] = useState(() => new Date().toISOString().slice(0, 10));
    const [closePriceInput, setClosePriceInput] = useState('');
    const [closeReason, setCloseReason] = useState<ViopCloseReason>('MANUAL_CLOSE');
    const [feeInput, setFeeInput] = useState('');
    const [note, setNote] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [priceResolve, setPriceResolve] = useState<PositionHistoricalPriceResolve | null>(null);
    const [resolvingPrice, setResolvingPrice] = useState(false);

    useEffect(() => {
        if (!open || !position) return;
        const defaultPrice = position.currentPrice ?? position.entryPrice;
        setCloseDate(new Date().toISOString().slice(0, 10));
        setClosePriceInput(
            defaultPrice != null ? fmtLocaleDecimal(defaultPrice, locale) : '',
        );
        setCloseReason('MANUAL_CLOSE');
        setFeeInput('');
        setNote(position.note ?? '');
        setError(null);
        setPriceResolve(null);
    }, [open, position, locale]);

    const closePriceNum = parseLocaleDecimal(closePriceInput, locale);
    const feeNum = parseLocaleDecimal(feeInput, locale) ?? 0;

    const expiry = useMemo(
        () =>
            position
                ? resolveViopExpiry(position.symbol, locale, { expiryDate: position.expiryDate })
                : null,
        [position, locale],
    );
    const cat = position?.viopCategory ?? 'EQUITY';

    const live = useMemo(() => {
        if (!position || closePriceNum == null || closePriceNum <= 0) {
            return computeViopClose({
                direction: position?.direction ?? 'LONG',
                entryPrice: position?.entryPrice ?? 0,
                closePrice: 0,
                contractMultiplier: position?.contractMultiplier ?? 1,
                contractCount: position?.contractCount ?? 0,
                initialMargin: position?.initialMargin ?? 0,
                fee: feeNum,
            });
        }
        return computeViopClose({
            direction: position.direction,
            entryPrice: position.entryPrice,
            closePrice: closePriceNum,
            contractMultiplier: position.contractMultiplier,
            contractCount: position.contractCount,
            initialMargin: position.initialMargin ?? 0,
            fee: feeNum,
        });
    }, [position, closePriceNum, feeNum]);

    const formatOnBlur = (value: string, setter: (v: string) => void) => {
        const n = parseLocaleDecimal(value, locale);
        if (n != null) setter(fmtLocaleDecimal(n, locale));
    };

    const handleResolveClosePrice = async () => {
        if (!position?.symbol.trim() || !closeDate) return;
        setResolvingPrice(true);
        setPriceResolve(null);
        try {
            const res = await resolveViopHistoricalPrice(position.symbol, closeDate);
            setPriceResolve(res);
            if (res.found && res.price != null) {
                setClosePriceInput(fmtLocaleDecimal(res.price, locale));
            }
        } catch {
            setPriceResolve({ found: false, price: null, source: null, message: t('viopBond.priceResolveFailed', 'Fiyat bulunamadı') });
        } finally {
            setResolvingPrice(false);
        }
    };

    const useCurrentPrice = () => {
        const cp = position?.currentPrice;
        if (cp != null && Number.isFinite(cp) && cp > 0) {
            setClosePriceInput(fmtLocaleDecimal(cp, locale));
            setPriceResolve(null);
        }
    };

    const canSave =
        closePriceNum != null &&
        closePriceNum > 0 &&
        closeDate.length > 0 &&
        (position?.contractCount ?? 0) > 0;

    if (!open || !position) return null;

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        if (!canSave || closePriceNum == null) return;
        setSaving(true);
        setError(null);
        try {
            await onSubmit({
                closePrice: closePriceNum,
                closeDate,
                fee: feeNum > 0 ? feeNum : undefined,
                closeReason,
                note: note.trim() || undefined,
            });
            onClose();
        } catch (err) {
            setError(err instanceof Error ? err.message : t('viopBond.saveFailed', 'Kayıt başarısız'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="vb-modal-backdrop" onClick={onClose} role="presentation">
            <div className="vb-modal vb-modal--wide pf-card-premium" onClick={(e) => e.stopPropagation()} role="dialog">
                <div className="vb-modal-header">
                    <h3>{t('viopBond.closeViopTitle', 'VİOP Pozisyonunu Kapat')}</h3>
                    <button type="button" className="vb-icon-btn" onClick={onClose} aria-label={t('viopBond.close', 'Kapat')}>
                        <X size={18} />
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="vb-modal-body">
                    <div className="vb-modal-grid">
                        <section className="vb-modal-section">
                            <h4>{t('viopBond.sectionContract', 'Kontrat Bilgileri')}</h4>
                            <div className="vb-readonly-grid">
                                <div>
                                    <span>{t('viopBond.colContract', 'Kontrat')}</span>
                                    <strong>{position.symbol}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colDisplayName', 'Görünen ad')}</span>
                                    <strong>{position.displayName ?? position.symbol}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colViopType', 'Tür')}</span>
                                    <strong>{viopCategoryLabel(cat, t)}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colUnderlying', 'Dayanak')}</span>
                                    <strong>{position.underlyingSymbol ?? '—'}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colDirection', 'Yön')}</span>
                                    <strong>{viopDirectionLabel(position.direction, t)}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colCount', 'Adet')}</span>
                                    <strong>{fmtLocaleDecimal(position.contractCount, locale, 0)}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colEntryPrice', 'Giriş fiyatı')}</span>
                                    <strong>{fmtMoney(position.entryPrice, locale)}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colCurrentPrice', 'Güncel fiyat')}</span>
                                    <strong>{fmtMoney(position.currentPrice, locale)}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colExpiry', 'Vade')}</span>
                                    <strong>{expiry?.displayLong ?? expiry?.displayShort ?? '—'}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colMultiplier', 'Kontrat çarpanı')}</span>
                                    <strong>{fmtLocaleDecimal(position.contractMultiplier, locale, 0)}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colMargin', 'Başlangıç teminatı')}</span>
                                    <strong>{fmtMoney(position.initialMargin, locale)}</strong>
                                </div>
                            </div>
                        </section>

                        <section className="vb-modal-section">
                            <h4>{t('viopBond.sectionCloseInfo', 'Kapanış Bilgileri')}</h4>
                            <div className="vb-form-fields">
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
                                <label className="vb-field vb-field--full">
                                    {t('viopBond.colClosePrice', 'Kapanış fiyatı')}
                                    <div className="vb-field-actions">
                                        <input
                                            type="text"
                                            inputMode="decimal"
                                            required
                                            value={closePriceInput}
                                            onChange={(e) => {
                                                setClosePriceInput(e.target.value);
                                                setPriceResolve(null);
                                            }}
                                            onBlur={() => formatOnBlur(closePriceInput, setClosePriceInput)}
                                        />
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
                                    </div>
                                    <HistoricalPriceResolveBanner
                                        resolve={priceResolve}
                                        resolving={resolvingPrice}
                                        locale={locale}
                                        t={t}
                                    />
                                </label>
                                <label className="vb-field vb-field--full">
                                    {t('viopBond.colCloseReason', 'Kapanış nedeni')}
                                    <select
                                        value={closeReason}
                                        onChange={(e) => setCloseReason(e.target.value as ViopCloseReason)}
                                    >
                                        {CLOSE_REASONS.map((r) => (
                                            <option key={r} value={r}>
                                                {closeReasonLabel(r, t)}
                                            </option>
                                        ))}
                                    </select>
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
                                <span>{t('viopBond.colEntryValue', 'Giriş değeri')}</span>
                                <strong>{fmtMoney(live.entryValue, locale)}</strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colCloseValue', 'Kapanış değeri')}</span>
                                <strong>{fmtMoney(live.closeValue, locale)}</strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colGrossPnl', 'Brüt K/Z')}</span>
                                <strong className={live.grossPnl != null && live.grossPnl >= 0 ? 'vb-pos' : 'vb-neg'}>
                                    {fmtMoney(live.grossPnl, locale)}
                                </strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colFee', 'Masraf')}</span>
                                <strong>{fmtMoney(live.fee, locale)}</strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colNetPnl', 'Net K/Z')}</span>
                                <strong className={live.netPnl != null && live.netPnl >= 0 ? 'vb-pos' : 'vb-neg'}>
                                    {fmtMoney(live.netPnl, locale)}
                                </strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colReturnPct', 'Getiri %')}</span>
                                <strong>{fmtPct(live.returnPercent, locale)}</strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colMargin', 'Teminat')}</span>
                                <strong>{fmtMoney(live.margin, locale)}</strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colExposure', 'Risk maruziyeti')}</span>
                                <strong>{fmtMoney(live.riskExposure, locale)}</strong>
                            </div>
                            <p className="vb-live-hint">
                                {t(
                                    'viopBond.marginNotPnlHint',
                                    'Teminat, pozisyon için ayrılan güvence tutarıdır; doğrudan kâr/zarar değildir.',
                                )}
                            </p>
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
