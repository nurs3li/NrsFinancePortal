import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';
import keycloak from '../auth/keycloak';
import { useLanguage } from '../i18n/LanguageContext';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8085';

export type ReviewTaskView = {
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
    subjectUserId: number | null;
    subjectUsername: string | null;
    assignedFmKeycloakId: string | null;
    claimedAt: string | null;
    claimState: string;
    readOnlyHint: string | null;
};

// Spring Data 3.3+ VIA_DTO shape (bkz. NrsFinancePortalApplication).
type PageResponse<T> = {
    content: T[];
    page: {
        size: number;
        number: number;
        totalElements: number;
        totalPages: number;
    };
};

type FmTaskSummary = {
    poolOpenCount: number;
    myClaimedOpenCount: number;
    myCompletedCount: number;
};

const COMPLETED_STATUSES = ['APPROVED', 'REJECTED', 'FREEZE_REQUESTED'];

const TABS = [
    { key: 'pool', labelKey: 'fm.tabs.pool', fallback: 'Açık Görevler (Havuz)', mode: 'pool' as const },
    { key: 'all', labelKey: 'fm.tabs.all', fallback: 'Tümü', mode: 'me' as const, status: undefined, type: undefined, filterHighPriority: false },
    { key: 'pending', labelKey: 'fm.tabs.pending', fallback: 'Bekleyen', mode: 'me' as const, status: 'PENDING', type: undefined, filterHighPriority: false },
    { key: 'high', labelKey: 'fm.tabs.high', fallback: 'Yüksek öncelik', mode: 'me' as const, status: undefined, type: undefined, filterHighPriority: true },
    { key: 'escalated', labelKey: 'fm.tabs.escalated', fallback: 'Escalated', mode: 'me' as const, status: 'ESCALATED', type: undefined, filterHighPriority: false },
] as const;

