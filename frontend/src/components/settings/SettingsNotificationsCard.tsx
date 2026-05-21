import type { ReactNode } from 'react';
import { Bell, FileText, LineChart, Mail, Megaphone } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import type { NotificationPreferenceKey, NotificationPreferences } from '../../hooks/useNotificationPreferences';
import { SettingsCard } from './SettingsCard';
import { SettingsToggle } from './SettingsToggle';

type SettingsNotificationsCardProps = {
    prefs: NotificationPreferences;
    onChange: (key: NotificationPreferenceKey, value: boolean) => void;
    disabled?: boolean;
};

export function SettingsNotificationsCard({ prefs, onChange, disabled }: SettingsNotificationsCardProps) {
    const { t } = useLanguage();

    const rows: {
        key: NotificationPreferenceKey;
        label: string;
        description: string;
        icon: ReactNode;
    }[] = [
        {
            key: 'email',
            label: t('settings.notifEmail', 'E-posta Bildirimleri'),
            description: t(
                'settings.notifEmailDesc',
                'Hesabınızla ilgili önemli bildirimleri e-posta ile alın.',
            ),
            icon: <Mail size={18} />,
        },
        {
            key: 'app',
            label: t('settings.notifApp', 'Uygulama Bildirimleri'),
            description: t('settings.notifAppDesc', 'Tarayıcı üzerinden anlık bildirimler alın.'),
            icon: <Bell size={18} />,
        },
        {
            key: 'marketAlerts',
            label: t('settings.notifMarket', 'Piyasa Uyarıları'),
            description: t(
                'settings.notifMarketDesc',
                'Fiyat alarmı ve piyasa hareketleri için uyarılar alın.',
            ),
            icon: <LineChart size={18} />,
        },
        {
            key: 'weeklyReports',
            label: t('settings.notifWeekly', 'Haftalık Raporlar'),
            description: t(
                'settings.notifWeeklyDesc',
                'Portföy ve piyasa özetlerinizi haftalık olarak e-posta ile alın.',
            ),
            icon: <FileText size={18} />,
        },
        {
            key: 'announcements',
            label: t('settings.notifAnnouncements', 'Duyurular'),
            description: t(
                'settings.notifAnnouncementsDesc',
                'Yeni özellikler ve sistem duyuruları hakkında bilgilendirilin.',
            ),
            icon: <Megaphone size={18} />,
        },
    ];

    return (
        <SettingsCard
            className="settings-card--notifications"
            title={t('settings.notificationsTitle', 'Bildirim Ayarları')}
            subtitle={t('settings.notificationsSubtitle', 'Hangi bildirimleri almak istediğinizi seçin.')}
        >
            <div className="settings-toggle-list">
                {rows.map((row) => (
                    <SettingsToggle
                        key={row.key}
                        checked={prefs[row.key]}
                        onChange={(v) => onChange(row.key, v)}
                        label={row.label}
                        description={row.description}
                        icon={row.icon}
                        disabled={disabled}
                    />
                ))}
            </div>
            <p className="settings-hint">
                {t(
                    'settings.notificationsHint',
                    'Tercihler bu cihazda saklanır; e-posta ve haftalık raporlar için sunucu entegrasyonu yakında eklenecek.',
                )}
            </p>
        </SettingsCard>
    );
}
