import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { X } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import {
    changePassword,
    confirmEmailChange,
    readProfileApiError,
    requestEmailChangeCode,
    updateProfileNames,
    updateUsername,
} from '../../services/userApi';
import keycloak from '../../auth/keycloak';

type ModalShellProps = {
    open: boolean;
    title: string;
    onClose: () => void;
    children: ReactNode;
    busy?: boolean;
};

function ModalShell({ open, title, onClose, children, busy }: ModalShellProps) {
    if (!open) return null;
    return (
        <div className="settings-modal-overlay" role="presentation" onClick={busy ? undefined : onClose}>
            <div
                className="settings-modal"
                role="dialog"
                aria-modal="true"
                aria-labelledby="settings-modal-title"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="settings-modal__head">
                    <h3 id="settings-modal-title">{title}</h3>
                    <button type="button" className="settings-modal__close" onClick={onClose} disabled={busy} aria-label="Kapat">
                        <X size={18} aria-hidden />
                    </button>
                </div>
                {children}
            </div>
        </div>
    );
}

async function refreshKeycloakToken(): Promise<void> {
    try {
        await keycloak.updateToken(30);
    } catch {
        /* session refresh optional */
    }
}

type UsernameModalProps = {
    open: boolean;
    initialValue: string;
    onClose: () => void;
    onSaved: () => void;
};

export function UsernameEditModal({ open, initialValue, onClose, onSaved }: UsernameModalProps) {
    const { t } = useLanguage();
    const [value, setValue] = useState(initialValue);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (open) {
            setValue(initialValue);
            setError(null);
        }
    }, [open, initialValue]);

    const submit = async (e: FormEvent) => {
        e.preventDefault();
        setError(null);
        setBusy(true);
        try {
            await updateUsername(value.trim());
            await refreshKeycloakToken();
            onSaved();
            onClose();
        } catch (err: unknown) {
            setError(readProfileApiError(err, t('settings.saveFailed', 'Kaydedilemedi.')));
        } finally {
            setBusy(false);
        }
    };

    return (
        <ModalShell open={open} title={t('settings.editUsername', 'Kullanıcı adını değiştir')} onClose={onClose} busy={busy}>
            <form className="settings-modal__form" onSubmit={submit}>
                <label className="settings-modal__label">
                    {t('settings.username', 'Kullanıcı Adı')}
                    <input
                        className="settings-modal__input"
                        value={value}
                        onChange={(e) => setValue(e.target.value)}
                        autoComplete="username"
                        required
                        minLength={3}
                        maxLength={32}
                    />
                </label>
                {error ? <p className="settings-modal__error">{error}</p> : null}
                <div className="settings-modal__actions">
                    <button type="button" className="settings-modal__btn settings-modal__btn--ghost" onClick={onClose} disabled={busy}>
                        {t('common.cancel', 'İptal')}
                    </button>
                    <button type="submit" className="settings-modal__btn settings-modal__btn--primary" disabled={busy}>
                        {busy ? t('settings.saving', 'Kaydediliyor…') : t('common.save', 'Kaydet')}
                    </button>
                </div>
            </form>
        </ModalShell>
    );
}

type FullNameModalProps = {
    open: boolean;
    firstName: string;
    lastName: string;
    onClose: () => void;
    onSaved: () => void;
};

export function FullNameEditModal({ open, firstName, lastName, onClose, onSaved }: FullNameModalProps) {
    const { t } = useLanguage();
    const [first, setFirst] = useState(firstName);
    const [last, setLast] = useState(lastName);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (open) {
            setFirst(firstName);
            setLast(lastName);
            setError(null);
        }
    }, [open, firstName, lastName]);

    const submit = async (e: FormEvent) => {
        e.preventDefault();
        setError(null);
        setBusy(true);
        try {
            await updateProfileNames(first.trim(), last.trim());
            await refreshKeycloakToken();
            onSaved();
            onClose();
        } catch (err: unknown) {
            setError(readProfileApiError(err, t('settings.saveFailed', 'Kaydedilemedi.')));
        } finally {
            setBusy(false);
        }
    };

    return (
        <ModalShell open={open} title={t('settings.editFullName', 'Ad soyadı değiştir')} onClose={onClose} busy={busy}>
            <form className="settings-modal__form" onSubmit={submit}>
                <label className="settings-modal__label">
                    {t('settings.firstName', 'Ad')}
                    <input className="settings-modal__input" value={first} onChange={(e) => setFirst(e.target.value)} maxLength={128} />
                </label>
                <label className="settings-modal__label">
                    {t('settings.lastName', 'Soyad')}
                    <input className="settings-modal__input" value={last} onChange={(e) => setLast(e.target.value)} maxLength={128} />
                </label>
                {error ? <p className="settings-modal__error">{error}</p> : null}
                <div className="settings-modal__actions">
                    <button type="button" className="settings-modal__btn settings-modal__btn--ghost" onClick={onClose} disabled={busy}>
                        {t('common.cancel', 'İptal')}
                    </button>
                    <button type="submit" className="settings-modal__btn settings-modal__btn--primary" disabled={busy}>
                        {busy ? t('settings.saving', 'Kaydediliyor…') : t('common.save', 'Kaydet')}
                    </button>
                </div>
            </form>
        </ModalShell>
    );
}

