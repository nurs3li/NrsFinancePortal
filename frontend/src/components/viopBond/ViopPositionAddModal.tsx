import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { X } from 'lucide-react';
import { readApiError } from '../../api/envelope';
import { useLanguage } from '../../i18n/LanguageContext';
import type { ManualViopPosition, ManualViopPositionCreatePayload, ViopDirection } from '../../types/viopPosition';
import type { TerminalListInstrumentVm } from '../../utils/marketTerminalListVm';
import { viopCategoryLabel, viopCategoryToBackend } from './viopBondMarket';
import { formatViopUnderlyingDisplay, resolveViopExpiry, viopUnderlyingCode } from './viopContractMeta';
import { resolveViopHistoricalPrice } from '../../services/viopPositionApi';
import type { PositionHistoricalPriceResolve } from '../../types/historicalPriceResolve';
import { computeViopLive } from './viopBondCalculations';
import { fmtLeverageX, fmtLocaleDecimal, fmtMoney, fmtRatioPercent, parseLocaleDecimal } from './formatViopBond';
import { HistoricalPriceResolveBanner } from './HistoricalPriceResolveBanner';
import { viopCategoryFor } from '../../constants/ViopWhitelist';
import { useViopFxRates } from '../../hooks/useViopFxRates';
import {
    calculateViopPositionMetrics,
    findSimilarOpenViopPosition,
    getViopContractCurrency,
    mergeViopPositionQuantities,
    resolveContractMultiplier,
} from '../../utils/viopPositionMetrics';

type Props = {
    open: boolean;
    onClose: () => void;
    instrument: TerminalListInstrumentVm | null;
    editPosition?: ManualViopPosition | null;
    openPositions?: ManualViopPosition[];
    onSubmit: (
        payload: ManualViopPositionCreatePayload,
        action?: 'create' | 'merge',
        mergeId?: number,
    ) => Promise<void>;
};

