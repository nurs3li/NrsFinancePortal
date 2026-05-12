import { useState, useEffect } from 'react';
import { marketClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import DOMPurify from 'dompurify';
import { useSearchParams } from 'react-router-dom';
import './News.css';

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

const CATEGORIES = [
    { value: '', label: 'Tümü' },
    { value: 'GENERAL', label: 'Genel' },
    { value: 'FOREX', label: 'Döviz' },
    { value: 'COMMODITY', label: 'Altın' },
    { value: 'FUND', label: 'Fonlar' },
    { value: 'CRYPTO', label: 'Kripto' },
    { value: 'STOCK', label: 'Hisse' },
    { value: 'VIOP', label: 'VİOP' },
    { value: 'BOND', label: 'Tahvil' },
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
    const [detailLoading, setDetailLoading] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const preferTurkish = lang === 'tr';
    const uiLocale = lang === 'en' ? 'en-US' : 'tr-TR';

    useEffect(() => {
        if (!queryCategory) return;
        const allowed = new Set(CATEGORIES.map((x) => x.value));
        if (allowed.has(queryCategory)) {
            setCategory(queryCategory);
            setPageNum(0);
        }
    }, [queryCategory]);

    const extractReadableNewsBody = (rawHtml: string | null | undefined): string => {
        if (!rawHtml || !rawHtml.trim()) return '<p>İçerik bulunamadı.</p>';

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
            const cleaned = candidate?.innerHTML?.trim() || '<p>İçerik bulunamadı.</p>';
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

    const loadNewsDetail = (id: number, optimisticItem?: NewsItem) => {
        if (optimisticItem) {
            setSelected(optimisticItem);
        }
        setDetailLoading(true);
        marketClient
            .get<NewsItem>(`/api/news/${id}`)
            .then((res) => setSelected(res.data))
            .catch(() => {
                // Detay açılamazsa liste deneyimini bozma.
            })
            .finally(() => setDetailLoading(false));
    };

    useEffect(() => {
        if (!focusId || !page) return;
        const found = page.content.find((n) => n.id === focusId);
        if (found) {
            loadNewsDetail(found.id, found);
            return;
        }
        loadNewsDetail(focusId);
    }, [focusId, page]);

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };

    if (error) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Haberler</h1>
                <p style={{ ...mutedStyle, color: tokens.error }}>{t('news.errorPrefix', 'Hata')}: {error}</p>
            </div>
        );
    }

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>{t('news.title', 'Haberler')}</h1>
            <div style={{ marginBottom: 16, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {CATEGORIES.map((c) => (
                    <button
                        key={c.value || 'all'}
                        type="button"
                        onClick={() => { setCategory(c.value); setPageNum(0); }}
                        style={{
                            padding: '8px 16px',
                            fontSize: '0.875rem',
                            fontWeight: category === c.value ? 600 : 500,
                            background: category === c.value ? tokens.accent : tokens.bgCard,
                            color: category === c.value ? '#fff' : tokens.text,
                            border: `1px solid ${tokens.border}`,
                            borderRadius: 8,
                            cursor: 'pointer',
                        }}
                    >
                        {c.label}
                    </button>
                ))}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 24 }}>
                <div>
                    {loading && <p style={mutedStyle}>{t('news.loading', 'Yükleniyor...')}</p>}
                    {page?.content?.length === 0 && <p style={mutedStyle}>{t('news.empty', 'Haber yok.')}</p>}
                    <ul style={{ listStyle: 'none', padding: 0 }}>
                        {page?.content?.map((item) => (
                            <li
                                key={item.id}
                                onClick={() => loadNewsDetail(item.id, item)}
                                style={{
                                    padding: 12,
                                    marginBottom: 8,
                                    border: `1px solid ${selected?.id === item.id ? tokens.accent : tokens.border}`,
                                    borderRadius: 8,
                                    cursor: 'pointer',
                                    background: tokens.bgCard,
                                }}
                            >
                                <strong style={{ fontSize: '0.9375rem' }}>
                                    {preferTurkish && item.titleTr ? item.titleTr : item.title}
                                </strong>
                                <div style={{ fontSize: '0.75rem', color: tokens.textMuted, marginTop: 4 }}>
                                    {item.source} · {new Date(item.publishedAt).toLocaleString(uiLocale)}
                                </div>
                            </li>
                        ))}
                    </ul>
                    {page && page.page && page.page.totalElements > page.page.size && (
                        <div style={{ marginTop: 16, display: 'flex', gap: 8, alignItems: 'center', fontSize: '0.875rem' }}>
                            <button
                                type="button"
                                disabled={pageNum === 0}
                                onClick={() => setPageNum((p) => p - 1)}
                                style={{ padding: '6px 12px', background: tokens.bgCard, color: tokens.text, border: `1px solid ${tokens.border}`, borderRadius: 8, cursor: pageNum === 0 ? 'default' : 'pointer' }}
                            >
                                {t('news.prev', 'Önceki')}
                            </button>
                            <span style={{ color: tokens.textMuted }}>
                                {t('news.page', 'Sayfa')} {page.page.number + 1} / {page.page.totalPages}
                            </span>
                            <button
                                type="button"
                                disabled={(page.page.number + 1) * page.page.size >= page.page.totalElements}
                                onClick={() => setPageNum((p) => p + 1)}
                                style={{ padding: '6px 12px', background: tokens.bgCard, color: tokens.text, border: `1px solid ${tokens.border}`, borderRadius: 8, cursor: (page.page.number + 1) * page.page.size >= page.page.totalElements ? 'default' : 'pointer' }}
                            >
                                {t('news.next', 'Sonraki')}
                            </button>
                        </div>
                    )}
                </div>
                <div>
                    {detailLoading ? (
                        <div>
                            {selected ? (
                                <div
                                    style={{
                                        padding: 16,
                                        marginBottom: 12,
                                        border: `1px solid ${tokens.border}`,
                                        borderRadius: 8,
                                        background: tokens.bgCard,
                                        opacity: 0.92,
                                    }}
                                >
                                    <h2 style={{ fontSize: '1.125rem', fontWeight: 600, marginBottom: 8 }}>
                                        {preferTurkish && selected.titleTr ? selected.titleTr : selected.title}
                                    </h2>
                                    {selected.summary ? (
                                        <div
                                            className="content-container"
                                            style={{ fontSize: '0.875rem', color: tokens.textMuted }}
                                            dangerouslySetInnerHTML={{
                                                __html: extractReadableNewsBody(selected.summary),
                                            }}
                                        />
                                    ) : null}
                                </div>
                            ) : null}
                            <p style={{ color: tokens.textMuted }}>{t('news.loading', 'Yükleniyor...')}</p>
                        </div>
                    ) : selected ? (
                        <div style={{ padding: 16, border: `1px solid ${tokens.border}`, borderRadius: 8, background: tokens.bgCard }} className="news-detail-card">
                            <h2 style={{ fontSize: '1.125rem', fontWeight: 600, marginBottom: 8 }}>
                                {preferTurkish && selected.titleTr ? selected.titleTr : selected.title}
                            </h2>
                            <div className="news-detail-meta">
                                <span><strong>{t('news.source', 'Kaynak')}:</strong> {selected.source ?? 'Unknown'}</span>
                                <span><strong>{t('news.date', 'Tarih')}:</strong> {new Date(selected.publishedAt).toLocaleString(uiLocale)}</span>
                            </div>
                            <div
                                className="content-container"
                                dangerouslySetInnerHTML={{
                                    __html: extractReadableNewsBody(
                                        (preferTurkish ? selected.contentTr : null) ?? selected.content ?? selected.summary
                                    ),
                                }}
                            />
                            {selected.url && (
                                <a href={selected.url} target="_blank" rel="noreferrer" className="news-source-button">
                                    {t('news.goToSource', 'Kaynağa git')}
                                </a>
                            )}
                        </div>
                    ) : (
                        <p style={{ color: tokens.textMuted }}>{t('news.selectPrompt', 'Detay için listeden bir haber seçin.')}</p>
                    )}
                </div>
            </div>
        </div>
    );
}