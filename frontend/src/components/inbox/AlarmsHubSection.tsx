import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BellRing, Trash2 } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import { priceAlertKeys } from '../../queries/priceAlertKeys';
import {
    deletePriceAlert,
    listPriceAlertsPage,
    type PriceAlertListFilter,
    readFinanceApiError,
} from '../../services/priceAlertApi';
import type { PriceAlertStatus } from '../../types/priceAlert';
import {
    formatPriceAlertTitle,
    priceAlertConditionLabel,
    priceAlertStatusLabel,
} from '../../utils/priceAlertDisplay';
import { InboxCardPagination } from './InboxCardPagination';

export type AlarmFilterTab = 'ACTIVE' | 'PAST' | 'ALL';

const INBOX_PAGE_SIZE = 10;

type AlarmsHubSectionProps = {
    initialFilter?: AlarmFilterTab;
    embedded?: boolean;
    syncUrl?: boolean;
};

function statusChipClass(status: PriceAlertStatus): string {
    if (status === 'ACTIVE') return 'alarm-status-chip alarm-status-chip--active';
    if (status === 'TRIGGERED') return 'alarm-status-chip alarm-status-chip--triggered';
    return 'alarm-status-chip alarm-status-chip--disabled';
}

function filterToApi(filter: AlarmFilterTab): PriceAlertListFilter {
    if (filter === 'ACTIVE') return 'active';
    if (filter === 'PAST') return 'past';
    return 'all';
}

