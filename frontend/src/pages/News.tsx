import { useState, useEffect, useRef, useCallback } from 'react';
import { marketClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import DOMPurify from 'dompurify';
import { useSearchParams } from 'react-router-dom';
import './News.css';

type NewsMediaItem = {
    url: string;
    caption: string | null;
};

type NewsItem = {
    id: number;
    title: string;
    titleTr?: string | null;
    summary: string | null;
    contentTr?: string | null;
    content?: string | null;
    source: string | null;
    url: string | null;
    category: string;
    publishedAt: string;
    createdAt: string;
};

// Spring Data 3.3+ VIA_DTO shape (bkz. MarketDataApplication).
type NewsPage = {
    content: NewsItem[];
    page: { size: number; number: number; totalElements: number; totalPages: number };
};

const CATEGORY_DEFS = [
    { value: '', labelKey: 'news.category.all', labelFallback: 'Tümü' },
    { value: 'FOREX', labelKey: 'news.category.fx', labelFallback: 'Döviz' },
    { value: 'COMMODITY', labelKey: 'news.category.gold', labelFallback: 'Altın' },
    { value: 'FUND', labelKey: 'news.category.funds', labelFallback: 'Fonlar' },
    { value: 'CRYPTO', labelKey: 'news.category.crypto', labelFallback: 'Kripto' },
    { value: 'STOCK', labelKey: 'news.category.stock', labelFallback: 'Hisse' },
    { value: 'VIOP', labelKey: 'news.category.viop', labelFallback: 'VİOP' },
    { value: 'BOND', labelKey: 'news.category.bond', labelFallback: 'Tahvil' },
];

export function News() {
    const { tokens } = useTheme();
    const { lang, t } = useLanguage();
    const [searchParams] = useSearchParams();
    const focusId = Number(searchParams.get('focus') ?? 0);
    const queryCategory = searchParams.get('category') ?? '';
    const [page, setPage] = useState<NewsPage | null>(null);
    const [category, setCategory] = useState('');
    const [pageNum, setPageNum] = useState(0);
    const [selected, setSelected] = useState<NewsItem | null>(null);
    const [detailMedia, setDetailMedia] = useState<NewsMediaItem[]>([]);
    const [mediaLoading, setMediaLoading] = useState(false);
    const [detailLoading, setDetailLoading] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const detailColRef = useRef<HTMLDivElement>(null);
    const preferTurkish = lang === 'tr';
    const uiLocale = lang === 'en' ? 'en-US' : 'tr-TR';

    const pageCssVars = {
        '--news-bg': tokens.bg,
        '--news-text': tokens.text,
        '--news-muted': tokens.textMuted,
        '--news-card': tokens.bgCard,
        '--news-border': tokens.border,
        '--news-accent': tokens.accent,
        '--news-error': tokens.error,
    } as React.CSSProperties;

    useEffect(() => {
        if (!queryCategory) return;
        const allowed = new Set(CATEGORY_DEFS.map((x) => x.value));
        if (allowed.has(queryCategory)) {
            setCategory(queryCategory);
            setPageNum(0);
        }
    }, [queryCategory]);

    const contentNotFoundHtml = `<p>${t('news.contentNotFound', 'İçerik bulunamadı.')}</p>`;

    const extractReadableNewsBody = (rawHtml: string | null | undefined): string => {
        if (!rawHtml || !rawHtml.trim()) return contentNotFoundHtml;

        try {
            const parser = new DOMParser();
            const doc = parser.parseFromString(rawHtml, 'text/html');
            doc.querySelectorAll('script,style,iframe,object,embed,form,input,button,noscript,svg').forEach((el) => el.remove());

            doc.querySelectorAll('*').forEach((el) => {
                Array.from(el.attributes).forEach((attr) => {
                    const name = attr.name.toLowerCase();
                    if (name.startsWith('on')) el.removeAttribute(attr.name);
                });
            });

            doc.querySelectorAll('a').forEach((anchor) => {
                anchor.setAttribute('target', '_blank');
                anchor.setAttribute('rel', 'noreferrer noopener');
            });

            const candidate = doc.querySelector('article') || doc.querySelector('.content') || doc.querySelector('main') || doc.body;
            const cleaned = candidate?.innerHTML?.trim() || contentNotFoundHtml;
            return DOMPurify.sanitize(cleaned, {
                USE_PROFILES: { html: true },
                ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'u', 'ul', 'ol', 'li', 'a', 'blockquote', 'h2', 'h3', 'h4'],
                ALLOWED_ATTR: ['href', 'target', 'rel'],
            });
        } catch {
            return DOMPurify.sanitize(rawHtml);
        }
    };

    useEffect(() => {
        setLoading(true);
        const params: { page: number; size: number; detail: boolean; category?: string } = { page: pageNum, size: 10, detail: false };
        if (category) params.category = category;
        marketClient
            .get<NewsPage>('/api/news', { params })
            .then((res) => setPage(res.data))
            .catch((err) => setError(err.message ?? 'Hata'))
            .finally(() => setLoading(false));
    }, [category, pageNum]);

    const scrollDetailIntoView = useCallback(() => {
        if (typeof window === 'undefined') return;
        if (!window.matchMedia('(max-width: 1023px)').matches) return;
        detailColRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, []);

    const loadNewsDetail = (id: number, optimisticItem?: NewsItem) => {
        if (optimisticItem) {
            setSelected(optimisticItem);
        }
        setDetailLoading(true);
        setMediaLoading(true);
        setDetailMedia([]);

        const detailReq = marketClient.get<NewsItem>(`/api/news/${id}`);
        const mediaReq = marketClient.get<NewsMediaItem[]>(`/api/news/${id}/media`).catch(() => ({ data: [] as NewsMediaItem[] }));

        detailReq
            .then((res) => setSelected(res.data))
            .catch(() => {
                // Detay açılamazsa liste deneyimini bozma.
            })
            .finally(() => {
                setDetailLoading(false);
                requestAnimationFrame(() => scrollDetailIntoView());
            });

        mediaReq
            .then((res) => setDetailMedia(Array.isArray(res.data) ? res.data : []))
            .finally(() => setMediaLoading(false));
    };

    useEffect(() => {
        if (!focusId || !page) return;
        const found = page.content.find((n) => n.id === focusId);
        if (found) {
            loadNewsDetail(found.id, found);
            return;
        }
        loadNewsDetail(focusId);
        // eslint-disable-next-line react-hooks/exhaustive-deps -- focusId/page tetikleyici; loadNewsDetail stabil davranış
    }, [focusId, page]);

    const displayTitle = (item: NewsItem) =>
        preferTurkish && item.titleTr ? item.titleTr : item.title;

    const renderDetailMedia = () => {
        if (mediaLoading) {
            return <p className="news-page__muted news-detail-media__status">{t('news.mediaLoading', 'Görseller yükleniyor…')}</p>;
        }
        if (detailMedia.length === 0 || !selected) {
            return null;
        }
        return (
            <div className="news-detail-media" aria-label={t('news.mediaGallery', 'Haber görselleri')}>
                {detailMedia.map((item) => (
                    <figure key={item.url} className="news-detail-media__item">
                        <a href={item.url} target="_blank" rel="noreferrer noopener">
                            <img
                                src={item.url}
                                alt={item.caption ?? displayTitle(selected)}
                                loading="lazy"
                                decoding="async"
                                referrerPolicy="no-referrer"
                                onError={(e) => {
                                    (e.currentTarget as HTMLImageElement).style.display = 'none';
                                }}
                            />
                        </a>
                        {item.caption ? <figcaption className="news-detail-media__caption">{item.caption}</figcaption> : null}
                    </figure>
                ))}
            </div>
        );
    };

    const renderDetailBody = () => {
        if (detailLoading) {
            return (
                <div>
                    {selected ? (
                        <div className="news-detail-card news-detail-card--preview">
                            <h2 className="news-detail-card__title">{displayTitle(selected)}</h2>
                            {selected.summary ? (
                                <div
                                    className="content-container"
                                    dangerouslySetInnerHTML={{
                                        __html: extractReadableNewsBody(selected.summary),
                                    }}
                                />
                            ) : null}
                        </div>
                    ) : null}
                    <p className="news-page__muted">{t('news.loading', 'Yükleniyor...')}</p>
                </div>
            );
        }
        if (selected) {
            const bodySource =
                (preferTurkish ? selected.contentTr : null) ?? selected.content ?? selected.summary;
            const hasBody = Boolean(bodySource?.trim());
            const showEmptyContent = !hasBody && !mediaLoading && detailMedia.length === 0;

            return (
                <div className="news-detail-card">
                    <h2 className="news-detail-card__title">{displayTitle(selected)}</h2>
                    <div className="news-detail-meta">
                        <span>
                            <strong>{t('news.source', 'Kaynak')}:</strong> {selected.source ?? 'Unknown'}
                        </span>
                        <span>
                            <strong>{t('news.date', 'Tarih')}:</strong>{' '}
                            {new Date(selected.publishedAt).toLocaleString(uiLocale)}
                        </span>
                    </div>
                    {hasBody || showEmptyContent ? (
                        <div
                            className="content-container"
                            dangerouslySetInnerHTML={{
                                __html: extractReadableNewsBody(bodySource),
                            }}
                        />
                    ) : null}
                    {renderDetailMedia()}
                    {selected.url && (
                        <a href={selected.url} target="_blank" rel="noreferrer" className="news-source-button">
                            {t('news.goToSource', 'Kaynağa git')}
                        </a>
                    )}
                </div>
            );
        }
        return <p className="news-page__detail-placeholder">{t('news.selectPrompt', 'Detay için listeden bir haber seçin.')}</p>;
    };

    if (error) {
        return (
            <div className="news-page" style={pageCssVars}>
                <h1 className="news-page__title">{t('news.title', 'Haberler')}</h1>
                <p className="news-page__muted news-page__muted--error">
                    {t('news.errorPrefix', 'Hata')}: {error}
                </p>
            </div>
        );
    }

    return (
        <div className="news-page" style={pageCssVars}>
            <h1 className="news-page__title">{t('news.title', 'Haberler')}</h1>
            <div className="news-page__categories" role="tablist" aria-label={t('news.title', 'Haberler')}>
                {CATEGORY_DEFS.map((c) => (
                    <button
                        key={c.value || 'all'}
                        type="button"
                        role="tab"
                        aria-selected={category === c.value}
                        className={`news-page__category-btn${category === c.value ? ' news-page__category-btn--active' : ''}`}
                        onClick={() => {
                            setCategory(c.value);
                            setPageNum(0);
                        }}
                    >
                        {t(c.labelKey, c.labelFallback)}
                    </button>
                ))}
            </div>
            <div className="news-page__layout">
                <div className="news-page__list-col">
                    {loading && <p className="news-page__muted">{t('news.loading', 'Yükleniyor...')}</p>}
                    {page?.content?.length === 0 && <p className="news-page__muted">{t('news.empty', 'Haber yok.')}</p>}
                    <ul className="news-page__list">
                        {page?.content?.map((item) => (
                            <li key={item.id}>
                                <button
                                    type="button"
                                    className={`news-page__card${selected?.id === item.id ? ' news-page__card--active' : ''}`}
                                    onClick={() => loadNewsDetail(item.id, item)}
                                >
                                    <span className="news-page__card-title">{displayTitle(item)}</span>
                                    <div className="news-page__card-meta">
                                        <span>{item.source}</span>
                                        <span aria-hidden>·</span>
                                        <span>{new Date(item.publishedAt).toLocaleString(uiLocale)}</span>
                                    </div>
                                </button>
                            </li>
                        ))}
                    </ul>
                    {page && page.page && page.page.totalElements > page.page.size && (
                        <div className="news-page__pagination">
                            <button
                                type="button"
                                className="news-page__page-btn"
                                disabled={pageNum === 0}
                                onClick={() => setPageNum((p) => p - 1)}
                            >
                                {t('news.prev', 'Önceki')}
                            </button>
                            <span className="news-page__page-info">
                                {t('news.page', 'Sayfa')} {page.page.number + 1} / {page.page.totalPages}
                            </span>
                            <button
                                type="button"
                                className="news-page__page-btn"
                                disabled={(page.page.number + 1) * page.page.size >= page.page.totalElements}
                                onClick={() => setPageNum((p) => p + 1)}
                            >
                                {t('news.next', 'Sonraki')}
                            </button>
                        </div>
                    )}
                </div>
                <div className="news-page__detail-col" ref={detailColRef}>
                    {renderDetailBody()}
                </div>
            </div>
        </div>
    );
}