const EMAIL_CODE_VALID_SEC = 60;

type EmailModalProps = {
    open: boolean;
    currentEmail: string;
    onClose: () => void;
    onSaved: () => void;
};

export function EmailChangeModal({ open, currentEmail, onClose, onSaved }: EmailModalProps) {
    const { t } = useLanguage();
    const [email, setEmail] = useState('');
    const [code, setCode] = useState('');
    const [codeSent, setCodeSent] = useState(false);
    const [secondsLeft, setSecondsLeft] = useState(0);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    const codeExpired = codeSent && secondsLeft <= 0;

    useEffect(() => {
        if (open) {
            setEmail('');
            setCode('');
            setCodeSent(false);
            setSecondsLeft(0);
            setMessage(null);
            setError(null);
        }
    }, [open]);

    useEffect(() => {
        if (!codeSent) return;
        const timer = window.setInterval(() => {
            setSecondsLeft((s) => (s <= 0 ? 0 : s - 1));
        }, 1000);
        return () => window.clearInterval(timer);
    }, [codeSent]);

    const sendCode = async () => {
        setError(null);
        setMessage(null);
        if (!email.trim()) {
            setError(t('settings.emailRequired', 'Yeni e-posta girin.'));
            return;
        }
        setBusy(true);
        try {
            const msg = await requestEmailChangeCode(email.trim());
            setCodeSent(true);
            setSecondsLeft(EMAIL_CODE_VALID_SEC);
            setCode('');
            setMessage(
                msg ||
                    t(
                        'settings.codeSentCountdown',
                        'Doğrulama kodu gönderildi. Kodu 60 saniye içinde girin.'
                    )
            );
        } catch (err: unknown) {
            setCodeSent(false);
            setSecondsLeft(0);
            setError(readProfileApiError(err, t('settings.codeSendFailed', 'Kod gönderilemedi.')));
        } finally {
            setBusy(false);
        }
    };

    const confirm = async (e: FormEvent) => {
        e.preventDefault();
        setError(null);
        setMessage(null);
        if (!email.trim() || !code.trim()) {
            setError(t('settings.emailCodeRequired', 'E-posta ve doğrulama kodu gerekli.'));
            return;
        }
        if (codeExpired) {
            setError(t('settings.codeExpired', 'Kod süresi doldu. Yeni kod gönderin.'));
            return;
        }
        if (code.trim().length !== 6) {
            setError(t('settings.codeSixDigits', 'Kod 6 haneli olmalıdır.'));
            return;
        }
        setBusy(true);
        try {
            await confirmEmailChange(email.trim(), code.trim());
            await refreshKeycloakToken();
            onSaved();
            onClose();
        } catch (err: unknown) {
            setError(readProfileApiError(err, t('settings.emailConfirmFailed', 'E-posta doğrulanamadı.')));
        } finally {
            setBusy(false);
        }
    };

    return (
        <ModalShell open={open} title={t('settings.editEmail', 'E-posta adresini değiştir')} onClose={onClose} busy={busy}>
            <form className="settings-modal__form" onSubmit={confirm}>
                <p className="settings-modal__hint">
                    {t('settings.currentEmail', 'Mevcut E-posta')}: <strong>{currentEmail || '—'}</strong>
                </p>

                <label className="settings-modal__label">
                    {t('settings.newEmail', 'Yeni E-posta')}
                    <input
                        className="settings-modal__input"
                        type="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        autoComplete="email"
                        disabled={codeSent && !codeExpired}
                        required
                    />
                </label>

                {codeSent ? (
                    <div className="settings-email-verify-block">
                        <label className="settings-modal__label">
                            {t('settings.verificationCode', 'Doğrulama Kodu')}
                            <input
                                className="settings-modal__input settings-modal__input--code"
                                value={code}
                                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                                inputMode="numeric"
                                pattern="\d{6}"
                                maxLength={6}
                                placeholder="000000"
                                autoFocus
                                required
                                disabled={codeExpired}
                            />
                        </label>
                        <p
                            className={`settings-modal__timer${codeExpired ? ' settings-modal__timer--expired' : ''}`}
                            role="status"
                        >
                            {codeExpired
                                ? t('settings.codeExpired', 'Kod süresi doldu. Yeni kod gönderin.')
                                : t('settings.codeTimer', 'Kalan süre: {sec} sn').replace(
                                      '{sec}',
                                      String(secondsLeft)
                                  )}
                        </p>
                    </div>
                ) : (
                    <p className="settings-modal__hint settings-modal__hint--step">
                        {t('settings.emailStepHint', 'Kod gönderildikten sonra alttaki alana 6 haneli kodu yazıp onaylayın.')}
                    </p>
                )}

                {message ? <p className="settings-modal__success">{message}</p> : null}
                {error ? <p className="settings-modal__error">{error}</p> : null}

                <div className="settings-modal__actions">
                    <button type="button" className="settings-modal__btn settings-modal__btn--ghost" onClick={onClose} disabled={busy}>
                        {t('common.cancel', 'İptal')}
                    </button>
                    {!codeSent || codeExpired ? (
                        <button type="button" className="settings-modal__btn settings-modal__btn--primary" onClick={sendCode} disabled={busy}>
                            {busy
                                ? t('settings.sending', 'Gönderiliyor…')
                                : codeExpired
                                  ? t('settings.resendCode', 'Yeni Kod Gönder')
                                  : t('settings.sendCode', 'Kod Gönder')}
                        </button>
                    ) : (
                        <button
                            type="submit"
                            className="settings-modal__btn settings-modal__btn--primary"
                            disabled={busy || code.length !== 6}
                        >
                            {busy ? t('settings.saving', 'Kaydediliyor…') : t('settings.verifyEmail', 'Doğrula')}
                        </button>
                    )}
                </div>
            </form>
        </ModalShell>
    );
}

