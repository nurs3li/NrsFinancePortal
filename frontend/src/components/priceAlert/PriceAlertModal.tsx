import { useEffect, useMemo, useState, type CSSProperties, type FormEvent, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, Trash2, X } from 'lucide-react';
import { useTheme } from '../../theme/ThemeContext';
import { useLanguage } from '../../i18n/LanguageContext';
import { createPriceAlert, deletePriceAlert, listPriceAlerts, readFinanceApiError } from '../../services/priceAlertApi';
import { priceAlertKeys } from '../../queries/priceAlertKeys';
import '../../pages/Portfolio.css';
import type {
    PriceAlertAssetType,
    PriceAlertChangeWindow,
    PriceAlertChannels,
    PriceAlertConditionType,
} from '../../types/priceAlert';

export type PriceAlertModalProps = {
    open: boolean;
    onClose: () => void;
    assetType: PriceAlertAssetType;
    symbol: string;
    displayName?: string;
};

type ConditionUi = 'PRICE_GTE' | 'PRICE_LTE' | 'CHANGE_PCT_UP' | 'CHANGE_PCT_DOWN';

function conditionUiToApi(
    ui: ConditionUi,
    assetType: PriceAlertAssetType,
): { conditionType: PriceAlertConditionType; changeWindow?: PriceAlertChangeWindow | null } {
    switch (ui) {
        case 'PRICE_GTE':
            return { conditionType: 'PRICE_GTE' };
        case 'PRICE_LTE':
            return { conditionType: 'PRICE_LTE' };
        case 'CHANGE_PCT_UP':
            return {
                conditionType: 'CHANGE_PCT_GTE',
                changeWindow: assetType === 'CRYPTO' ? 'HOURS_24' : 'DAILY',
            };
        case 'CHANGE_PCT_DOWN':
            return {
                conditionType: 'CHANGE_PCT_LTE',
                changeWindow: assetType === 'CRYPTO' ? 'HOURS_24' : 'DAILY',
            };
    }
}

export function PriceAlertModal({ open, onClose, assetType, symbol, displayName }: PriceAlertModalProps) {
    const { tokens } = useTheme();
    const { t } = useLanguage();
    const queryClient = useQueryClient();

    const [conditionUi, setConditionUi] = useState<ConditionUi>('PRICE_GTE');
    const [threshold, setThreshold] = useState('');
    const [inApp, setInApp] = useState(true);
    const [email, setEmail] = useState(true);
    const [repeatAlert, setRepeatAlert] = useState(false);
    const [banner, setBanner] = useState<string | null>(null);

    const label = displayName?.trim() || symbol;

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

    useEffect(() => {
        if (!open) return;
        setBanner(null);
        setThreshold('');
        setConditionUi('PRICE_GTE');
        setInApp(true);
        setEmail(true);
        setRepeatAlert(false);
    }, [open, assetType, symbol]);

    const createMutation = useMutation({
        mutationFn: createPriceAlert,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: priceAlertKeys.all });
            setBanner(null);
            setThreshold('');
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
        const num = Number(threshold.replace(',', '.'));
        if (!Number.isFinite(num)) {
            setBanner(t('priceAlert.invalidThreshold', 'Geçerli bir eşik değeri girin.'));
            return;
        }
        const mapped = conditionUiToApi(conditionUi, assetType);
        createMutation.mutate({
            assetType,
            symbol,
            conditionType: mapped.conditionType,
            threshold: num,
            changeWindow: mapped.changeWindow ?? null,
            channels,
            repeatAlert,
            cooldownHours: 24,
        });
    };

    return (
        <ModalBackdrop onClose={onClose}>
            <ModalShell cardSurface={cardSurface} onClose={onClose} title={t('priceAlert.title', 'Alarm Kur')}>
                <p style={{ margin: '0 0 12px', fontSize: '0.85rem', color: tokens.textMuted }}>
                    {t('priceAlert.assetLabel', 'Varlık')}: <strong style={{ color: tokens.text }}>{label}</strong>
                </p>

                <form onSubmit={handleSubmit} className="mia-form">
                    <label className="pf-field-label">
                        {t('priceAlert.condition', 'Koşul')}
                        <select
                            className="pf-input"
                            value={conditionUi}
                            onChange={(e) => setConditionUi(e.target.value as ConditionUi)}
                        >
                            <option value="PRICE_GTE">{t('priceAlert.condPriceGte', 'Fiyat ≥ (üstüne çıkarsa)')}</option>
                            <option value="PRICE_LTE">{t('priceAlert.condPriceLte', 'Fiyat ≤ (altına düşerse)')}</option>
                            <option value="CHANGE_PCT_UP">
                                {assetType === 'CRYPTO'
                                    ? t('priceAlert.condPctUp24h', '% artış (24s)')
                                    : t('priceAlert.condPctUpDaily', '% artış (günlük)')}
                            </option>
                            <option value="CHANGE_PCT_DOWN">
                                {assetType === 'CRYPTO'
                                    ? t('priceAlert.condPctDown24h', '% düşüş (24s)')
                                    : t('priceAlert.condPctDownDaily', '% düşüş (günlük)')}
                            </option>
                        </select>
                    </label>

                    <label className="pf-field-label">
                        {t('priceAlert.threshold', 'Eşik')}
                        <input
                            className="pf-input"
                            type="text"
                            inputMode="decimal"
                            value={threshold}
                            onChange={(e) => setThreshold(e.target.value)}
                            placeholder={
                                conditionUi.startsWith('CHANGE')
                                    ? t('priceAlert.thresholdPctPlaceholder', 'örn. 1 veya -5')
                                    : t('priceAlert.thresholdPricePlaceholder', 'örn. 350')
                            }
                        />
                    </label>

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
                                        {a.conditionType} {a.threshold}
                                        {a.conditionType.includes('PCT') ? '%' : ' TL'}
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
