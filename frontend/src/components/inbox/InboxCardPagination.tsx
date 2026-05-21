import { useLanguage } from '../../i18n/LanguageContext';

type InboxCardPaginationProps = {
    page: number;
    totalPages: number;
    totalElements: number;
    pageSize: number;
    onPageChange: (page: number) => void;
    disabled?: boolean;
};

export function InboxCardPagination({
    page,
    totalPages,
    totalElements,
    pageSize,
    onPageChange,
    disabled,
}: InboxCardPaginationProps) {
    const { t } = useLanguage();

    if (totalElements === 0) return null;

    const safeTotalPages = Math.max(1, totalPages);
    const from = page * pageSize + 1;
    const to = Math.min((page + 1) * pageSize, totalElements);

    return (
        <div className="inbox-card-pagination">
            <span className="inbox-card-pagination__info">
                {from}–{to} / {totalElements}
            </span>
            <div className="inbox-card-pagination__nav">
                <button
                    type="button"
                    className="notif-page-btn"
                    disabled={disabled || page <= 0}
                    onClick={() => onPageChange(Math.max(0, page - 1))}
                >
                    {t('news.prev', 'Önceki')}
                </button>
                <span className="notif-page-info">
                    {page + 1} / {safeTotalPages}
                </span>
                <button
                    type="button"
                    className="notif-page-btn"
                    disabled={disabled || page >= safeTotalPages - 1}
                    onClick={() => onPageChange(Math.min(safeTotalPages - 1, page + 1))}
                >
                    {t('news.next', 'Sonraki')}
                </button>
            </div>
        </div>
    );
}
