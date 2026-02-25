import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';

type ReviewTaskView = {
    id: number;
    type: string;
    referenceId: number;
    assigneeRole: string;
    status: string;
    priority: string;
    dueAt: string | null;
    createdAt: string;
    outcome: string | null;
    accountId: number | null;
};

type PageResponse<T> = {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
};

const TABS = [
    { key: 'all', label: 'Tümü', status: undefined, type: undefined, filterHighPriority: false },
    { key: 'pending', label: 'Bekleyen', status: 'PENDING', type: undefined, filterHighPriority: false },
    { key: 'high', label: 'Yüksek öncelik', status: undefined, type: undefined, filterHighPriority: true },
    { key: 'escalated', label: 'Escalated', status: 'ESCALATED', type: undefined, filterHighPriority: false },
] as const;

export function FmTasks() {
    const { tokens } = useTheme();
    const navigate = useNavigate();
    const [tasks, setTasks] = useState<ReviewTaskView[]>([]);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [page, setPage] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [activeTab, setActiveTab] = useState<typeof TABS[number]['key']>('all');

    const tabConfig = TABS.find((t) => t.key === activeTab) ?? TABS[0];

    useEffect(() => {
        setLoading(true);
        const params: Record<string, string | number> = { page, size: 20 };
        if (tabConfig.status) params.status = tabConfig.status;
        if (tabConfig.type) params.type = tabConfig.type;

        financeClient
            .get('/api/tasks/me', { params })
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                const pageData = raw as PageResponse<ReviewTaskView>;
                let list = pageData?.content ?? [];
                if (tabConfig.filterHighPriority) {
                    list = list.filter((t) => t.priority === 'HIGH');
                }
                setTasks(list);
                setTotalPages(pageData?.totalPages ?? 0);
                setTotalElements(tabConfig.filterHighPriority ? list.length : (pageData?.totalElements ?? 0));
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'Liste alınamadı';
                setError(msg);
            })
            .finally(() => setLoading(false));
    }, [page, activeTab, tabConfig.status, tabConfig.type, tabConfig.filterHighPriority]);

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };
    const tableStyle: React.CSSProperties = { width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' };
    const thStyle: React.CSSProperties = { textAlign: 'left', padding: '10px 12px', borderBottom: `2px solid ${tokens.border}`, background: tokens.bgCard };
    const tdStyle: React.CSSProperties = { padding: '10px 12px', borderBottom: `1px solid ${tokens.border}` };
    const rowStyle: React.CSSProperties = { cursor: 'pointer' };
    const tabStyle = (selected: boolean): React.CSSProperties => ({
        padding: '8px 16px',
        marginRight: 8,
        border: `1px solid ${tokens.border}`,
        borderRadius: 8,
        background: selected ? tokens.accent : tokens.bgCard,
        color: selected ? '#fff' : tokens.text,
        cursor: 'pointer',
    });

    const formatDate = (s: string | null) => (s ? new Date(s).toLocaleString('tr-TR') : '–');

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Görevler</h1>
            <p style={mutedStyle}>İnceleme görevleri. Satıra tıklayarak detaya gidin.</p>

            <div style={{ marginTop: 16, marginBottom: 16, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {TABS.map((t) => (
                    <button
                        key={t.key}
                        style={tabStyle(activeTab === t.key)}
                        onClick={() => { setActiveTab(t.key); setPage(0); }}
                    >
                        {t.label}
                    </button>
                ))}
            </div>

            {loading && <p style={mutedStyle}>Yükleniyor...</p>}
            {error && <p style={{ color: tokens.error, marginBottom: 16 }}>Hata: {error}</p>}

            {!loading && !error && (
                <>
                    <div style={{ overflowX: 'auto', border: `1px solid ${tokens.border}`, borderRadius: 12, background: tokens.bgCard }}>
                        <table style={tableStyle}>
                            <thead>
                            <tr>
                                <th style={thStyle}>Görev / Referans</th>
                                <th style={thStyle}>Tip</th>
                                <th style={thStyle}>Öncelik</th>
                                <th style={thStyle}>Son tarih</th>
                                <th style={thStyle}>Durum</th>
                            </tr>
                            </thead>
                            <tbody>
                            {tasks.length === 0 ? (
                                <tr><td colSpan={5} style={{ ...tdStyle, color: tokens.textMuted, textAlign: 'center' }}>Görev yok.</td></tr>
                            ) : (
                                tasks.map((row) => (
                                    <tr
                                        key={row.id}
                                        style={rowStyle}
                                        onClick={() => navigate(`/fm/tasks/${row.id}`)}
                                    >
                                        <td style={tdStyle}>{row.type} #{row.referenceId}</td>
                                        <td style={tdStyle}>{row.type}</td>
                                        <td style={tdStyle}>{row.priority}</td>
                                        <td style={tdStyle}>{formatDate(row.dueAt)}</td>
                                        <td style={tdStyle}>{row.status}</td>
                                    </tr>
                                ))
                            )}
                            </tbody>
                        </table>
                    </div>
                    {totalPages > 1 && (
                        <div style={{ marginTop: 16, display: 'flex', gap: 8, alignItems: 'center' }}>
                            <button
                                style={{ padding: '6px 12px', border: `1px solid ${tokens.border}`, borderRadius: 8, background: tokens.bgCard, color: tokens.text, cursor: page === 0 ? 'not-allowed' : 'pointer', opacity: page === 0 ? 0.6 : 1 }}
                                onClick={() => setPage((p) => Math.max(0, p - 1))}
                                disabled={page === 0}
                            >
                                Önceki
                            </button>
                            <span style={{ color: tokens.textMuted }}>Sayfa {page + 1} / {totalPages} (toplam {totalElements})</span>
                            <button
                                style={{ padding: '6px 12px', border: `1px solid ${tokens.border}`, borderRadius: 8, background: tokens.bgCard, color: tokens.text, cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer', opacity: page >= totalPages - 1 ? 0.6 : 1 }}
                                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                                disabled={page >= totalPages - 1}
                            >
                                Sonraki
                            </button>
                        </div>
                    )}
                </>
            )}
        </div>
    );
}