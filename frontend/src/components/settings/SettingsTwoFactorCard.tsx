import { useCallback, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Info, ShieldCheck, ShieldOff } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import { SettingsCard } from './SettingsCard';
import { readApiError } from '../../api/envelope';
import {
    beginTotpSetup,
    cancelTotpSetup,
    confirmTotpSetup,
    disableTotp,
    fetchTotpStatus,
    type TotpSetup,
} from '../../services/totpApi';

type SettingsTwoFactorCardProps = {
    disabled?: boolean;
    embedded?: boolean;
};

export function SettingsTwoFactorCard({ disabled, embedded = false }: SettingsTwoFactorCardProps) {
    const { t } = useLanguage();
    const queryClient = useQueryClient();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [message, setMessage] = useState<string | null>(null);
    const [setup, setSetup] = useState<TotpSetup | null>(null);
    const [code, setCode] = useState('');

    const { data: status, isLoading } = useQuery({
        queryKey: ['users', 'me', 'totp'],
        queryFn: fetchTotpStatus,
        staleTime: 30_000,
        enabled: !disabled,
    });

    const refresh = useCallback(() => {
        void queryClient.invalidateQueries({ queryKey: ['users', 'me', 'totp'] });
    }, [queryClient]);

    const readError = (err: unknown): string =>
        readApiError(err).message || t('settings.totpFailed', 'İşlem başarısız.');

    const handleBeginSetup = async () => {
        setError(null);
        setMessage(null);
        setBusy(true);
        try {
            const payload = await beginTotpSetup();
            setSetup(payload);
            setCode('');
            refresh();
        } catch (err: unknown) {
            setError(readError(err));
        } finally {
            setBusy(false);
        }
    };

    const handleCancelSetup = async () => {
        setError(null);
        setMessage(null);
        setBusy(true);
        try {
            await cancelTotpSetup();
            setSetup(null);
            setCode('');
            refresh();
        } catch (err: unknown) {
            setError(readError(err));
        } finally {
            setBusy(false);
        }
    };

    const handleConfirm = async () => {
        if (!/^\d{6}$/.test(code.trim())) {
            setError(t('settings.totpCodeInvalid', '6 haneli doğrulama kodunu girin.'));
            return;
        }
        setError(null);
        setMessage(null);
        setBusy(true);
        try {
            const msg = await confirmTotpSetup(code.trim());
            setMessage(msg);
            setSetup(null);
            setCode('');
            refresh();
        } catch (err: unknown) {
            setError(readError(err));
        } finally {
            setBusy(false);
        }
    };

    const handleDisable = async () => {
        if (!window.confirm(t('settings.totpDisableConfirm', 'İki aşamalı doğrulamayı kapatmak istiyor musunuz?'))) {
            return;
        }
        setError(null);
        setMessage(null);
        setBusy(true);
        try {
            const msg = await disableTotp();
            setMessage(msg);
            setSetup(null);
            refresh();
        } catch (err: unknown) {
            setError(readError(err));
        } finally {
            setBusy(false);
        }
    };

    const enabled = status?.enabled ?? false;
    const qrSrc = setup?.otpauthUrl
        ? `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(setup.otpauthUrl)}`
        : null;

    const content = (
        <>
            {isLoading ? (
                <p className="settings-muted">{t('settings.loading', 'Yükleniyor…')}</p>
            ) : (
                <div className="settings-totp">
                    {!embedded || setup ? (
                        <div className="settings-totp__status">
                            {enabled ? (
                                <span className="settings-totp__badge settings-totp__badge--on">
                                    <ShieldCheck size={16} aria-hidden />
                                    {t('settings.totpEnabled', 'Etkin')}
                                </span>
                            ) : (
                                <span className="settings-totp__badge settings-totp__badge--off">
                                    <ShieldOff size={16} aria-hidden />
                                    {t('settings.totpDisabled', 'Kapalı')}
                                </span>
                            )}
                        </div>
                    ) : null}

                    {error ? <p className="settings-totp__error">{error}</p> : null}
                    {message ? <p className="settings-totp__message">{message}</p> : null}

                    {!enabled && !setup ? (
                        <button
                            type="button"
                            className="settings-totp__primary"
                            disabled={disabled || busy}
                            onClick={() => void handleBeginSetup()}
                        >
                            {t('settings.totpActivate', 'İki aşamalı doğrulamayı aktif et')}
                        </button>
                    ) : null}

                    {setup ? (
                        <div className="settings-totp__setup">
                            <p className="settings-muted">
                                {t(
                                    'settings.totpScanHint',
                                    'QR kodu Google Authenticator ile okutun veya anahtarı elle girin; ardından uygulamadaki 6 haneli kodu yazın.'
                                )}
                            </p>
                            {qrSrc ? (
                                <img
                                    className="settings-totp__qr"
                                    src={qrSrc}
                                    width={200}
                                    height={200}
                                    alt={t('settings.totpQrAlt', 'Authenticator QR kodu')}
                                />
                            ) : null}
                            <div className="settings-totp__secret">
                                <span className="settings-field__label">
                                    {t('settings.totpSecret', 'Manuel anahtar')}
                                </span>
                                <code className="settings-totp__secret-value">{setup.secret}</code>
                            </div>
                            <label className="settings-totp__code-label">
                                <span className="settings-field__label">
                                    {t('settings.totpCode', 'Doğrulama kodu')}
                                </span>
                                <input
                                    className="settings-totp__code-input"
                                    type="text"
                                    inputMode="numeric"
                                    autoComplete="one-time-code"
                                    maxLength={6}
                                    value={code}
                                    onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                                    placeholder="000000"
                                    disabled={busy}
                                />
                            </label>
                            <div className="settings-totp__actions">
                                <button
                                    type="button"
                                    className="settings-totp__primary"
                                    disabled={busy}
                                    onClick={() => void handleConfirm()}
                                >
                                    {t('settings.totpConfirm', 'Doğrula ve etkinleştir')}
                                </button>
                                <button
                                    type="button"
                                    className="settings-totp__secondary"
                                    disabled={busy}
                                    onClick={() => void handleCancelSetup()}
                                >
                                    {t('settings.totpCancelSetup', 'İptal')}
                                </button>
                            </div>
                        </div>
                    ) : null}

                    {enabled ? (
                        <button
                            type="button"
                            className="settings-totp__danger"
                            disabled={disabled || busy}
                            onClick={() => void handleDisable()}
                        >
                            {t('settings.totpDisable', 'İki aşamalı doğrulamayı kapat')}
                        </button>
                    ) : null}
                </div>
            )}
        </>
    );

    if (embedded) {
        return (
            <section className="settings-panel settings-panel--security">
                <header className="settings-panel__head">
                    <h2 className="settings-panel__title">{t('settings.totpTitle', 'İki Aşamalı Doğrulama')}</h2>
                    <p className="settings-panel__subtitle">
                        {t(
                            'settings.totpSubtitle',
                            'Google Authenticator veya benzeri uygulama ile hesabınızı koruyun.'
                        )}
                    </p>
                </header>
                {!setup ? (
                    <div className="settings-totp__summary">
                        <div className={`settings-totp__summary-card ${enabled ? 'is-enabled' : 'is-disabled'}`}>
                            <div className="settings-totp__summary-badge">
                                {enabled ? <ShieldCheck size={16} aria-hidden /> : <ShieldOff size={16} aria-hidden />}
                                <span>{enabled ? t('settings.totpEnabled', 'Etkin') : t('settings.totpDisabled', 'Kapalı')}</span>
                            </div>
                            <p className="settings-totp__summary-text">
                                {enabled
                                    ? t('settings.totpEnabledBody', 'Hesabınız iki aşamalı doğrulama ile korunuyor.')
                                    : t('settings.totpDisabledBody', 'Ek güvenlik için iki aşamalı doğrulamayı etkinleştirebilirsiniz.')}
                            </p>
                        </div>
                        <div className="settings-totp__info-card">
                            <div className="settings-totp__info-title">
                                <Info size={16} aria-hidden />
                                <span>{t('settings.totpWhyTitle', 'Neden İki Aşamalı Doğrulama?')}</span>
                            </div>
                            <p className="settings-totp__info-text">
                                {t(
                                    'settings.totpWhyBody',
                                    'Hesabınızın güvenliğini artırmak için iki aşamalı doğrulama kullanmanız önerilir. Bu özellik, hesabınıza izinsiz erişim riskini önemli ölçüde azaltır.'
                                )}
                            </p>
                        </div>
                    </div>
                ) : null}
                {content}
            </section>
        );
    }

    return (
        <SettingsCard
            className="settings-card--security"
            title={t('settings.totpTitle', 'İki Aşamalı Doğrulama')}
            subtitle={t(
                'settings.totpSubtitle',
                'Google Authenticator veya benzeri uygulama ile hesabınızı koruyun.'
            )}
        >
            {content}
        </SettingsCard>
    );
}