type PasswordModalProps = {
    open: boolean;
    onClose: () => void;
    onSaved: () => void;
};

export function PasswordChangeModal({ open, onClose, onSaved }: PasswordModalProps) {
    const { t } = useLanguage();
    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [message, setMessage] = useState<string | null>(null);

    useEffect(() => {
        if (open) {
            setCurrentPassword('');
            setNewPassword('');
            setConfirmPassword('');
            setError(null);
            setMessage(null);
        }
    }, [open]);

    const submit = async (e: FormEvent) => {
        e.preventDefault();
        setError(null);
        setMessage(null);
        if (!currentPassword || !newPassword) {
            setError(t('settings.passwordFieldsRequired', 'Tüm şifre alanlarını doldurun.'));
            return;
        }
        if (newPassword !== confirmPassword) {
            setError(t('landing.passwordMismatch', 'Şifreler eşleşmiyor.'));
            return;
        }
        setBusy(true);
        try {
            const msg = await changePassword(currentPassword, newPassword);
            setMessage(msg || t('settings.passwordChanged', 'Şifre güncellendi.'));
            onSaved();
            setTimeout(onClose, 600);
        } catch (err: unknown) {
            setError(readProfileApiError(err, t('settings.passwordChangeFailed', 'Şifre güncellenemedi.')));
        } finally {
            setBusy(false);
        }
    };

    return (
        <ModalShell open={open} title={t('settings.editPassword', 'Şifreyi değiştir')} onClose={onClose} busy={busy}>
            <form className="settings-modal__form" onSubmit={submit}>
                <label className="settings-modal__label">
                    {t('settings.currentPassword', 'Mevcut Şifre')}
                    <input
                        className="settings-modal__input"
                        type="password"
                        value={currentPassword}
                        onChange={(e) => setCurrentPassword(e.target.value)}
                        autoComplete="current-password"
                        required
                    />
                </label>
                <label className="settings-modal__label">
                    {t('settings.newPassword', 'Yeni Şifre')}
                    <input
                        className="settings-modal__input"
                        type="password"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        autoComplete="new-password"
                        minLength={8}
                        required
                    />
                </label>
                <label className="settings-modal__label">
                    {t('settings.confirmPassword', 'Yeni Şifre (Tekrar)')}
                    <input
                        className="settings-modal__input"
                        type="password"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        autoComplete="new-password"
                        minLength={8}
                        required
                    />
                </label>
                {message ? <p className="settings-modal__success">{message}</p> : null}
                {error ? <p className="settings-modal__error">{error}</p> : null}
                <div className="settings-modal__actions">
                    <button type="button" className="settings-modal__btn settings-modal__btn--ghost" onClick={onClose} disabled={busy}>
                        {t('common.cancel', 'İptal')}
                    </button>
                    <button type="submit" className="settings-modal__btn settings-modal__btn--primary" disabled={busy}>
                        {busy ? t('settings.saving', 'Kaydediliyor…') : t('common.save', 'Kaydet')}
                    </button>
                </div>
            </form>
        </ModalShell>
    );
}
