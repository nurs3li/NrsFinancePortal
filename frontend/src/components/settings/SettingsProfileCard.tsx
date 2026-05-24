import type { ReactNode } from 'react';
import { Pencil } from 'lucide-react';
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
};

function FieldRow({
    label,
    value,
    editLabel,
    onEdit,
    children,
}: {
    label: string;
    value?: string;
    editLabel: string;
    onEdit?: () => void;
    children?: ReactNode;
}) {
    return (
        <div className="settings-field-row">
            <div className="settings-field">
                <span className="settings-field__label">{label}</span>
                {children ?? <span className="settings-field__value">{value ?? '—'}</span>}
            </div>
            {onEdit ? (
                <button
                    type="button"
                    className="settings-field-edit"
                    onClick={onEdit}
                    aria-label={editLabel}
                    title={editLabel}
                >
                    <Pencil size={15} aria-hidden />
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
}: SettingsProfileCardProps) {
    const { t } = useLanguage();

    const editUsername = onEditUsername;
    const editEmail = onEditEmail;
    const editFullName = onEditFullName;
    const editPassword = onEditPassword;

    return (
        <SettingsCard className="settings-card--profile" title={t('settings.profileTitle', 'Profil Bilgileri')}>
            {loading ? (
                <p className="settings-muted">{t('settings.loading', 'Yükleniyor…')}</p>
            ) : (
                <div className="settings-profile-grid">
                    <FieldRow
                        label={t('settings.username', 'Kullanıcı Adı')}
                        value={user?.username}
                        editLabel={t('settings.editUsername', 'Kullanıcı adını değiştir')}
                        onEdit={editUsername}
                    />
                    <FieldRow
                        label={t('settings.email', 'E-posta')}
                        value={email || user?.email}
                        editLabel={t('settings.editEmail', 'E-posta adresini değiştir')}
                        onEdit={editEmail}
                    />
                    <FieldRow
                        label={t('settings.fullName', 'Ad Soyad')}
                        value={displayName ?? '—'}
                        editLabel={t('settings.editFullName', 'Ad soyadı değiştir')}
                        onEdit={editFullName}
                    />
                    <FieldRow
                        label={t('settings.password', 'Şifre')}
                        value="••••••••••"
                        editLabel={t('settings.editPassword', 'Şifreyi değiştir')}
                        onEdit={editPassword}
                    />
                    <div className="settings-field-row settings-field-row--static">
                        <div className="settings-field">
                            <span className="settings-field__label">{t('settings.role', 'Üyelik Rolü')}</span>
                            <span className="settings-role-badge">{user?.role ?? 'USER'}</span>
                        </div>
                    </div>
                    <div className="settings-field-row settings-field-row--static">
                        <div className="settings-field">
                            <span className="settings-field__label">{t('settings.memberSince', 'Üyelik Tarihi')}</span>
                            <span className="settings-field__value">{memberSinceLabel}</span>
                        </div>
                    </div>
                </div>
            )}
        </SettingsCard>
    );
}
