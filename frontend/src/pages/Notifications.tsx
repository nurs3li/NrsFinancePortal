import { useState, useEffect, useCallback, useMemo, type CSSProperties } from 'react';
import {
    Bell,
    CheckCircle2,
    ShieldAlert,
    Info,
    ChevronRight,
    X,
} from 'lucide-react';
import { notificationClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { useLanguage } from '../i18n/LanguageContext';
import { portfolioInsightNotificationTypeLabel } from '../utils/portfolioInsightNotifications';
import {
    isPriceAlertNotificationType,
    priceAlertNotificationTypeLabel,
} from '../utils/priceAlertNotifications';
import './Notifications.css';

type NotificationItem = {
    id: number;
    title: string;
    body: string | null;
    type: string;
    readAt: string | null;
    createdAt: string;
    lastOccurredAt: string | null;
    referenceType: string | null;
    referenceId: number | null;
    occurrenceCount: number;
};

// Spring Data 3.3+ VIA_DTO shape (bkz. NotificationServiceApplication).
type PageResponse = {
    content: NotificationItem[];
    page: {
        size: number;
        number: number;
        totalElements: number;
        totalPages: number;
    };
};

type FilterTab = 'ALL' | 'UNREAD';

/**
 * Bildirim turune gore kategori cikariyoruz; mikro ikon ve renk kodu bu bilgiden uretilir.
 * Backend her notification icin tek bir `type` string'i tutuyor (orn. USER_REGISTERED,
 * SUSPICIOUS_TXN, FUND_REQUEST_APPROVED ...) — burada anahtar kelimelerle gruplayip
 * UI'da tutarli bir renk-anlam haritasi sunuyoruz.
 */
type NotifCategory = 'APPROVAL' | 'SECURITY' | 'REVIEW' | 'INFO';

function classifyNotification(type: string): NotifCategory {
    const t = (type ?? '').toUpperCase();
    if (t === 'REAL_RETURN_NEGATIVE' || t === 'PORTFOLIO_CONCENTRATION_RISK') {
        return 'SECURITY';
    }
    if (t === 'REAL_RETURN_POSITIVE') {
        return 'APPROVAL';
    }
    if (
        t.includes('APPROVED') ||
        t.includes('APPROVAL') ||
        t.includes('SUCCESS') ||
        t.includes('COMPLETED') ||
        t.includes('CONFIRM') ||
        t.includes('ONAY')
    ) {
        return 'APPROVAL';
    }
    if (
        t.includes('SUSPICIOUS') ||
        t.includes('SECURITY') ||
        t.includes('RISK') ||
        t.includes('REJECT') ||
        t.includes('FAIL') ||
        t.includes('FRAUD') ||
        t.includes('BLOCK') ||
        t.includes('FROZEN') ||
        t.includes('GUVENL')
    ) {
        return 'SECURITY';
    }
    if (
        t.includes('REVIEW') ||
        t.includes('PENDING') ||
        t.includes('REGISTERED') ||
        t.includes('REQUEST') ||
        t.includes('TASK') ||
        t.includes('INCELE')
    ) {
        return 'REVIEW';
    }
    return 'INFO';
}

function categoryColor(category: NotifCategory): string {
    switch (category) {
        case 'APPROVAL':
            return '#22c55e';
        case 'SECURITY':
            return '#ef4444';
        case 'REVIEW':
            return '#38bdf8';
        default:
            return '#c0c0c0';
    }
}

function CategoryIcon({ category, size = 16 }: { category: NotifCategory; size?: number }) {
    switch (category) {
        case 'APPROVAL':
            return <CheckCircle2 size={size} aria-hidden />;
        case 'SECURITY':
            return <ShieldAlert size={size} aria-hidden />;
        case 'REVIEW':
            return <Info size={size} aria-hidden />;
        default:
            return <Bell size={size} aria-hidden />;
    }
}

export function Notifications() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const [items, setItems] = useState<NotificationItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const size = 20;
    const [filter, setFilter] = useState<FilterTab>('UNREAD');
    const [activeId, setActiveId] = useState<number | null>(null);

    const unreadOnly = filter === 'UNREAD';

    const fetchNotifications = useCallback(() => {
        setLoading(true);
        setError(null);
        notificationClient
            .get<PageResponse>('/api/notifications/me', {
                params: { page, size, unreadOnly, sort: ['lastOccurredAt,desc', 'createdAt,desc'] },
            })
            .then((res) => {
                const data = res.data;
                const content = data?.content ?? [];
                const normalized = Array.isArray(content) ? content : [];
                const sorted = [...normalized].sort((a, b) => {
                    const aTs = new Date(a.lastOccurredAt ?? a.createdAt ?? 0).getTime();
                    const bTs = new Date(b.lastOccurredAt ?? b.createdAt ?? 0).getTime();
                    return bTs - aTs;
                });
                setItems(sorted);
                setTotalPages(data?.page?.totalPages ?? 0);
                setTotalElements(data?.page?.totalElements ?? 0);
            })
            .catch((err) => {
                setError(
                    err.response?.data?.message ??
                        err.message ??
                        t('notifications.loadFailed', 'Bildirimler yüklenemedi'),
                );
            })
            .finally(() => setLoading(false));
        // t fonksiyonu LanguageProvider'da stable degil; deps'e koyarsak surekli fetch
        // tetiklenir. Sadece page/unreadOnly degisiminde tekrar cek.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [page, unreadOnly]);

    useEffect(() => {
        fetchNotifications();
    }, [fetchNotifications]);
    useRefetchOnFocus(fetchNotifications);

    const markAsRead = useCallback(
        (id: number) => {
            notificationClient
                .patch(`/api/notifications/${id}/read`)
                .then(() => {
                    setItems((prev) =>
                        prev.map((n) => (n.id === id ? { ...n, readAt: new Date().toISOString() } : n)),
                    );
                    fetchNotifications();
                })
                .catch(() => {
                    /* sessizce yut: kullanici tekrar deneyebilir */
                });
        },
        [fetchNotifications],
    );

    const activeNotification = useMemo(
        () => (activeId == null ? null : items.find((n) => n.id === activeId) ?? null),
        [activeId, items],
    );

    /**
     * Modal acilirken bildirimi otomatik okundu isaretliyoruz; backend zaten idempotent
     * davraniyor. Boylece kullanici sonradan ayrica "okundu" tiklamasiyla ugrasmiyor.
     */
    useEffect(() => {
        if (activeNotification && !activeNotification.readAt) {
            markAsRead(activeNotification.id);
        }
    }, [activeNotification, markAsRead]);

    // ESC ile modal kapansin
    useEffect(() => {
        if (activeId == null) return;
        const onKey = (event: KeyboardEvent) => {
            if (event.key === 'Escape') setActiveId(null);
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [activeId]);

    /**
     * CSS dosyasi tema-bagimli renkler icin `var(--notif-*)` kullaniyor.
     * Aydinlik/karanlik temalar arasinda kart arka plani vs. tutarli kalsin diye
     * tokens'i CSS custom property olarak page root'una baglaiyoruz.
     */
    const pageVars: CSSProperties = {
        '--notif-bg': tokens.bg,
        '--notif-text': tokens.text,
        '--notif-muted': tokens.textMuted,
    } as CSSProperties;

    if (error) {
        return (
            <div className="notif-page" style={pageVars}>
                <header className="notif-header">
                    <span className="notif-header__icon">
                        <Bell size={18} aria-hidden />
                    </span>
                    <h1 className="notif-header__title">
                        {t('notifications.title', 'Bildirimler')}
                    </h1>
                </header>
                <div className="notif-error">{error}</div>
            </div>
        );
    }

    return (
        <div className="notif-page" style={pageVars}>
            <header className="notif-header">
                <span className="notif-header__icon" aria-hidden>
                    <Bell size={18} />
                </span>
                <h1 className="notif-header__title">
                    {t('notifications.title', 'Bildirimler')}
                </h1>
                <span className="notif-header__count">
                    {totalElements} {t('notifications.item', 'bildirim')}
                </span>
            </header>

            <div className="notif-tabs" role="tablist" aria-label={t('notifications.filter', 'Filtre')}>
                <button
                    type="button"
                    role="tab"
                    aria-selected={filter === 'ALL'}
                    className={`notif-tab ${filter === 'ALL' ? 'is-active' : ''}`}
                    onClick={() => {
                        setFilter('ALL');
                        setPage(0);
                    }}
                >
                    {t('notifications.all', 'Tümü')}
                </button>
                <button
                    type="button"
                    role="tab"
                    aria-selected={filter === 'UNREAD'}
                    className={`notif-tab ${filter === 'UNREAD' ? 'is-active' : ''}`}
                    onClick={() => {
                        setFilter('UNREAD');
                        setPage(0);
                    }}
                >
                    {t('notifications.unread', 'Okunmamış')}
                </button>
            </div>

            {loading ? (
                <div className="notif-loading">{t('common.loading', 'Yükleniyor...')}</div>
            ) : items.length === 0 ? (
                <div className="notif-empty">{t('notifications.empty', 'Bildirim yok.')}</div>
            ) : (
                <div className="notif-list">
                    {items.map((n) => {
                        const category = classifyNotification(n.type);
                        const color = categoryColor(category);
                        const isUnread = !n.readAt;
                        const occurredAt = n.lastOccurredAt ?? n.createdAt;
                        return (
                            <div
                                key={n.id}
                                role="button"
                                tabIndex={0}
                                className={`notif-item ${isUnread ? 'is-unread' : 'is-read'}`}
                                onClick={() => setActiveId(n.id)}
                                onKeyDown={(event) => {
                                    if (event.key === 'Enter' || event.key === ' ') {
                                        event.preventDefault();
                                        setActiveId(n.id);
                                    }
                                }}
                            >
                                <span className="notif-item__type-icon" style={{ color }} aria-hidden>
                                    <CategoryIcon category={category} size={16} />
                                </span>
                                <div className="notif-item__main">
                                    <div className="notif-item__title">{n.title}</div>
                                    <div className="notif-item__meta">
                                        <span className="notif-item__type-chip">
                                            {isPriceAlertNotificationType(n.type)
                                                ? priceAlertNotificationTypeLabel(n.type, t)
                                                : portfolioInsightNotificationTypeLabel(n.type, t)}
                                        </span>
                                        <span className="notif-item__date">
                                            {new Date(occurredAt).toLocaleString(locale)}
                                        </span>
                                    </div>
                                </div>
                                {n.occurrenceCount > 1 ? (
                                    <span
                                        className="notif-item__count-badge"
                                        title={t('notifications.occurrenceCount', 'Tekrar sayısı')}
                                    >
                                        {n.occurrenceCount}x
                                    </span>
                                ) : (
                                    <span />
                                )}
                                <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                                    <span className="notif-item__hint">
                                        {t('notifications.viewDetail', 'Detayı gör')}
                                    </span>
                                    <ChevronRight size={16} className="notif-item__chevron" aria-hidden />
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}

            {totalPages > 1 && (
                <div className="notif-pagination">
                    <button
                        type="button"
                        className="notif-page-btn"
                        disabled={page <= 0}
                        onClick={() => setPage((p) => Math.max(0, p - 1))}
                    >
                        {t('news.prev', 'Önceki')}
                    </button>
                    <span className="notif-page-info">
                        {t('news.page', 'Sayfa')} {page + 1} / {totalPages}
                    </span>
                    <button
                        type="button"
                        className="notif-page-btn"
                        disabled={page >= totalPages - 1}
                        onClick={() => setPage((p) => p + 1)}
                    >
                        {t('news.next', 'Sonraki')}
                    </button>
                </div>
            )}

            {activeNotification ? (
                <NotificationDetailModal
                    item={activeNotification}
                    locale={locale}
                    onClose={() => setActiveId(null)}
                    t={t}
                />
            ) : null}
        </div>
    );
}

type NotificationDetailModalProps = {
    item: NotificationItem;
    locale: string;
    onClose: () => void;
    t: (key: string, fallback?: string) => string;
};

function NotificationDetailModal({ item, locale, onClose, t }: NotificationDetailModalProps) {
    const category = classifyNotification(item.type);
    const color = categoryColor(category);
    const occurredAt = item.lastOccurredAt ?? item.createdAt;
    return (
        <div
            className="notif-modal-backdrop"
            role="dialog"
            aria-modal="true"
            aria-labelledby={`notif-modal-title-${item.id}`}
            // Backdrop'a tiklayinca kapatsin; ic modal click stopPropagation ile korunuyor.
            onClick={onClose}
        >
            <div
                className="notif-modal"
                onClick={(event) => event.stopPropagation()}
            >
                <button
                    type="button"
                    className="notif-modal__close"
                    onClick={onClose}
                    aria-label={t('notifications.close', 'Kapat')}
                >
                    <X size={16} />
                </button>

                <div className="notif-modal__head">
                    <span className="notif-modal__head-icon" style={{ color }} aria-hidden>
                        <CategoryIcon category={category} size={20} />
                    </span>
                    <div>
                        <h2 className="notif-modal__title" id={`notif-modal-title-${item.id}`}>
                            {item.title}
                        </h2>
                        <div className="notif-modal__subtitle">
                            <span className="notif-item__type-chip">{item.type}</span>
                            <span>·</span>
                            <span>{new Date(occurredAt).toLocaleString(locale)}</span>
                            {item.occurrenceCount > 1 ? (
                                <>
                                    <span>·</span>
                                    <span>
                                        {item.occurrenceCount}x{' '}
                                        {t('notifications.repeat', 'tekrar')}
                                    </span>
                                </>
                            ) : null}
                        </div>
                    </div>
                </div>

                <hr className="notif-modal__divider" />

                {item.body ? (
                    <div className="notif-modal__body">{item.body}</div>
                ) : (
                    <div className="notif-modal__body" style={{ opacity: 0.65 }}>
                        {t('notifications.noBody', 'Bu bildirim için ek detay sağlanmadı.')}
                    </div>
                )}

                <div className="notif-modal__details">
                    <span className="notif-modal__detail-label">
                        {t('notifications.createdAt', 'İlk oluşma')}
                    </span>
                    <span className="notif-modal__detail-value">
                        {new Date(item.createdAt).toLocaleString(locale)}
                    </span>

                    {item.lastOccurredAt ? (
                        <>
                            <span className="notif-modal__detail-label">
                                {t('notifications.lastOccurredAt', 'Son tekrar')}
                            </span>
                            <span className="notif-modal__detail-value">
                                {new Date(item.lastOccurredAt).toLocaleString(locale)}
                            </span>
                        </>
                    ) : null}

                    {item.referenceType ? (
                        <>
                            <span className="notif-modal__detail-label">
                                {t('notifications.referenceType', 'Referans tipi')}
                            </span>
                            <span className="notif-modal__detail-value">{item.referenceType}</span>
                        </>
                    ) : null}

                    {item.referenceId != null ? (
                        <>
                            <span className="notif-modal__detail-label">
                                {t('notifications.referenceId', 'Referans No')}
                            </span>
                            <span className="notif-modal__detail-value">#{item.referenceId}</span>
                        </>
                    ) : null}

                    <span className="notif-modal__detail-label">
                        {t('notifications.status', 'Durum')}
                    </span>
                    <span className="notif-modal__detail-value" style={{ color }}>
                        {item.readAt
                            ? t('notifications.read', 'Okundu')
                            : t('notifications.unreadStatus', 'Okunmadı')}
                    </span>
                </div>

                <div className="notif-modal__actions">
                    <button
                        type="button"
                        className="notif-modal__btn notif-modal__btn--primary"
                        onClick={onClose}
                    >
                        {t('notifications.close', 'Kapat')}
                    </button>
                </div>
            </div>
        </div>
    );
}
