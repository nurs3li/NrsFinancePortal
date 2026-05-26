import type { ReactNode } from 'react';
import { CalendarDays, LockKeyhole, Mail, User as UserIcon } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import type { UserMeDto } from '../../services/userApi';
import { SettingsCard } from './SettingsCard';

type SettingsProfileCardProps = {
    user: UserMeDto | null;
    displayName: string | null;
    email: string;
    memberSinceLabel: string;
    loading?: boolean;
    onEditUsername?: () => void;
    onEditEmail?: () => void;
    onEditFullName?: () => void;
    onEditPassword?: () => void;
    embedded?: boolean;
};

function FieldRow({
    label,
    value,
    editLabel,
    onEdit,
    children,
    icon,
}: {
    label: string;
    value?: string;
    editLabel: string;
    onEdit?: () => void;
    children?: ReactNode;
    icon: ReactNode;
}) {
    return (
        <div className="settings-field-row">
            <div className="settings-field-row__icon" aria-hidden>
                {icon}
            </div>
            <div className="settings-field">
                <span className="settings-field__label">{label}</span>
                {children ?? <span className="settings-field__value">{value ?? '—'}</span>}
            </div>
            {onEdit ? (
                <button
                    type="button"
                    className="settings-field-action"
                    onClick={onEdit}
                    aria-label={editLabel}
                    title={editLabel}
                >
                    {editLabel}
                </button>
            ) : null}
        </div>
    );
}

export function SettingsProfileCard({
    user,
    displayName,
    email,
    memberSinceLabel,
    loading,
    onEditUsername,
    onEditEmail,
    onEditFullName,
    onEditPassword,
    embedded = false,
}: SettingsProfileCardProps) {
    const { t } = useLanguage();

    const editUsername = onEditUsername;
    const editEmail = onEditEmail;
    const editFullName = onEditFullName;
    const editPassword = onEditPassword;

    const content = (
        <>
            {loading ? (
                <p className="settings-muted">{t('settings.loading', 'Yükleniyor…')}</p>
            ) : (
                <div className="settings-profile-grid">
                    <FieldRow
                        label={t('settings.username', 'Kullanıcı Adı')}
                        value={user?.username}
                        editLabel={t('settings.editAction', 'Düzenle')}
                        onEdit={editUsername}
                        icon={<UserIcon size={17} strokeWidth={1.8} />}
                    />
                    <FieldRow
                        label={t('settings.email', 'E-posta')}
                        value={email || user?.email}
                        editLabel={t('settings.editAction', 'Düzenle')}
                        onEdit={editEmail}
                        icon={<Mail size={17} strokeWidth={1.8} />}
                    />
                    <FieldRow
                        label={t('settings.fullName', 'Ad Soyad')}
                        value={displayName ?? '—'}
                        editLabel={t('settings.editAction', 'Düzenle')}
                        onEdit={editFullName}
                        icon={<UserIcon size={17} strokeWidth={1.8} />}
                    />
                    <FieldRow
                        label={t('settings.password', 'Şifre')}
                        value="••••••••••"
                        editLabel={t('settings.editAction', 'Düzenle')}
                        onEdit={editPassword}
                        icon={<LockKeyhole size={17} strokeWidth={1.8} />}
                    />
                    <div className="settings-field-row settings-field-row--static">
                        <div className="settings-field-row__icon" aria-hidden>
                            <CalendarDays size={17} strokeWidth={1.8} />
                        </div>
                        <div className="settings-field">
                            <span className="settings-field__label">{t('settings.memberSince', 'Üyelik Tarihi')}</span>
                            <span className="settings-field__value">{memberSinceLabel}</span>
                        </div>
                    </div>
                </div>
            )}
        </>
    );

    if (embedded) {
        return (
            <section className="settings-panel settings-panel--profile">
                <header className="settings-panel__head">
                    <h2 className="settings-panel__title">{t('settings.profileTitle', 'Profil Bilgileri')}</h2>
                </header>
                {content}
            </section>
        );
    }

    return (
        <SettingsCard className="settings-card--profile" title={t('settings.profileTitle', 'Profil Bilgileri')}>
            {content}
        </SettingsCard>
    );
}
