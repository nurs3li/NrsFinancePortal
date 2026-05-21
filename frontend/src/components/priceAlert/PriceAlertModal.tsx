import {
    useCallback,
    useEffect,
    useMemo,
    useState,
    type CSSProperties,
    type FormEvent,
    type ReactNode,
} from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, Trash2, X } from 'lucide-react';
import { useTheme } from '../../theme/ThemeContext';
import { useLanguage } from '../../i18n/LanguageContext';
import { createPriceAlert, deletePriceAlert, listPriceAlerts, readFinanceApiError } from '../../services/priceAlertApi';
import { priceAlertKeys } from '../../queries/priceAlertKeys';
import { fetchPriceAlertReferencePrice } from '../../utils/priceAlertReferencePrice';
import { fmtLocaleDecimal, fmtMoney, parseLocaleDecimal } from '../viopBond/formatViopBond';
import '../../pages/Portfolio.css';
import type {
    PriceAlertAssetType,
    PriceAlertChannels,
    PriceAlertConditionType,
} from '../../types/priceAlert';

export type PriceAlertModalProps = {
    open: boolean;
    onClose: () => void;
    assetType: PriceAlertAssetType;
    symbol: string;
    displayName?: string;
    referencePrice?: number | null;
    priceCurrency?: string | null;
};

type InferredPriceCondition = 'PRICE_GTE' | 'PRICE_LTE';

const PRICE_QUICK_PCTS = [-10, -5, -3, -1, 1, 3, 5, 10] as const;

function pctFromReference(reference: number, target: number): number {
    if (!Number.isFinite(reference) || reference <= 0) return 0;
    return ((target - reference) / reference) * 100;
}

function targetFromPct(reference: number, pct: number): number {
    return reference * (1 + pct / 100);
}

function formatPctInput(pct: number, locale: string): string {
    return fmtLocaleDecimal(pct, locale, 2);
}

function pricesEqual(a: number, b: number): boolean {
    const scale = Math.max(Math.abs(a), Math.abs(b), 1) * 1e-6;
    return Math.abs(a - b) <= scale;
}

function inferPriceCondition(target: number, reference: number): InferredPriceCondition | 'SAME' | null {
    if (!Number.isFinite(target) || !Number.isFinite(reference) || reference <= 0) return null;
    if (pricesEqual(target, reference)) return 'SAME';
    return target > reference ? 'PRICE_GTE' : 'PRICE_LTE';
}

function conditionTypeLabel(type: PriceAlertConditionType, t: (k: string, fb: string) => string): string {
    switch (type) {
        case 'PRICE_GTE':
            return t('priceAlert.condPriceGte', 'Fiyat ≥ (üstüne çıkarsa)');
        case 'PRICE_LTE':
            return t('priceAlert.condPriceLte', 'Fiyat ≤ (altına düşerse)');
        case 'CHANGE_PCT_GTE':
            return t('priceAlert.condPctUpDaily', '% artış (günlük)');
        case 'CHANGE_PCT_LTE':
            return t('priceAlert.condPctDownDaily', '% düşüş (günlük)');
        default:
            return type;
    }
}