export function FmTasks() {
    const { tokens } = useTheme();
    const { t } = useLanguage();
    const navigate = useNavigate();
    const [tasks, setTasks] = useState<ReviewTaskView[]>([]);
    const [summary, setSummary] = useState<FmTaskSummary | null>(null);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [page, setPage] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [activeTab, setActiveTab] = useState<(typeof TABS)[number]['key']>('pool');
    const [hideCompleted, setHideCompleted] = useState(true);
    const [claimingId, setClaimingId] = useState<number | null>(null);
    const sseAbortRef = useRef<AbortController | null>(null);

    const tabConfig = TABS.find((t) => t.key === activeTab) ?? TABS[0];

    const fetchSummary = useCallback(() => {
        financeClient
            .get('/api/tasks/me/summary')
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                setSummary(raw as FmTaskSummary);
            })
            .catch(() => setSummary(null));
    }, []);

    const fetchTasks = useCallback(() => {
        setLoading(true);
        const params: Record<string, string | number> = { page, size: 20 };
        const isPool = tabConfig.mode === 'pool';
        if (!isPool) {
            if (tabConfig.status) params.status = tabConfig.status;
            if (tabConfig.type) params.type = tabConfig.type;
        }
        const req = isPool
            ? financeClient.get('/api/tasks/pool', { params })
            : financeClient.get('/api/tasks/me', { params });
        req.then((res) => {
                const raw = res.data?.data ?? res.data;
                const pageData = raw as PageResponse<ReviewTaskView>;
                let list = pageData?.content ?? [];
                if (tabConfig.mode === 'me' && 'filterHighPriority' in tabConfig && tabConfig.filterHighPriority) {
                    list = list.filter((t) => t.priority === 'HIGH');
                }
                if (hideCompleted) {
                    list = list.filter((t) => !COMPLETED_STATUSES.includes(t.status));
                }
                setTasks(list);
                setTotalPages(pageData?.page?.totalPages ?? 0);
                setTotalElements(
                    (tabConfig.mode === 'me' && 'filterHighPriority' in tabConfig && tabConfig.filterHighPriority) || hideCompleted
                        ? list.length
                        : (pageData?.page?.totalElements ?? 0),
                );
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? t('admin.listLoadFailed', 'Liste alınamadı');
                setError(msg);
            })
            .finally(() => setLoading(false));
    }, [page, activeTab, tabConfig, hideCompleted, t]);

    useEffect(() => {
        fetchTasks();
        fetchSummary();
    }, [fetchTasks, fetchSummary]);

    useRefetchOnFocus(() => {
        fetchTasks();
        fetchSummary();
    });
    usePolling(
        () => {
            void fetchTasks();
            void fetchSummary();
        },
        activeTab === 'pool' ? 12_000 : 60_000,
    );

    useEffect(() => {
        sseAbortRef.current?.abort();
        if (activeTab !== 'pool' || !keycloak.token) return;
        const ac = new AbortController();
        sseAbortRef.current = ac;
        const run = async () => {
            try {
                const res = await fetch(`${API_BASE}/api/tasks/sse/fm`, {
                    method: 'GET',
                    headers: {
                        Authorization: `Bearer ${keycloak.token}`,
                        Accept: 'text/event-stream',
                    },
                    signal: ac.signal,
                });
                if (!res.ok || !res.body) return;
                const reader = res.body.getReader();
                const dec = new TextDecoder();
                let buf = '';
                for (;;) {
                    const { done, value } = await reader.read();
                    if (done) break;
                    buf += dec.decode(value, { stream: true });
                    if (buf.includes('TASK_CLAIMED') || buf.includes('TASK_REMOVED_FROM_POOL') || buf.includes('TASK_FORCE_ASSIGNED')) {
                        buf = '';
                        void fetchTasks();
                        void fetchSummary();
                    }
                    if (buf.length > 64_000) buf = buf.slice(-32_000);
                }
            } catch {
                /* abort / network */
            }
        };
        void run();
        return () => ac.abort();
    }, [activeTab, fetchTasks, fetchSummary]);

    const claimTask = (e: React.MouseEvent, id: number) => {
        e.stopPropagation();
        setClaimingId(id);
        financeClient
            .post(`/api/tasks/${id}/claim`)
            .then(() => {
                void fetchTasks();
                void fetchSummary();
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? t('fm.claimFailed', 'Üstlenilemedi');
                setError(msg);
            })
            .finally(() => setClaimingId(null));
    };

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
            <style>{`
              @keyframes fmPoolPulse {
                0%, 100% { opacity: 1; transform: scale(1); }
                50% { opacity: 0.45; transform: scale(0.92); }
              }
            `}</style>
            <h1 style={titleStyle}>{t('nav.tasks', 'Görevler')}</h1>
            <p style={mutedStyle}>{t('fm.tasksSubtitle', 'Görev havuzu ve inceleme görevleri. Satıra tıklayarak detaya gidin.')}</p>

            {summary && (
                <div style={{ display: 'flex', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
                    <div style={{ padding: '12px 16px', borderRadius: 12, border: `1px solid ${tokens.border}`, background: tokens.bgCard, minWidth: 140 }}>
                        <div style={{ ...mutedStyle, fontSize: '0.75rem' }}>Havuzda açık</div>
                        <div style={{ fontSize: '1.35rem', fontWeight: 800 }}>{summary.poolOpenCount}</div>
                    </div>
                    <div style={{ padding: '12px 16px', borderRadius: 12, border: `1px solid ${tokens.border}`, background: tokens.bgCard, minWidth: 140 }}>
                        <div style={{ ...mutedStyle, fontSize: '0.75rem' }}>Üzerimdeki işler</div>
                        <div style={{ fontSize: '1.35rem', fontWeight: 800, color: tokens.accent }}>{summary.myClaimedOpenCount}</div>
                    </div>
                    <div style={{ padding: '12px 16px', borderRadius: 12, border: `1px solid ${tokens.border}`, background: tokens.bgCard, minWidth: 140 }}>
                        <div style={{ ...mutedStyle, fontSize: '0.75rem' }}>Tamamladıklarım</div>
                        <div style={{ fontSize: '1.35rem', fontWeight: 800 }}>{summary.myCompletedCount}</div>
                    </div>
                </div>
            )}

            <div style={{ marginTop: 16, marginBottom: 16, display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
                {TABS.map((tab) => (
                    <button
                        key={tab.key}
                        style={tabStyle(activeTab === tab.key)}
                        onClick={() => {
                            setActiveTab(tab.key);
                            setPage(0);
                        }}
                    >
                        {t(tab.labelKey, tab.fallback)}
                    </button>
                ))}
                <label style={{ marginLeft: 16, display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
                    <input
                        type="checkbox"
                        checked={hideCompleted}
                        onChange={(e) => {
                            setHideCompleted(e.target.checked);
                            setPage(0);
                        }}
                    />
                    <span style={mutedStyle}>{t('fm.hideCompleted', 'Tamamlananları gizle (Onaylanan / Reddedilen / Freeze talebi)')}</span>
                </label>
            </div>

            {loading && <p style={mutedStyle}>{t('common.loading', 'Yükleniyor...')}</p>}
            {error && <p style={{ color: tokens.error, marginBottom: 16 }}>{t('news.errorPrefix', 'Hata')}: {error}</p>}

            {!loading && !error && (
                <>
                    <div style={{ overflowX: 'auto', border: `1px solid ${tokens.border}`, borderRadius: 12, background: tokens.bgCard }}>
                        <table style={tableStyle}>
                            <thead>
                                <tr>
                                    <th style={thStyle} />
                                    <th style={thStyle}>{t('fm.taskReference', 'Görev / Referans')}</th>
                                    <th style={thStyle}>{t('admin.user', 'Kullanıcı')}</th>
                                    <th style={thStyle}>{t('fm.userId', 'Kullanıcı ID')}</th>
                                    <th style={thStyle}>{t('fm.type', 'Tip')}</th>
                                    <th style={thStyle}>{t('fm.priority', 'Öncelik')}</th>
                                    <th style={thStyle}>{t('fm.dueDate', 'Son tarih')}</th>
                                    <th style={thStyle}>{t('wallet.status', 'Durum')}</th>
                                    <th style={thStyle}>{t('fm.action', 'İşlem')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {tasks.length === 0 ? (
                                    <tr>
                                        <td colSpan={9} style={{ ...tdStyle, color: tokens.textMuted, textAlign: 'center' }}>
                                            {t('admin.noTasks', 'Görev yok.')}
                                        </td>
                                    </tr>
                                ) : (
                                    tasks.map((row) => {
                                        const isPoolRow = tabConfig.mode === 'pool';
                                        const claimedOther = row.claimState === 'OTHER';
                                        const claimedMine = row.claimState === 'MINE';
                                        const rowBg = claimedOther ? tokens.bg : undefined;
                                        return (
                                            <tr
                                                key={row.id}
                                                style={{ ...rowStyle, background: rowBg }}
                                                onClick={() => navigate(`/fm/tasks/${row.id}`)}
                                            >
                                                <td style={{ ...tdStyle, width: 36 }}>
                                                    {row.status === 'PENDING' && row.claimState === 'POOL' && (
                                                        <span
                                                            title={t('fm.pending', 'Bekliyor')}
                                                            style={{
                                                                display: 'inline-block',
                                                                width: 10,
                                                                height: 10,
                                                                borderRadius: 999,
                                                                background: tokens.error,
                                                                animation: 'fmPoolPulse 1.2s ease-in-out infinite',
                                                            }}
                                                        />
                                                    )}
                                                    {row.status === 'CLAIMED' && (
                                                        <span title={t('fm.claimed', 'Üstlenildi')} style={{ fontSize: '1rem' }}>
                                                            ⏳
                                                        </span>
                                                    )}
                                                </td>
                                                <td style={tdStyle}>
                                                    {row.type} #{row.referenceId}
                                                </td>
                                                <td style={{ ...tdStyle, fontWeight: 600 }}>{row.subjectUsername ?? '–'}</td>
                                                <td style={tdStyle}>{row.subjectUserId ?? '–'}</td>
                                                <td style={tdStyle}>{row.type}</td>
                                                <td style={tdStyle}>{row.priority}</td>
                                                <td style={tdStyle}>{formatDate(row.dueAt)}</td>
                                                <td style={tdStyle}>{row.status}</td>
                                                <td style={tdStyle} onClick={(e) => e.stopPropagation()}>
                                                    {isPoolRow && row.status === 'PENDING' && row.claimState === 'POOL' && (
                                                        <button
                                                            type="button"
                                                            style={{
                                                                padding: '8px 16px',
                                                                borderRadius: 10,
                                                                border: 'none',
                                                                fontWeight: 700,
                                                                cursor: claimingId === row.id ? 'wait' : 'pointer',
                                                                background: tokens.accentGradient,
                                                                color: '#fff',
                                                                boxShadow: '0 4px 14px rgba(0,0,0,0.18)',
                                                            }}
                                                            disabled={claimingId === row.id}
                                                            onClick={(e) => claimTask(e, row.id)}
                                                        >
                                                            {claimingId === row.id ? '…' : t('fm.claimMine', 'Üzerime Al')}
                                                        </button>
                                                    )}
                                                    {!isPoolRow && row.status === 'PENDING' && row.claimState === 'POOL' && (
                                                        <button
                                                            type="button"
                                                            style={{
                                                                padding: '8px 16px',
                                                                borderRadius: 10,
                                                                border: 'none',
                                                                fontWeight: 700,
                                                                cursor: claimingId === row.id ? 'wait' : 'pointer',
                                                                background: tokens.accentGradient,
                                                                color: '#fff',
                                                            }}
                                                            disabled={claimingId === row.id}
                                                            onClick={(e) => claimTask(e, row.id)}
                                                        >
                                                            {claimingId === row.id ? '…' : t('fm.claimMine', 'Üzerime Al')}
                                                        </button>
                                                    )}
                                                    {!isPoolRow && claimedMine && (
                                                        <button
                                                            type="button"
                                                            style={{
                                                                padding: '8px 14px',
                                                                borderRadius: 10,
                                                                border: `1px solid ${tokens.accent}`,
                                                                background: tokens.accent,
                                                                color: '#fff',
                                                                fontWeight: 600,
                                                                cursor: 'pointer',
                                                            }}
                                                            onClick={() => navigate(`/fm/tasks/${row.id}`)}
                                                        >
                                                            {t('fm.startOrDetail', 'İşleme Başla / Detay')}
                                                        </button>
                                                    )}
                                                    {!isPoolRow && claimedOther && (
                                                        <span style={{ color: tokens.textMuted, fontSize: '0.8125rem' }}>{t('fm.taken', 'Alındı')}</span>
                                                    )}
                                                </td>
                                            </tr>
                                        );
                                    })
                                )}
                            </tbody>
                        </table>
                    </div>
                    {totalPages > 1 && (
                        <div style={{ marginTop: 16, display: 'flex', gap: 8, alignItems: 'center' }}>
                            <button
                                style={{
                                    padding: '6px 12px',
                                    border: `1px solid ${tokens.border}`,
                                    borderRadius: 8,
                                    background: tokens.bgCard,
                                    color: tokens.text,
                                    cursor: page === 0 ? 'not-allowed' : 'pointer',
                                    opacity: page === 0 ? 0.6 : 1,
                                }}
                                onClick={() => setPage((p) => Math.max(0, p - 1))}
                                disabled={page === 0}
                            >
                                {t('news.prev', 'Önceki')}
                            </button>
                            <span style={{ color: tokens.textMuted }}>
                                Sayfa {page + 1} / {totalPages} (toplam {totalElements})
                            </span>
                            <button
                                style={{
                                    padding: '6px 12px',
                                    border: `1px solid ${tokens.border}`,
                                    borderRadius: 8,
                                    background: tokens.bgCard,
                                    color: tokens.text,
                                    cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer',
                                    opacity: page >= totalPages - 1 ? 0.6 : 1,
                                }}
                                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                                disabled={page >= totalPages - 1}
                            >
                                {t('news.next', 'Sonraki')}
                            </button>
                        </div>
                    )}
                </>
            )}
        </div>
    );
}