export function AlarmsHubSection({
    initialFilter = 'ALL',
    embedded = false,
    syncUrl = true,
}: AlarmsHubSectionProps) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const queryClient = useQueryClient();
    const [searchParams, setSearchParams] = useSearchParams();
    const [filter, setFilter] = useState<AlarmFilterTab>(initialFilter);
    const [page, setPage] = useState(0);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    useEffect(() => {
        setFilter(initialFilter);
        setPage(0);
    }, [initialFilter]);

    const changeFilter = (next: AlarmFilterTab) => {
        setFilter(next);
        setPage(0);
        if (!syncUrl) return;
        const params = new URLSearchParams(searchParams);
        params.delete('hub');
        params.set('alarmFilter', next === 'ACTIVE' ? 'active' : next === 'PAST' ? 'past' : 'all');
        params.delete('alarmPage');
        setSearchParams(params, { replace: true });
    };

    const apiFilter = filterToApi(filter);

    const { data, isLoading, isFetching, error } = useQuery({
        queryKey: [...priceAlertKeys.list(), 'page', page, INBOX_PAGE_SIZE, apiFilter],
        queryFn: () => listPriceAlertsPage(page, INBOX_PAGE_SIZE, apiFilter),
        staleTime: 20_000,
        placeholderData: (prev) => prev,
    });

    const items = data?.content ?? [];
    const totalPages = data?.totalPages ?? 0;
    const totalElements = data?.totalElements ?? 0;

    const removeMutation = useMutation({
        mutationFn: deletePriceAlert,
        onSuccess: () => {
            setDeleteError(null);
            void queryClient.invalidateQueries({ queryKey: priceAlertKeys.all });
        },
        onError: (err: unknown) => {
            setDeleteError(readFinanceApiError(err).message || t('alarms.deleteFailed', 'Alarm silinemedi.'));
        },
    });

    const listPane = (
        <div className="notif-inbox-card__list-pane">
            {error ? (
                <div className="notif-error">
                    {readFinanceApiError(error).message || t('alarms.loadFailed', 'Alarmlar yüklenemedi')}
                </div>
            ) : isLoading && items.length === 0 ? (
                <div className="notif-loading">{t('common.loading', 'Yükleniyor...')}</div>
            ) : items.length === 0 ? (
                <div className="notif-empty">{t('alarms.empty', 'Bu listede alarm yok.')}</div>
            ) : (
                <div className="notif-alarm-list notif-alarm-list--embedded">
                    {items.map((alert) => (
                        <div key={alert.id} className="alarm-list-item alarm-list-item--compact">
                            <span className="notif-item__type-icon" style={{ color: '#38bdf8' }} aria-hidden>
                                <BellRing size={16} />
                            </span>
                            <div className="alarm-list-item__body">
                                <div className="alarm-list-item__symbol">{formatPriceAlertTitle(alert)}</div>
                                <div className="alarm-list-item__meta">
                                    <span className={statusChipClass(alert.status)}>
                                        {priceAlertStatusLabel(alert.status, t)}
                                    </span>
                                    {priceAlertConditionLabel(alert.conditionType, t)}{' '}
                                    {alert.conditionType.startsWith('CHANGE_PCT')
                                        ? `${Number(alert.threshold).toLocaleString(locale, { maximumFractionDigits: 2 })}%`
                                        : Number(alert.threshold).toLocaleString(locale, {
                                              maximumFractionDigits: 4,
                                          })}
                                </div>
                            </div>
                            <div className="alarm-list-item__aside">
                                <span className="alarm-list-item__date">
                                    {new Date(alert.lastTriggeredAt ?? alert.createdAt).toLocaleString(locale, {
                                        dateStyle: 'short',
                                        timeStyle: 'short',
                                    })}
                                </span>
                                <button
                                    type="button"
                                    className="notif-page-btn"
                                    disabled={removeMutation.isPending}
                                    onClick={() => removeMutation.mutate(alert.id)}
                                    title={t('priceAlert.delete', 'Sil')}
                                >
                                    <Trash2 size={14} aria-hidden />
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );

    const body = (
        <>
            <div
                className={`notif-tabs notif-alarm-filter-tabs${embedded ? ' notif-alarm-filter-tabs--embedded' : ''}`}
                role="tablist"
                aria-label={t('alarms.filter', 'Alarm filtresi')}
            >
                <button
                    type="button"
                    role="tab"
                    aria-selected={filter === 'ALL'}
                    className={`notif-tab ${filter === 'ALL' ? 'is-active' : ''}`}
                    onClick={() => changeFilter('ALL')}
                >
                    {t('alarms.filterAll', 'Tümü')}
                </button>
                <button
                    type="button"
                    role="tab"
                    aria-selected={filter === 'ACTIVE'}
                    className={`notif-tab ${filter === 'ACTIVE' ? 'is-active' : ''}`}
                    onClick={() => changeFilter('ACTIVE')}
                >
                    {t('alarms.filterActive', 'Aktif')}
                </button>
                <button
                    type="button"
                    role="tab"
                    aria-selected={filter === 'PAST'}
                    className={`notif-tab ${filter === 'PAST' ? 'is-active' : ''}`}
                    onClick={() => changeFilter('PAST')}
                >
                    {t('alarms.filterPast', 'Geçmiş')}
                </button>
            </div>

            {deleteError ? <div className="notif-error">{deleteError}</div> : null}

            {listPane}

            <InboxCardPagination
                page={page}
                totalPages={totalPages}
                totalElements={totalElements}
                pageSize={INBOX_PAGE_SIZE}
                onPageChange={setPage}
                disabled={isFetching}
            />
        </>
    );

    if (embedded) {
        return (
            <section className="notif-inbox-card notif-inbox-card--alarms" aria-labelledby="notif-alarms-heading">
                <header className="notif-inbox-card__head">
                    <span className="notif-inbox-card__icon" aria-hidden>
                        <BellRing size={18} />
                    </span>
                    <h2 id="notif-alarms-heading" className="notif-inbox-card__title">
                        {t('alarms.title', 'Alarmlar')}
                    </h2>
                    <span className="notif-inbox-card__count">
                        {totalElements} {t('alarms.item', 'alarm')}
                    </span>
                </header>
                <div className="notif-inbox-card__body">{body}</div>
            </section>
        );
    }

    return body;
}