export function ViopPositionAddModal({
    open,
    onClose,
    instrument,
    editPosition,
    openPositions = [],
    onSubmit,
}: Props) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    const [direction, setDirection] = useState<ViopDirection>('LONG');
    const [contractCount, setContractCount] = useState('1');
    const [entryPriceInput, setEntryPriceInput] = useState('');
    const [entryDate, setEntryDate] = useState(() => new Date().toISOString().slice(0, 10));
    const [initialMarginInput, setInitialMarginInput] = useState('');
    const [manualCurrentPriceInput, setManualCurrentPriceInput] = useState('');
    const [note, setNote] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [marginAutoFilled, setMarginAutoFilled] = useState(false);
    const [priceResolve, setPriceResolve] = useState<PositionHistoricalPriceResolve | null>(null);
    const [resolvingPrice, setResolvingPrice] = useState(false);
    const [duplicateTarget, setDuplicateTarget] = useState<ManualViopPosition | null>(null);
    const [pendingPayload, setPendingPayload] = useState<ManualViopPositionCreatePayload | null>(null);

    const { data: fxRates } = useViopFxRates(open);

    const symbol = instrument?.symbol ?? editPosition?.symbol ?? '';
    const displayName = instrument?.displayName ?? editPosition?.displayName ?? symbol;
    const marketPrice = instrument?.price ?? editPosition?.currentPrice ?? null;
    const marketPriceValid = marketPrice != null && Number.isFinite(marketPrice) && marketPrice > 0;

    const parsedManualCurrent = parseLocaleDecimal(manualCurrentPriceInput, locale);
    const effectiveCurrent = marketPriceValid ? marketPrice! : parsedManualCurrent;
    const priceMissing = !marketPriceValid;

    const cat = viopCategoryFor(symbol);
    // Çarpan kullanıcıdan alınmaz; sembol+kategoriye göre gerçek VİOP sözleşme büyüklüğü.
    const contractMultiplier = resolveContractMultiplier(symbol, cat ?? undefined, viopUnderlyingCode(symbol));

    const expiryResolved = useMemo(
        () =>
            resolveViopExpiry(symbol, locale, {
                contractMonth: instrument?.contractMonth,
                expiryDate: editPosition?.expiryDate,
            }),
        [symbol, locale, instrument?.contractMonth, editPosition?.expiryDate],
    );

    const underlyingDisplay = useMemo(() => formatViopUnderlyingDisplay(symbol), [symbol]);
    const marginFromMarket =
        instrument?.marginRequirement != null && Number.isFinite(instrument.marginRequirement)
            ? instrument.marginRequirement
            : null;

    useEffect(() => {
        if (!open) return;

        const entryNum = editPosition?.entryPrice ?? (marketPriceValid ? marketPrice! : null);
        const marginNum =
            editPosition?.initialMargin != null
                ? editPosition.initialMargin
                : marginFromMarket != null
                  ? marginFromMarket
                  : null;

        setDirection(editPosition?.direction ?? 'LONG');
        setContractCount(String(editPosition?.contractCount ?? 1));
        setEntryPriceInput(entryNum != null ? fmtLocaleDecimal(entryNum, locale) : '');
        setEntryDate(editPosition?.entryDate ?? new Date().toISOString().slice(0, 10));
        setInitialMarginInput(marginNum != null ? fmtLocaleDecimal(marginNum, locale) : '');
        setMarginAutoFilled(
            marginFromMarket != null &&
                editPosition?.initialMargin == null &&
                instrument != null &&
                !editPosition,
        );
        setManualCurrentPriceInput(
            editPosition?.currentPrice != null && priceMissing
                ? fmtLocaleDecimal(editPosition.currentPrice, locale)
                : '',
        );
        setNote(editPosition?.note ?? '');
        setError(null);
        setPriceResolve(null);
    }, [open, instrument, editPosition, marketPrice, marketPriceValid, marginFromMarket, priceMissing, locale]);

    const handleResolveEntryPrice = async () => {
        if (!symbol.trim() || !entryDate) return;
        setResolvingPrice(true);
        setError(null);
        try {
            const r = await resolveViopHistoricalPrice(symbol.trim(), entryDate);
            setPriceResolve(r);
            if (r.matchType !== 'NOT_FOUND' && r.price != null && Number.isFinite(r.price)) {
                setEntryPriceInput(fmtLocaleDecimal(r.price, locale));
            }
        } catch (err) {
            setError(readApiError(err).message || t('viopBond.priceResolveFailed', 'Fiyat bulunamadı'));
        } finally {
            setResolvingPrice(false);
        }
    };

    const entryPriceNum = parseLocaleDecimal(entryPriceInput, locale) ?? 0;
    const initialMarginNum = parseLocaleDecimal(initialMarginInput, locale) ?? 0;
    const contractCountNum = Number(contractCount) || 0;

    const quoteCurrency = getViopContractCurrency(symbol, viopUnderlyingCode(symbol), cat ?? undefined);

    const liveInput = useMemo(
        () => ({
            symbol,
            underlyingSymbol: viopUnderlyingCode(symbol),
            viopCategory: viopCategoryToBackend(cat ?? 'COMMODITY'),
            direction,
            entryPrice: entryPriceNum,
            currentPrice: effectiveCurrent,
            contractMultiplier,
            contractCount: contractCountNum,
            initialMargin: initialMarginNum,
        }),
        [
            symbol,
            cat,
            direction,
            entryPriceNum,
            effectiveCurrent,
            contractMultiplier,
            contractCountNum,
            initialMarginNum,
        ],
    );

    const liveMetrics = useMemo(
        () => calculateViopPositionMetrics(liveInput, fxRates ?? { usdTry: null, eurTry: null }),
        [liveInput, fxRates],
    );

    const live = useMemo(
        () =>
            computeViopLive({
                ...liveInput,
                fxRates: fxRates ?? { usdTry: null, eurTry: null },
            }),
        [liveInput, fxRates],
    );

    const formatOnBlur = (value: string, setter: (v: string) => void) => {
        const n = parseLocaleDecimal(value, locale);
        if (n != null) setter(fmtLocaleDecimal(n, locale));
    };

    if (!open) return null;

    const buildPayload = (): ManualViopPositionCreatePayload => ({
        symbol: symbol.trim().toUpperCase(),
        displayName: displayName.trim() || undefined,
        viopCategory: editPosition?.viopCategory ?? viopCategoryToBackend(cat),
        underlyingSymbol: viopUnderlyingCode(symbol),
        direction,
        contractCount: contractCountNum,
        entryPrice: entryPriceNum,
        entryDate,
        currentPrice: effectiveCurrent ?? undefined,
        contractMultiplier,
        initialMargin: initialMarginNum > 0 ? initialMarginNum : undefined,
        expiryDate: expiryResolved?.expiryDate,
        note: note.trim() || undefined,
    });

    const savePayload = async (payload: ManualViopPositionCreatePayload, action: 'create' | 'merge' = 'create', mergeId?: number) => {
        setSaving(true);
        setError(null);
        try {
            await onSubmit(payload, action, mergeId);
            setDuplicateTarget(null);
            setPendingPayload(null);
            onClose();
        } catch (err) {
            setError(readApiError(err).message || t('viopBond.saveFailed', 'Kayıt başarısız'));
        } finally {
            setSaving(false);
        }
    };

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        const payload = buildPayload();
        if (!editPosition) {
            const similar = findSimilarOpenViopPosition(openPositions, payload);
            if (similar) {
                setDuplicateTarget(similar);
                setPendingPayload(payload);
                return;
            }
        }
        await savePayload(payload, 'create');
    };

    const handleMergeIntoExisting = async () => {
        if (!duplicateTarget || !pendingPayload) return;
        const merged = mergeViopPositionQuantities(
            duplicateTarget,
            pendingPayload.contractCount,
            pendingPayload.entryPrice,
        );
        await savePayload(
            {
                ...pendingPayload,
                contractCount: merged.contractCount,
                entryPrice: merged.entryPrice,
                // Teminat tek sözleşme başınadır; aynı kontrat için toplanmaz.
                initialMargin:
                    pendingPayload.initialMargin ?? duplicateTarget.initialMargin ?? undefined,
            },
            'merge',
            duplicateTarget.id,
        );
    };

    const longLabel = t('viopBond.directionLong', 'LONG — fiyat yükselirse kâr');
    const shortLabel = t('viopBond.directionShort', 'SHORT — fiyat düşerse kâr');

    return (
        <div className="vb-modal-backdrop" onClick={onClose} role="presentation">
            <div className="vb-modal vb-modal--wide pf-card-premium" onClick={(e) => e.stopPropagation()} role="dialog">
                <div className="vb-modal-header">
                    <h3>
                        {editPosition
                            ? t('viopBond.editViop', 'VİOP Pozisyonu Güncelle')
                            : t('viopBond.addViopFromMarket', 'Pozisyona Ekle')}
                    </h3>
                    <button type="button" className="vb-icon-btn" onClick={onClose} aria-label={t('viopBond.close', 'Kapat')}>
                        <X size={18} />
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="vb-modal-body">
                    {duplicateTarget && pendingPayload ? (
                        <div className="vb-duplicate-prompt" role="alert">
                            <p>
                                {t(
                                    'viopBond.duplicatePositionWarn',
                                    'Aynı kontrat ve aynı yön için benzer bir açık pozisyonunuz var. Yeni pozisyon olarak mı eklemek istiyorsunuz, mevcut pozisyona adet eklemek mi istiyorsunuz?',
                                )}
                            </p>
                            <p className="vb-cell-sub">
                                {duplicateTarget.symbol} · {duplicateTarget.direction} ·{' '}
                                {duplicateTarget.contractCount} {t('viopBond.contracts', 'adet')}
                            </p>
                            <div className="vb-duplicate-prompt__actions">
                                <button
                                    type="button"
                                    className="pf-dash-btn"
                                    disabled={saving}
                                    onClick={() => void savePayload(pendingPayload, 'create')}
                                >
                                    {t('viopBond.duplicateAsNew', 'Yeni pozisyon olarak ekle')}
                                </button>
                                <button
                                    type="button"
                                    className="pf-dash-btn pf-dash-btn--primary"
                                    disabled={saving}
                                    onClick={() => void handleMergeIntoExisting()}
                                >
                                    {t('viopBond.duplicateMerge', 'Mevcut pozisyona ekle')}
                                </button>
                                <button
                                    type="button"
                                    className="pf-dash-btn"
                                    disabled={saving}
                                    onClick={() => {
                                        setDuplicateTarget(null);
                                        setPendingPayload(null);
                                    }}
                                >
                                    {t('viopBond.cancel', 'İptal')}
                                </button>
                            </div>
                        </div>
                    ) : null}
                    <div className="vb-modal-grid">
                        <section className="vb-modal-section">
                            <h4>{t('viopBond.sectionContract', 'Kontrat Bilgileri')}</h4>
                            <div className="vb-readonly-grid">
                                <div>
                                    <span>{t('viopBond.colContract', 'Kontrat')}</span>
                                    <strong>{symbol}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colDisplayName', 'Görünen ad')}</span>
                                    <strong>{displayName}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colViopType', 'Tür')}</span>
                                    <strong>{viopCategoryLabel(cat, t)}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colUnderlying', 'Dayanak')}</span>
                                    <strong>{underlyingDisplay}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colCurrentPrice', 'Güncel fiyat')}</span>
                                    <strong>
                                        {priceMissing
                                            ? '—'
                                            : fmtMoney(effectiveCurrent, locale)}
                                    </strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colExpiry', 'Vade')}</span>
                                    <strong title={expiryResolved?.expiryDate}>
                                        {expiryResolved?.displayLong ?? expiryResolved?.displayShort ?? '—'}
                                    </strong>
                                    {expiryResolved?.displayShort ? (
                                        <span className="vb-cell-sub">{expiryResolved.displayShort}</span>
                                    ) : null}
                                </div>
                                <div>
                                    <span>{t('viopBond.colMultiplier', 'Kontrat çarpanı')}</span>
                                    <strong>{fmtLocaleDecimal(contractMultiplier, locale, 0)}</strong>
                                </div>
                                <div>
                                    <span>{t('viopBond.colMargin', 'Teminat (piyasa)')}</span>
                                    <strong>{fmtMoney(marginFromMarket, locale)}</strong>
                                </div>
                                {instrument?.source ? (
                                    <div>
                                        <span>{t('viopBond.colSource', 'Kaynak')}</span>
                                        <strong>{instrument.source}</strong>
                                    </div>
                                ) : null}
                            </div>
                            {priceMissing ? (
                                <>
                                    <p className="vb-warn">
                                        {t(
                                            'viopBond.priceMissingHint',
                                            'Güncel fiyat bulunamadı, manuel giriş yapabilirsiniz.',
                                        )}
                                    </p>
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
                                </>
                            ) : null}
                        </section>

                        <section className="vb-modal-section">
                            <h4>{t('viopBond.sectionPosition', 'Pozisyon Bilgileri')}</h4>
                            <div className="vb-form-fields">
                                <label className="vb-field vb-field--full">
                                    {t('viopBond.colDirection', 'Yön')}
                                    <select value={direction} onChange={(e) => setDirection(e.target.value as ViopDirection)}>
                                        <option value="LONG">{longLabel}</option>
                                        <option value="SHORT">{shortLabel}</option>
                                    </select>
                                </label>
                                <label className="vb-field">
                                    {t('viopBond.colCount', 'Kontrat adedi')}
                                    <input
                                        type="number"
                                        min="0.000001"
                                        step="any"
                                        required
                                        value={contractCount}
                                        onChange={(e) => setContractCount(e.target.value)}
                                    />
                                </label>
                                <label className="vb-field">
                                    {t('viopBond.colEntryDate', 'Giriş tarihi')}
                                    <input
                                        type="date"
                                        required
                                        value={entryDate}
                                        onChange={(e) => {
                                            setEntryDate(e.target.value);
                                            setPriceResolve(null);
                                        }}
                                    />
                                </label>
                                <label className="vb-field vb-field--full">
                                    {t('viopBond.colEntryPrice', 'Giriş fiyatı')}
                                    <div className="vb-field-actions">
                                        <input
                                            type="text"
                                            inputMode="decimal"
                                            required
                                            value={entryPriceInput}
                                            onChange={(e) => {
                                                setEntryPriceInput(e.target.value);
                                                setPriceResolve(null);
                                            }}
                                            onBlur={() => formatOnBlur(entryPriceInput, setEntryPriceInput)}
                                            placeholder={locale.startsWith('tr') ? '0,00' : '0.00'}
                                        />
                                        <button
                                            type="button"
                                            className="pf-dash-btn pf-dash-btn--compact"
                                            disabled={resolvingPrice || !symbol.trim() || !entryDate}
                                            onClick={() => void handleResolveEntryPrice()}
                                        >
                                            {t('viopBond.resolveEntryPrice', 'Giriş fiyatını otomatik bul')}
                                        </button>
                                        <button
                                            type="button"
                                            className="pf-dash-btn pf-dash-btn--compact"
                                            disabled={effectiveCurrent == null}
                                            onClick={() => {
                                                if (effectiveCurrent != null) {
                                                    setEntryPriceInput(fmtLocaleDecimal(effectiveCurrent, locale));
                                                    setPriceResolve(null);
                                                }
                                            }}
                                        >
                                            {t('viopBond.useCurrentAsEntry', 'Güncel fiyatı giriş yap')}
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
                                    {t('viopBond.colMarginPerContract', 'Kontrat başına başlangıç teminatı')}
                                    <input
                                        type="text"
                                        inputMode="decimal"
                                        value={initialMarginInput}
                                        onChange={(e) => {
                                            setInitialMarginInput(e.target.value);
                                            setMarginAutoFilled(false);
                                        }}
                                        onBlur={() => formatOnBlur(initialMarginInput, setInitialMarginInput)}
                                        placeholder={locale.startsWith('tr') ? '0,00' : '0.00'}
                                    />
                                    {marginAutoFilled ? (
                                        <span className="vb-field-hint">
                                            {t(
                                                'viopBond.marginAutoHint',
                                                'Piyasa verisinden otomatik dolduruldu, gerekirse düzenleyebilirsiniz.',
                                            )}
                                        </span>
                                    ) : null}
                                </label>
                                <label className="vb-field vb-field--full">
                                    {t('viopBond.colNote', 'Not')}
                                    <textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} />
                                </label>
                            </div>
                        </section>

                        <section className="vb-modal-section vb-live-summary">
                            <h4>{t('viopBond.sectionLive', 'Canlı Hesap Özeti')}</h4>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colPnl', 'Tahmini K/Z (TRY)')}</span>
                                <strong className={live.unrealizedPnl != null && live.unrealizedPnl >= 0 ? 'vb-pos' : 'vb-neg'}>
                                    {fmtMoney(live.unrealizedPnl, locale)} ₺
                                </strong>
                            </div>
                            <div className="vb-live-row vb-live-row--stack">
                                <div className="vb-live-row-top">
                                    <span>{t('viopBond.colExposure', 'Risk maruziyeti (TRY)')}</span>
                                    <strong>
                                        {live.riskExposure != null
                                            ? `${fmtMoney(live.riskExposure, locale)} ₺`
                                            : '—'}
                                    </strong>
                                </div>
                                {liveMetrics.missingFxRate ? (
                                    <p className="vb-warn vb-live-hint">
                                        {t('viopBond.exposureTryMissing', 'Kur verisi eksik — TRY karşılığı hesaplanamadı')}
                                    </p>
                                ) : (
                                    <p className="vb-live-hint">
                                        {t(
                                            'viopBond.riskExposureHint',
                                            'Risk maruziyeti kontratın nominal büyüklüğüdür (TRY karşılığı).',
                                        )}
                                    </p>
                                )}
                            </div>
                            <div className="vb-live-row">
                                <span>
                                    {marginAutoFilled
                                        ? t('viopBond.estimatedTotalMargin', 'Tahmini toplam teminat')
                                        : t('viopBond.totalMargin', 'Toplam teminat')}
                                </span>
                                <strong>{fmtMoney(initialMarginNum * contractCountNum, locale)} ₺</strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colLeverage', 'Kaldıraç')}</span>
                                <strong>{fmtLeverageX(liveMetrics.leverage, locale)}</strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.colPnlMargin', 'K/Z / Teminat')}</span>
                                <strong>{fmtRatioPercent(liveMetrics.pnlToMarginRatio, locale)}</strong>
                            </div>
                            <div className="vb-live-row">
                                <span>{t('viopBond.netEffect', 'Net finansal etki')}</span>
                                <strong>{fmtMoney(live.netFinancialEffect, locale)} ₺</strong>
                            </div>
                            <div className="vb-live-meta">
                                <span>
                                    {t('viopBond.colCurrency', 'PB')}: {quoteCurrency} ·{' '}
                                    {t('viopBond.liveMeta', 'Çarpan')}:{' '}
                                    {fmtLocaleDecimal(contractMultiplier, locale, 0)} ·{' '}
                                    {t('viopBond.colCount', 'Adet')}: {contractCount || '—'}
                                </span>
                            </div>
                            {live.unrealizedPnl == null ? (
                                <p className="vb-muted-sm">
                                    {priceMissing
                                        ? t(
                                              'viopBond.priceMissingHint',
                                              'Güncel fiyat bulunamadı, manuel giriş yapabilirsiniz.',
                                          )
                                        : t('viopBond.liveHint', 'Güncel fiyat ve giriş fiyatı girildiğinde hesaplanır.')}
                                </p>
                            ) : null}
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
