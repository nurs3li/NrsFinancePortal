import { useLanguage } from '../../i18n/LanguageContext';

export type InboxHubSegment = 'notifications' | 'alarms';

type InboxHubTabsProps = {
    value: InboxHubSegment;
    onChange: (value: InboxHubSegment) => void;
    variant?: 'header' | 'page';
    className?: string;
};

export function InboxHubTabs({ value, onChange, variant = 'page', className = '' }: InboxHubTabsProps) {
    const { t } = useLanguage();
    const rootClass = `inbox-hub-tabs inbox-hub-tabs--${variant}${className ? ` ${className}` : ''}`;

    return (
        <div className={rootClass} role="tablist" aria-label={t('inbox.hubLabel', 'Bildirimler ve alarmlar')}>
            <button
                type="button"
                role="tab"
                aria-selected={value === 'notifications'}
                className={`inbox-hub-tabs__btn${value === 'notifications' ? ' is-active' : ''}`}
                onClick={() => onChange('notifications')}
            >
                {t('nav.notifications', 'Bildirimler')}
            </button>
            <button
                type="button"
                role="tab"
                aria-selected={value === 'alarms'}
                className={`inbox-hub-tabs__btn${value === 'alarms' ? ' is-active' : ''}`}
                onClick={() => onChange('alarms')}
            >
                {t('alarms.title', 'Alarmlar')}
            </button>
            <span className="inbox-hub-tabs__indicator" aria-hidden data-active={value} />
        </div>
    );
}
