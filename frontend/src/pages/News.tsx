import { useState, useEffect } from 'react';
import { marketClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import DOMPurify from 'dompurify';
import './News.css';

type NewsItem = {
    id: number;
    title: string;
    summary: string | null;
    content?: string | null;
    source: string | null;
    url: string | null;
    category: string;
    publishedAt: string;
    createdAt: string;
};

type NewsPage = { content: NewsItem[]; totalElements: number; number: number; size: number };

const CATEGORIES = [
    { value: '', label: 'Tümü' },
    { value: 'GENERAL', label: 'Genel' },
    { value: 'FOREX', label: 'Döviz' },
    { value: 'CRYPTO', label: 'Kripto' },
    { value: 'STOCK', label: 'Hisse' },
];

export function News() {
    const { tokens } = useTheme();
    const [page, setPage] = useState<NewsPage | null>(null);
    const [category, setCategory] = useState('');
    const [pageNum, setPageNum] = useState(0);
    const [selected, setSelected] = useState<NewsItem | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

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
        const params: { page: number; size: number; category?: string } = { page: pageNum, size: 10 };
        if (category) params.category = category;
        marketClient
            .get<NewsPage>('/api/news', { params })
            .then((res) => setPage(res.data))
            .catch((err) => setError(err.message ?? 'Hata'))
            .finally(() => setLoading(false));
    }, [category, pageNum]);

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };

    if (error) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Haberler</h1>
                <p style={{ ...mutedStyle, color: tokens.error }}>Hata: {error}</p>
            </div>
        );
    }

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Haberler</h1>
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
                    {loading && <p style={mutedStyle}>Yükleniyor...</p>}
                    {page?.content?.length === 0 && <p style={mutedStyle}>Haber yok.</p>}
                    <ul style={{ listStyle: 'none', padding: 0 }}>
                        {page?.content?.map((item) => (
                            <li
                                key={item.id}
                                onClick={() => setSelected(item)}
                                style={{
                                    padding: 12,
                                    marginBottom: 8,
                                    border: `1px solid ${selected?.id === item.id ? tokens.accent : tokens.border}`,
                                    borderRadius: 8,
                                    cursor: 'pointer',
                                    background: tokens.bgCard,
                                }}
                            >
                                <strong style={{ fontSize: '0.9375rem' }}>{item.title}</strong>
                                <div style={{ fontSize: '0.75rem', color: tokens.textMuted, marginTop: 4 }}>
                                    {item.source} · {new Date(item.publishedAt).toLocaleString('tr-TR')}
                                </div>
                            </li>
                        ))}
                    </ul>
                    {page && page.totalElements > page.size && (
                        <div style={{ marginTop: 16, display: 'flex', gap: 8, alignItems: 'center', fontSize: '0.875rem' }}>
                            <button
                                type="button"
                                disabled={pageNum === 0}
                                onClick={() => setPageNum((p) => p - 1)}
                                style={{ padding: '6px 12px', background: tokens.bgCard, color: tokens.text, border: `1px solid ${tokens.border}`, borderRadius: 8, cursor: pageNum === 0 ? 'default' : 'pointer' }}
                            >
                                Önceki
                            </button>
                            <span style={{ color: tokens.textMuted }}>Sayfa {page.number + 1} / {Math.ceil(page.totalElements / page.size)}</span>
                            <button
                                type="button"
                                disabled={(page.number + 1) * page.size >= page.totalElements}
                                onClick={() => setPageNum((p) => p + 1)}
                                style={{ padding: '6px 12px', background: tokens.bgCard, color: tokens.text, border: `1px solid ${tokens.border}`, borderRadius: 8, cursor: (page.number + 1) * page.size >= page.totalElements ? 'default' : 'pointer' }}
                            >
                                Sonraki
                            </button>
                        </div>
                    )}
                </div>
                <div>
                    {selected ? (
                        <div style={{ padding: 16, border: `1px solid ${tokens.border}`, borderRadius: 8, background: tokens.bgCard }} className="news-detail-card">
                            <h2 style={{ fontSize: '1.125rem', fontWeight: 600, marginBottom: 8 }}>{selected.title}</h2>
                            <div className="news-detail-meta">
                                <span><strong>Kaynak:</strong> {selected.source ?? 'Bilinmiyor'}</span>
                                <span><strong>Tarih:</strong> {new Date(selected.publishedAt).toLocaleString('tr-TR')}</span>
                            </div>
                            <div
                                className="content-container"
                                dangerouslySetInnerHTML={{
                                    __html: extractReadableNewsBody(selected.content ?? selected.summary),
                                }}
                            />
                            {selected.url && (
                                <a href={selected.url} target="_blank" rel="noreferrer" className="news-source-button">
                                    Kaynağa git
                                </a>
                            )}
                        </div>
                    ) : (
                        <p style={{ color: tokens.textMuted }}>Detay için listeden bir haber seçin.</p>
                    )}
                </div>
            </div>
        </div>
    );
}