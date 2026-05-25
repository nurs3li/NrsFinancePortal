import { useMemo, useState, type CSSProperties } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import {
    EmailChangeModal,
    FullNameEditModal,
    PasswordChangeModal,
    UsernameEditModal,
} from '../components/settings/ProfileEditModals';
import { SettingsProfileCard } from '../components/settings/SettingsProfileCard';
import { SettingsTwoFactorCard } from '../components/settings/SettingsTwoFactorCard';
import { fetchCurrentUser, formatUserFullName } from '../services/userApi';
import { readKeycloakDisplayName } from '../utils/keycloakProfile';
import './UserSettings.css';

function formatMemberSince(iso: string | null | undefined, locale: string): string {
    if (!iso) return '—';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '—';
    return d.toLocaleDateString(locale, { day: 'numeric', month: 'long', year: 'numeric' });
}

type EditModal = 'username' | 'fullName' | 'email' | 'password' | null;

export function UserSettings() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const { user: authUser } = useAuth();
    const queryClient = useQueryClient();
    const locale = lang === 'en' ? 'en-GB' : 'tr-TR';

    const { data: me, isLoading } = useQuery({
        queryKey: ['users', 'me'],
        queryFn: fetchCurrentUser,
        staleTime: 60_000,
    });

    const [activeModal, setActiveModal] = useState<EditModal>(null);

    const displayName = useMemo(() => {
        return formatUserFullName(me) ?? readKeycloakDisplayName();
    }, [me]);

    const memberSinceLabel = formatMemberSince(me?.createdAt ?? null, locale);
    const email = me?.email ?? authUser?.email ?? '';

    const refreshProfile = () => {
        void queryClient.invalidateQueries({ queryKey: ['users', 'me'] });
    };

    const pageVars = {
        '--page-bg': tokens.bg,
        '--header-card': tokens.bgCard,
        '--header-card-text': tokens.text,
        '--header-card-muted': tokens.textMuted,
        '--header-border': tokens.border,
        '--header-accent': tokens.accent,
    } as CSSProperties;

    return (
        <div className="settings-page" style={pageVars}>
            <header className="settings-page__header">
                <h1 className="settings-page__title">{t('settings.pageTitle', 'Hesap Ayarları')}</h1>
                <p className="settings-page__subtitle">
                    {t('settings.pageSubtitle', 'Hesap bilgilerinizi görüntüleyin ve güncelleyin.')}
                </p>
            </header>

            <div className="settings-layout">
                <div className="settings-layout__profile">
                    <SettingsProfileCard
                        user={me ?? null}
                        displayName={displayName}
                        email={email}
                        memberSinceLabel={memberSinceLabel}
                        loading={isLoading}
                        onEditUsername={() => setActiveModal('username')}
                        onEditEmail={() => setActiveModal('email')}
                        onEditFullName={() => setActiveModal('fullName')}
                        onEditPassword={() => setActiveModal('password')}
                    />
                </div>

                <div className="settings-layout__side">
                    <SettingsTwoFactorCard disabled={me?.id == null && authUser?.id == null} />
                </div>
            </div>

            <UsernameEditModal
                open={activeModal === 'username'}
                initialValue={me?.username ?? ''}
                onClose={() => setActiveModal(null)}
                onSaved={refreshProfile}
            />
            <FullNameEditModal
                open={activeModal === 'fullName'}
                firstName={me?.firstName ?? ''}
                lastName={me?.lastName ?? ''}
                onClose={() => setActiveModal(null)}
                onSaved={refreshProfile}
            />
            <EmailChangeModal
                open={activeModal === 'email'}
                currentEmail={email}
                onClose={() => setActiveModal(null)}
                onSaved={refreshProfile}
            />
            <PasswordChangeModal
                open={activeModal === 'password'}
                onClose={() => setActiveModal(null)}
                onSaved={refreshProfile}
            />
        </div>
    );
}