export function PriceAlertModal({
    open,
    onClose,
    assetType,
    symbol,
    displayName,
    referencePrice: referencePriceProp,
    priceCurrency,
}: PriceAlertModalProps) {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const queryClient = useQueryClient();

    const [targetPriceInput, setTargetPriceInput] = useState('');
    const [pctOffsetInput, setPctOffsetInput] = useState('');
    const [inApp, setInApp] = useState(true);
    const [email, setEmail] = useState(true);
    const [repeatAlert, setRepeatAlert] = useState(false);
    const [banner, setBanner] = useState<string | null>(null);

    const label = displayName?.trim() || symbol;
    const currencyLabel = (priceCurrency?.trim() || 'TRY').toUpperCase();

    const { data: fetchedReferencePrice, isLoading: refPriceLoading } = useQuery({
        queryKey: ['price-alert-ref', assetType, symbol],
        queryFn: ({ signal }) => fetchPriceAlertReferencePrice(assetType, symbol, signal),
        enabled: open && (referencePriceProp == null || !Number.isFinite(referencePriceProp)),
        staleTime: 60_000,
    });

    const referencePrice = useMemo(() => {
        if (referencePriceProp != null && Number.isFinite(referencePriceProp) && referencePriceProp > 0) {
            return referencePriceProp;
        }
        if (fetchedReferencePrice != null && Number.isFinite(fetchedReferencePrice) && fetchedReferencePrice > 0) {
            return fetchedReferencePrice;
        }
        return null;
    }, [referencePriceProp, fetchedReferencePrice]);

    const resolvedTarget = useMemo(() => {
        const fromPrice = parseLocaleDecimal(targetPriceInput, locale);
        if (fromPrice != null) return fromPrice;
        if (referencePrice != null) {
            const pct = parseLocaleDecimal(pctOffsetInput, locale);
            if (pct != null) return targetFromPct(referencePrice, pct);
        }
        return null;
    }, [targetPriceInput, pctOffsetInput, referencePrice, locale]);

    const inferred = useMemo(() => {
        if (resolvedTarget == null || referencePrice == null) return null;
        return inferPriceCondition(resolvedTarget, referencePrice);
    }, [resolvedTarget, referencePrice]);

    const inferredLabel = useMemo(() => {
        if (inferred === 'SAME') {
            return t('priceAlert.sameAsLast', 'Hedef fiyat son fiyatla aynı olamaz.');
        }
        if (inferred === 'PRICE_GTE' || inferred === 'PRICE_LTE') {
            const priceText = `${fmtMoney(resolvedTarget, locale)} ${currencyLabel}`;
            const key = inferred === 'PRICE_GTE' ? 'priceAlert.inferredGte' : 'priceAlert.inferredLte';
            const fallback =
                inferred === 'PRICE_GTE'
                    ? 'Fiyat ≥ {price} — üstüne çıkarsa'
                    : 'Fiyat ≤ {price} — altına düşerse';
            return t(key, fallback).replace('{price}', priceText);
        }
        return t('priceAlert.inferredPending', 'Hedef fiyat veya yüzde girin; koşul otomatik belirlenir.');
    }, [inferred, resolvedTarget, locale, currencyLabel, t]);

    const { data: alerts = [], isLoading } = useQuery({
        queryKey: priceAlertKeys.list(),
        queryFn: listPriceAlerts,
        enabled: open,
    });

    const relevantAlerts = useMemo(
        () =>
            alerts.filter(
                (a) =>
                    a.assetType === assetType &&
                    a.symbol.toUpperCase() === symbol.toUpperCase() &&
                    a.status === 'ACTIVE',
            ),
        [alerts, assetType, symbol],
    );

    const syncPctFromPrice = useCallback(
        (price: number) => {
            if (referencePrice == null) return;
            setPctOffsetInput(formatPctInput(pctFromReference(referencePrice, price), locale));
        },
        [referencePrice, locale],
    );

    const syncPriceFromPct = useCallback(
        (pct: number) => {
            if (referencePrice == null) return;
            const target = targetFromPct(referencePrice, pct);
            setTargetPriceInput(fmtLocaleDecimal(target, locale));
        },
        [referencePrice, locale],
    );

    const applyQuickPct = useCallback(
        (pct: number) => {
            if (referencePrice == null) return;
            const target = targetFromPct(referencePrice, pct);
            setTargetPriceInput(fmtLocaleDecimal(target, locale));
            setPctOffsetInput(formatPctInput(pct, locale));
        },
        [referencePrice, locale],
    );

    useEffect(() => {
        if (!open) return;
        setBanner(null);
        setTargetPriceInput('');
        setPctOffsetInput('');
        setInApp(true);
        setEmail(true);
        setRepeatAlert(false);
    }, [open, assetType, symbol]);

    const createMutation = useMutation({
        mutationFn: createPriceAlert,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: priceAlertKeys.all });
            setBanner(null);
            setTargetPriceInput('');
            setPctOffsetInput('');
        },
        onError: (err) => setBanner(readFinanceApiError(err).message),
    });

    const deleteMutation = useMutation({
        mutationFn: deletePriceAlert,
        onSuccess: () => queryClient.invalidateQueries({ queryKey: priceAlertKeys.all }),
    });

    if (!open) return null;

    const cardSurface: CSSProperties = {
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        borderRadius: 12,
        color: tokens.text,
    };

    const channels: PriceAlertChannels =
        inApp && email ? 'BOTH' : email ? 'EMAIL' : inApp ? 'IN_APP' : 'BOTH';

    const handleSubmit = (e: FormEvent) => {
        e.preventDefault();
        if (referencePrice == null) {
            setBanner(t('priceAlert.needReference', 'Koşul için son fiyat gerekli.'));
            return;
        }
        const target = resolvedTarget;
        if (target == null || !Number.isFinite(target)) {
            setBanner(t('priceAlert.invalidThreshold', 'Geçerli bir eşik değeri girin.'));
            return;
        }
        const condition = inferPriceCondition(target, referencePrice);
        if (condition === 'SAME') {
            setBanner(t('priceAlert.sameAsLast', 'Hedef fiyat son fiyatla aynı olamaz.'));
            return;
        }
        if (condition == null) {
            setBanner(t('priceAlert.invalidThreshold', 'Geçerli bir eşik değeri girin.'));
            return;
        }

        createMutation.mutate({
            assetType,
            symbol,
            conditionType: condition,
            threshold: target,
            changeWindow: null,
            channels,
            repeatAlert,
            cooldownHours: 24,
        });
    };

    const chipStyle: CSSProperties = {
        padding: '4px 8px',
        fontSize: '0.75rem',
        borderRadius: 6,
        border: `1px solid ${tokens.border}`,
        background: tokens.bg,
        color: tokens.textMuted,
        cursor: referencePrice != null ? 'pointer' : 'not-allowed',
        opacity: referencePrice != null ? 1 : 0.5,
    };

    const inferredTone =
        inferred === 'PRICE_GTE'
            ? tokens.success
            : inferred === 'PRICE_LTE'
              ? '#f59e0b'
              : inferred === 'SAME'
                ? tokens.error
                : tokens.textMuted;

    return (
        <ModalBackdrop onClose={onClose}>
            <ModalShell cardSurface={cardSurface} onClose={onClose} title={t('priceAlert.title', 'Alarm Kur')}>
                <p style={{ margin: '0 0 8px', fontSize: '0.85rem', color: tokens.textMuted }}>
                    {t('priceAlert.assetLabel', 'Varlık')}: <strong style={{ color: tokens.text }}>{label}</strong>
                </p>

                <div
                    style={{
                        marginBottom: 12,
                        padding: '8px 10px',
                        borderRadius: 8,
                        border: `1px solid ${tokens.border}`,
                        background: tokens.bg,
                        fontSize: '0.85rem',
                    }}
                >
                    <span style={{ color: tokens.textMuted }}>{t('priceAlert.lastPrice', 'Son fiyat')}: </span>
                    {refPriceLoading && referencePrice == null ? (
                        <span style={{ color: tokens.textMuted }}>
                            {t('priceAlert.lastPriceLoading', 'Son fiyat yükleniyor…')}
                        </span>
                    ) : referencePrice != null ? (
                        <strong style={{ color: tokens.text }}>
                            {fmtMoney(referencePrice, locale)} {currencyLabel}
                        </strong>
                    ) : (
                        <span style={{ color: tokens.textMuted }}>
                            {t('priceAlert.lastPriceUnavailable', 'Son fiyat alınamadı')}
                        </span>
                    )}
                </div>

                <form onSubmit={handleSubmit} className="mia-form">
                    <div
                        style={{
                            marginBottom: 10,
                            padding: '8px 10px',
                            borderRadius: 8,
                            border: `1px solid ${inferred === 'PRICE_GTE' || inferred === 'PRICE_LTE' ? inferredTone : tokens.border}`,
                            background: tokens.bg,
                            fontSize: '0.82rem',
                        }}
                    >
                        <div style={{ color: tokens.textMuted, marginBottom: 4, fontSize: '0.75rem' }}>
                            {t('priceAlert.inferredCondition', 'Alarm koşulu')}
                        </div>
                        <div style={{ color: inferredTone, fontWeight: 600, lineHeight: 1.4 }}>{inferredLabel}</div>
                    </div>

                    <label className="pf-field-label">
                        {t('priceAlert.targetPrice', 'Hedef fiyat')}
                        <input
                            className="pf-input"
                            type="text"
                            inputMode="decimal"
                            value={targetPriceInput}
                            onChange={(e) => setTargetPriceInput(e.target.value)}
                            onBlur={() => {
                                const p = parseLocaleDecimal(targetPriceInput, locale);
                                if (p != null) syncPctFromPrice(p);
                            }}
                            placeholder={t('priceAlert.thresholdPricePlaceholder', 'örn. 350')}
                        />
                    </label>

                    <label className="pf-field-label">
                        {t('priceAlert.pctFromLast', 'Son fiyata göre %')}
                        <input
                            className="pf-input"
                            type="text"
                            inputMode="decimal"
                            value={pctOffsetInput}
                            onChange={(e) => setPctOffsetInput(e.target.value)}
                            onBlur={() => {
                                const pct = parseLocaleDecimal(pctOffsetInput, locale);
                                if (pct != null) syncPriceFromPct(pct);
                            }}
                            placeholder={t('priceAlert.thresholdPctPlaceholder', 'örn. 1 veya -5')}
                        />
                    </label>

                    <div style={{ marginTop: 6 }}>
                        <span
                            className="pf-field-label"
                            style={{ display: 'block', marginBottom: 6, fontSize: '0.78rem', color: tokens.textMuted }}
                        >
                            {t('priceAlert.quickPct', 'Hızlı seçim')}
                        </span>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                            {PRICE_QUICK_PCTS.map((pct) => (
                                <button
                                    key={pct}
                                    type="button"
                                    style={chipStyle}
                                    disabled={referencePrice == null}
                                    onClick={() => applyQuickPct(pct)}
                                >
                                    {pct > 0 ? `+${pct}%` : `${pct}%`}
                                </button>
                            ))}
                        </div>
                    </div>

                    <fieldset style={{ border: 'none', padding: 0, margin: '8px 0' }}>
                        <legend className="pf-field-label" style={{ marginBottom: 6 }}>
                            {t('priceAlert.channels', 'Bildirim kanalı')}
                        </legend>
                        <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: '0.85rem', marginBottom: 4 }}>
                            <input type="checkbox" checked={inApp} onChange={(e) => setInApp(e.target.checked)} />
                            {t('priceAlert.channelInApp', 'Uygulama içi')}
                        </label>
                        <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: '0.85rem' }}>
                            <input type="checkbox" checked={email} onChange={(e) => setEmail(e.target.checked)} />
                            {t('priceAlert.channelEmail', 'E-posta')}
                        </label>
                    </fieldset>

                    <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: '0.82rem', color: tokens.textMuted }}>
                        <input type="checkbox" checked={repeatAlert} onChange={(e) => setRepeatAlert(e.target.checked)} />
                        {t('priceAlert.repeat', 'Tekrarlayan alarm (cooldown 24s)')}
                    </label>

                    {banner ? (
                        <div
                            style={{
                                fontSize: '0.8rem',
                                color: tokens.error,
                                marginTop: 8,
                                padding: 8,
                                borderRadius: 8,
                                border: `1px solid ${tokens.border}`,
                            }}
                        >
                            {banner}
                        </div>
                    ) : null}

                    <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
                        <button type="submit" className="pf-btn pf-btn--primary" disabled={createMutation.isPending}>
                            <Bell size={14} style={{ marginRight: 6, verticalAlign: 'middle' }} />
                            {t('priceAlert.save', 'Alarmı kaydet')}
                        </button>
                        <button type="button" className="pf-btn pf-btn--ghost" onClick={onClose}>
                            {t('common.cancel', 'İptal')}
                        </button>
                    </div>
                </form>

                <section style={{ marginTop: 16, borderTop: `1px solid ${tokens.border}`, paddingTop: 12 }}>
                    <h4 style={{ margin: '0 0 8px', fontSize: '0.85rem' }}>{t('priceAlert.activeList', 'Aktif alarmlar')}</h4>
                    {isLoading ? (
                        <p style={{ fontSize: '0.8rem', color: tokens.textMuted }}>{t('common.loading', 'Yükleniyor…')}</p>
                    ) : relevantAlerts.length === 0 ? (
                        <p style={{ fontSize: '0.8rem', color: tokens.textMuted }}>
                            {t('priceAlert.noActive', 'Bu varlık için aktif alarm yok.')}
                        </p>
                    ) : (
                        <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
                            {relevantAlerts.map((a) => (
                                <li
                                    key={a.id}
                                    style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'center',
                                        gap: 8,
                                        fontSize: '0.8rem',
                                        padding: '6px 0',
                                        borderBottom: `1px solid ${tokens.border}`,
                                    }}
                                >
                                    <span>
                                        {conditionTypeLabel(a.conditionType, t)} · {fmtMoney(Number(a.threshold), locale)}{' '}
                                        {a.conditionType.includes('PCT') ? '%' : currencyLabel}
                                    </span>
                                    <button
                                        type="button"
                                        className="pf-icon-inline pf-icon-inline--danger"
                                        title={t('priceAlert.delete', 'Sil')}
                                        disabled={deleteMutation.isPending}
                                        onClick={() => deleteMutation.mutate(a.id)}
                                    >
                                        <Trash2 size={14} />
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                </section>
            </ModalShell>
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

function ModalShell({
    children,
    cardSurface,
    onClose,
    title,
}: {
    children: ReactNode;
    cardSurface: CSSProperties;
    onClose: () => void;
    title: string;
}) {
    return (
        <div
            className="mia-modal"
            role="dialog"
            aria-modal
            onMouseDown={(ev) => ev.stopPropagation()}
            style={{ ...cardSurface, maxWidth: 480, width: '100%', maxHeight: '90vh', overflow: 'auto', padding: 16 }}
        >
            <ModalHeader title={title} onClose={onClose} />
            {children}
        </div>
    );
}

function ModalHeader({ title, onClose }: { title: string; onClose: () => void }) {
    return (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
            <h3 style={{ margin: 0, fontSize: '1rem' }}>{title}</h3>
            <button type="button" className="mia-icon-btn" aria-label="Kapat" onClick={onClose}>
                <X size={18} />
            </button>
        </div>
    );
}
