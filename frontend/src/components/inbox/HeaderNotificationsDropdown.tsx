import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Bell } from 'lucide-react';
import { notificationClient } from '../../api/client';
import { useLanguage } from '../../i18n/LanguageContext';
import { notificationKeys } from '../../queries/notificationKeys';
import { priceAlertKeys } from '../../queries/priceAlertKeys';
import { listPriceAlerts } from '../../services/priceAlertApi';
import { formatPriceAlertMeta, formatPriceAlertTitle } from '../../utils/priceAlertDisplay';
import { isPriceAlertNotificationType, priceAlertNotificationTypeLabel } from '../../utils/priceAlertNotifications';
import { portfolioInsightNotificationTypeLabel } from '../../utils/portfolioInsightNotifications';
import { InboxHubTabs, type InboxHubSegment } from './InboxHubTabs';
import './inboxHub.css';

const HEADER_PREVIEW_SIZE = 5;

type HeaderNotificationsDropdownProps = {
    unreadCount: number;
    isOpen: boolean;
    onToggle: (e: React.MouseEvent) => void;
    onMarkRead: (id: number) => void;
    wrapRef: React.RefObject<HTMLDivElement | null>;
};

export function HeaderNotificationsDropdown({
    unreadCount,
    isOpen,
    onToggle,
    onMarkRead,
    wrapRef,
}: HeaderNotificationsDropdownProps) {
    const { t, lang } = useLanguage();
    const navigate = useNavigate();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const [segment, setSegment] = useState<InboxHubSegment>('notifications');

    const { data: notificationItems = [] } = useQuery({
        queryKey: [...notificationKeys.headerDropdown(), 'preview'],
        queryFn: async () => {
            const res = await notificationClient.get<{
                content: { id: number; title: string; readAt: string | null; type: string }[];
            }>('/api/notifications/me', {
                params: {
                    size: HEADER_PREVIEW_SIZE,
                    unreadOnly: false,
                    sort: ['lastOccurredAt,desc', 'createdAt,desc'],
                },
            });
            return res.data?.content ?? [];
        },
        enabled: isOpen && segment === 'notifications',
        staleTime: 20_000,
    });

    const { data: alertItems = [] } = useQuery({
        queryKey: [...priceAlertKeys.list(), 'header-preview'],
        queryFn: async () => {
            const all = await listPriceAlerts();
            return [...all]
                .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
                .slice(0, HEADER_PREVIEW_SIZE);
        },
        enabled: isOpen && segment === 'alarms',
        staleTime: 30_000,
    });

    return (
        <div ref={wrapRef} className="header-control-wrap">
            <button type="button" className="header-icon-btn" aria-label={t('inbox.open', 'Bildirimleri aç')} onClick={onToggle}>
                <Bell size={17} />
                {unreadCount > 0 && (
                    <span className="header-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>
                )}
            </button>
            {isOpen && (
                <div className="header-dropdown header-dropdown--notifications">
                    <div className="header-dropdown__head-inbox">
                        <InboxHubTabs variant="header" value={segment} onChange={setSegment} />
                    </div>

                    {segment === 'notifications' ? (
                        notificationItems.length === 0 ? (
                            <div className="header-dropdown__empty">{t('notifications.emptyShort', 'Bildirim yok')}</div>
                        ) : (
                            notificationItems.map((n) => (
                                <div
                                    key={n.id}
                                    className="header-dropdown__item"
                                    onClick={() => {
                                        onMarkRead(n.id);
                                        navigate('/notifications');
                                    }}
                                >
                                    <div className="header-dropdown__item-title">{n.title}</div>
                                    <div className="header-dropdown__item-meta">
                                        {isPriceAlertNotificationType(n.type)
                                            ? priceAlertNotificationTypeLabel(n.type, t)
                                            : portfolioInsightNotificationTypeLabel(n.type, t)}
                                    </div>
                                </div>
                            ))
                        )
                    ) : alertItems.length === 0 ? (
                        <div className="header-dropdown__empty">{t('alarms.emptyShort', 'Alarm yok')}</div>
                    ) : (
                        alertItems.map((a) => (
                            <div
                                key={a.id}
                                className="header-dropdown__item header-dropdown__alarm-item"
                                onClick={() => navigate('/notifications?alarmFilter=all')}
                            >
                                <div className="header-dropdown__item-title">{formatPriceAlertTitle(a)}</div>
                                <div className="header-dropdown__item-meta">{formatPriceAlertMeta(a, t, locale)}</div>
                            </div>
                        ))
                    )}

                    <Link
                        to={segment === 'alarms' ? '/notifications?alarmFilter=all' : '/notifications'}
                        className="header-dropdown__footer"
                    >
                        {t('inbox.viewAll', 'Tümünü gör')}
                    </Link>
                </div>
            )}
        </div>
    );
}
