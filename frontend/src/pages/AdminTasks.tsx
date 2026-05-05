import { Fragment, useEffect, useRef, useState } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import keycloak from '../auth/keycloak';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8085';

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
    subjectUserId: number | null;
    subjectUsername: string | null;
};

type PageResponse<T> = {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
};

type InvestigationContext = {
    user: { id: number; username: string; email: string; role: string; whale: boolean; whaleLevel: string | null };
    account: { id: number; type: string; status: string; frozenAt: string | null; frozenReason: string | null };
    suspiciousEvent: {
        id: number; reason: string; amount: number | null;
        countInWindow: number | null; thresholdAmount: number | null;
        thresholdCount: number | null; occurredAt: string;
    } | null;
    whaleHistory: {
        id: number; whaleLevel: string; impactScore: number | null;
        reason: string | null; triggeredAt: string;
    }[];
    recentTransactions: {
        id: number; type: string; amount: number;
        balanceAfter: number; createdAt: string;
    }[];
};

const COMPLETED_STATUSES = ['APPROVED', 'REJECTED'];

export function AdminTasks() {
    const { tokens } = useTheme();
    const [tasks, setTasks] = useState<ReviewTaskView[]>([]);
    const [totalPages, setTotalPages] = useState(0);
    const [page, setPage] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [status, setStatus] = useState<string>('');
    const [actionLoading, setActionLoading] = useState<number | null>(null);
    const [reason, setReason] = useState('');
    const [reasonModal, setReasonModal] = useState<{ taskId: number; action: string } | null>(null);
    const [forceModalTaskId, setForceModalTaskId] = useState<number | null>(null);
    const [fmOptions, setFmOptions] = useState<{ id: number; username: string; email: string }[]>([]);
    const [selectedFmId, setSelectedFmId] = useState<string>('');

    const [expandedId, setExpandedId] = useState<number | null>(null);
    const [ctxCache, setCtxCache] = useState<Record<number, InvestigationContext>>({});
    const [ctxLoading, setCtxLoading] = useState<number | null>(null);

    const fetchTasks = () => {
        setLoading(true);
        const params: Record<string, string | number> = { page, size: 20 };
        if (status) params.status = status;
        financeClient
            .get('/api/admin/tasks', { params })
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                const pageData = raw as PageResponse<ReviewTaskView>;
                setTasks(pageData?.content ?? []);
                setTotalPages(pageData?.totalPages ?? 0);
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'Liste alınamadı';
                setError(msg);
            })
            .finally(() => setLoading(false));
    };

    useEffect(() => {
        fetchTasks();
    }, [page, status]);

    const sseAbortRef = useRef<AbortController | null>(null);
    useEffect(() => {
        sseAbortRef.current?.abort();
        if (!keycloak.token) return;
        const ac = new AbortController();
        sseAbortRef.current = ac;
        (async () => {
            try {
                const res = await fetch(`${API_BASE}/api/tasks/sse/admin`, {
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
                    if (buf.includes('TASK_ESCALATED_ADMIN')) {
                        buf = '';
                        fetchTasks();
                    }
                    if (buf.length > 64_000) buf = buf.slice(-32_000);
                }
            } catch {
                /* ignore */
            }
        })();
        return () => ac.abort();
    }, [page, status]);

    const toggleContext = (taskId: number) => {
        if (expandedId === taskId) {
            setExpandedId(null);
            return;
        }
        if (ctxCache[taskId]) {
            setExpandedId(taskId);
            return;
        }
        setCtxLoading(taskId);
        financeClient
            .get(`/api/tasks/${taskId}/context`)
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                setCtxCache((prev) => ({ ...prev, [taskId]: raw as InvestigationContext }));
                setExpandedId(taskId);
            })
            .catch(() => setError('İnceleme verisi yüklenemedi'))
            .finally(() => setCtxLoading(null));
    };

    const handleAdminPatch = (taskId: number, body: Record<string, string | undefined>) => {
        setActionLoading(taskId);
        financeClient
            .patch(`/api/admin/tasks/${taskId}`, body)
            .then(() => {
                setReasonModal(null);
                setReason('');
                setForceModalTaskId(null);
                setSelectedFmId('');
                fetchTasks();
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'İşlem başarısız';
                setError(msg);
            })
            .finally(() => setActionLoading(null));
    };

    const handleAdminAction = (taskId: number, action: 'FREEZE' | 'REJECT_FREEZE', reasonValue?: string) => {
        handleAdminPatch(taskId, { action, reason: reasonValue ?? undefined });
    };

    const openForceModal = (taskId: number) => {
        setForceModalTaskId(taskId);
        setSelectedFmId('');
        financeClient
            .get('/api/admin/tasks/finance-managers')
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                setFmOptions(Array.isArray(raw) ? raw : []);
            })
            .catch(() => setFmOptions([]));
    };

    const submitForceAssign = () => {
        if (forceModalTaskId == null || !selectedFmId) return;
        handleAdminPatch(forceModalTaskId, { action: 'FORCE_ASSIGN_FM', fmUserId: selectedFmId });
    };

    const isEscalatedInvestigation = (row: ReviewTaskView) =>
        row.status === 'ESCALATED'
        && row.assigneeRole === 'ADMIN'
        && (row.type === 'SUSPICIOUS_REVIEW' || row.type === 'WHALE_REVIEW');

    const openReasonModal = (taskId: number, action: string) => {
        setReasonModal({ taskId, action });
        setReason('');
    };

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };
    const tableStyle: React.CSSProperties = { width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' };
    const thStyle: React.CSSProperties = { textAlign: 'left', padding: '10px 12px', borderBottom: `2px solid ${tokens.border}`, background: tokens.bgCard };
    const tdStyle: React.CSSProperties = { padding: '10px 12px', borderBottom: `1px solid ${tokens.border}` };
    const btnStyle: React.CSSProperties = { padding: '6px 12px', marginRight: 8, borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text, cursor: 'pointer', fontSize: '0.875rem' };
    const innerThStyle: React.CSSProperties = { textAlign: 'left', padding: '6px 10px', borderBottom: `1px solid ${tokens.border}`, color: tokens.textMuted, fontWeight: 600, fontSize: '0.8125rem' };
    const innerTdStyle: React.CSSProperties = { padding: '6px 10px', borderBottom: `1px solid ${tokens.border}`, fontSize: '0.8125rem' };
    const sectionTitle: React.CSSProperties = { fontSize: '0.75rem', fontWeight: 700, color: tokens.textMuted, textTransform: 'uppercase' as const, letterSpacing: 1, marginBottom: 6, marginTop: 14 };

    const formatDate = (s: string | null) => (s ? new Date(s).toLocaleString('tr-TR') : '–');
    const formatMoney = (n: number | null | undefined) => n != null ? `₺${Number(n).toLocaleString('tr-TR', { minimumFractionDigits: 2 })}` : '–';

    const renderContext = (ctx: InvestigationContext, row: ReviewTaskView) => (
        <td colSpan={8} style={{ padding: 16, background: tokens.bg, borderBottom: `2px solid ${tokens.accent}` }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                {/* Kullanıcı */}
                <div>
                    <div style={sectionTitle}>Kullanıcı Bilgisi</div>
                    <div style={{ fontSize: '0.8125rem', display: 'grid', gap: 4 }}>
                        <div><strong>Kullanıcı:</strong> {ctx.user.username ?? '–'}</div>
                        <div><strong>E-posta:</strong> {ctx.user.email ?? '–'}</div>
                        <div>
                            <strong>Whale:</strong>{' '}
                            {ctx.user.whale
                                ? <span style={{ color: '#e74c3c', fontWeight: 600 }}>{ctx.user.whaleLevel}</span>
                                : <span style={{ color: tokens.textMuted }}>Hayır</span>}
                        </div>
                    </div>
                </div>
                {/* Hesap */}
                <div>
                    <div style={sectionTitle}>Hesap Durumu</div>
                    <div style={{ fontSize: '0.8125rem', display: 'grid', gap: 4 }}>
                        <div><strong>Hesap:</strong> #{ctx.account.id} ({ctx.account.type})</div>
                        <div>
                            <strong>Durum:</strong>{' '}
                            <span style={{ fontWeight: 600, color: ctx.account.status === 'FROZEN' ? '#e74c3c' : '#27ae60' }}>
                                {ctx.account.status}
                            </span>
                        </div>
                        {ctx.account.frozenReason && <div><strong>Sebep:</strong> {ctx.account.frozenReason}</div>}
                    </div>
                </div>
            </div>

            {/* Şüpheli olay */}
            {ctx.suspiciousEvent && (
                <>
                    <div style={sectionTitle}>Şüpheli Olay Detayı</div>
                    <div style={{
                        padding: 10, borderRadius: 8, background: tokens.bgCard,
                        border: `1px solid ${tokens.border}`, borderLeft: '3px solid #e74c3c',
                        fontSize: '0.8125rem', display: 'flex', flexWrap: 'wrap', gap: 16,
                    }}>
                        <span><strong>Sebep:</strong> <span style={{ color: '#e74c3c', fontWeight: 600 }}>{ctx.suspiciousEvent.reason}</span></span>
                        <span><strong>Tutar:</strong> {formatMoney(ctx.suspiciousEvent.amount)}</span>
                        <span><strong>Pencere:</strong> {ctx.suspiciousEvent.countInWindow ?? '–'} işlem</span>
                        <span><strong>Eşik tutar:</strong> {formatMoney(ctx.suspiciousEvent.thresholdAmount)}</span>
                        <span><strong>Eşik sayı:</strong> {ctx.suspiciousEvent.thresholdCount ?? '–'}</span>
                        <span><strong>Tarih:</strong> {formatDate(ctx.suspiciousEvent.occurredAt)}</span>
                    </div>
                </>
            )}

            {/* Whale geçmişi */}
            {ctx.whaleHistory.length > 0 && (
                <>
                    <div style={sectionTitle}>Whale Geçmişi (son 10)</div>
                    <div style={{ overflowX: 'auto' }}>
                        <table style={{ ...tableStyle, fontSize: '0.8125rem' }}>
                            <thead>
                                <tr>
                                    <th style={innerThStyle}>Seviye</th>
                                    <th style={innerThStyle}>Impact</th>
                                    <th style={innerThStyle}>Sebep</th>
                                    <th style={innerThStyle}>Tarih</th>
                                </tr>
                            </thead>
                            <tbody>
                                {ctx.whaleHistory.map((w) => (
                                    <tr key={w.id}>
                                        <td style={{ ...innerTdStyle, fontWeight: 600 }}>{w.whaleLevel}</td>
                                        <td style={innerTdStyle}>{w.impactScore != null ? `${w.impactScore}/100` : '–'}</td>
                                        <td style={innerTdStyle}>{w.reason ?? '–'}</td>
                                        <td style={innerTdStyle}>{formatDate(w.triggeredAt)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </>
            )}

            {/* Son işlemler */}
            {ctx.recentTransactions.length > 0 && (
                <>
                    <div style={sectionTitle}>Son İşlemler (son 20)</div>
                    <div style={{ overflowX: 'auto' }}>
                        <table style={{ ...tableStyle, fontSize: '0.8125rem' }}>
                            <thead>
                                <tr>
                                    <th style={innerThStyle}>#</th>
                                    <th style={innerThStyle}>Tip</th>
                                    <th style={innerThStyle}>Tutar</th>
                                    <th style={innerThStyle}>Bakiye</th>
                                    <th style={innerThStyle}>Tarih</th>
                                </tr>
                            </thead>
                            <tbody>
                                {ctx.recentTransactions.map((tx) => (
                                    <tr key={tx.id}>
                                        <td style={innerTdStyle}>{tx.id}</td>
                                        <td style={{
                                            ...innerTdStyle, fontWeight: 600,
                                            color: tx.type === 'DEPOSIT' ? '#27ae60' : tx.type === 'WITHDRAW' ? '#e74c3c' : tokens.text,
                                        }}>{tx.type}</td>
                                        <td style={innerTdStyle}>{formatMoney(tx.amount)}</td>
                                        <td style={innerTdStyle}>{formatMoney(tx.balanceAfter)}</td>
                                        <td style={innerTdStyle}>{formatDate(tx.createdAt)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </>
            )}

            {/* Aksiyon butonları (panel içinde de erişilebilir) */}
            {!COMPLETED_STATUSES.includes(row.status) && row.type === 'FREEZE_APPROVAL' && (
                <div style={{ marginTop: 14, paddingTop: 12, borderTop: `1px solid ${tokens.border}` }}>
                    <button
                        style={btnStyle}
                        disabled={actionLoading === row.id}
                        onClick={() => openReasonModal(row.id, 'FREEZE')}
                    >
                        {actionLoading === row.id ? '...' : 'Freeze'}
                    </button>
                    <button
                        style={{ ...btnStyle, borderColor: tokens.error, color: tokens.error }}
                        disabled={actionLoading === row.id}
                        onClick={() => openReasonModal(row.id, 'REJECT_FREEZE')}
                    >
                        Reddet
                    </button>
                </div>
            )}
        </td>
    );

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Admin Görevler</h1>
            <p style={mutedStyle}>Freeze talepleri ve escalation görevleri.</p>

            <div style={{ marginTop: 16, marginBottom: 16 }}>
                <label style={{ marginRight: 8, color: tokens.textMuted }}>Durum: </label>
                <select
                    style={{ padding: '6px 12px', borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text }}
                    value={status}
                    onChange={(e) => { setStatus(e.target.value); setPage(0); }}
                >
                    <option value="">Tümü</option>
                    <option value="PENDING">PENDING</option>
                    <option value="ESCALATED">ESCALATED</option>
                    <option value="FREEZE_REQUESTED">FREEZE_REQUESTED</option>
                    <option value="REJECTED">REJECTED</option>
                </select>
            </div>

            {error && <p style={{ color: tokens.error, marginBottom: 16 }}>Hata: {error}</p>}
            {loading && <p style={mutedStyle}>Yükleniyor...</p>}

            {!loading && (
                <div style={{ overflowX: 'auto', border: `1px solid ${tokens.border}`, borderRadius: 12, background: tokens.bgCard }}>
                    <table style={tableStyle}>
                        <thead>
                        <tr>
                            <th style={thStyle}>ID</th>
                            <th style={thStyle}>Tip</th>
                            <th style={thStyle}>Referans</th>
                            <th style={thStyle}>Kullanıcı</th>
                            <th style={thStyle}>Kullanıcı ID</th>
                            <th style={thStyle}>Durum</th>
                            <th style={thStyle}>Son tarih</th>
                            <th style={thStyle}>İşlem</th>
                        </tr>
                        </thead>
                        <tbody>
                        {tasks.length === 0 ? (
                            <tr><td colSpan={8} style={{ ...tdStyle, color: tokens.textMuted, textAlign: 'center' }}>Görev yok.</td></tr>
                        ) : (
                            tasks.map((row) => {
                                const escalated = row.status === 'ESCALATED';
                                const rowBg = escalated ? 'rgba(239,68,68,0.18)' : undefined;
                                return (
                                <Fragment key={row.id}>
                                    <tr style={{ background: rowBg }}>
                                        <td style={tdStyle}>{row.id}</td>
                                        <td style={tdStyle}>{row.type}</td>
                                        <td style={tdStyle}>{row.referenceId}</td>
                                        <td style={{ ...tdStyle, fontWeight: 600 }}>{row.subjectUsername ?? '–'}</td>
                                        <td style={tdStyle}>{row.subjectUserId ?? '–'}</td>
                                        <td style={tdStyle}>
                                            {escalated && (
                                                <span style={{ marginRight: 6, color: '#ef4444', fontWeight: 800 }} title="Eskale">
                                                    ⚠
                                                </span>
                                            )}
                                            {row.status}
                                        </td>
                                        <td style={tdStyle}>{formatDate(row.dueAt)}</td>
                                        <td style={tdStyle}>
                                            <button
                                                style={{
                                                    ...btnStyle,
                                                    borderColor: tokens.accent,
                                                    color: expandedId === row.id ? '#fff' : tokens.accent,
                                                    background: expandedId === row.id ? tokens.accent : tokens.bgCard,
                                                    fontWeight: 600,
                                                }}
                                                disabled={ctxLoading === row.id}
                                                onClick={() => toggleContext(row.id)}
                                            >
                                                {ctxLoading === row.id ? '...' : expandedId === row.id ? 'Kapat ▲' : 'İncele ▼'}
                                            </button>
                                            {!COMPLETED_STATUSES.includes(row.status) && row.type === 'FREEZE_APPROVAL' && (
                                                <>
                                                    <button
                                                        style={btnStyle}
                                                        disabled={actionLoading === row.id}
                                                        onClick={() => openReasonModal(row.id, 'FREEZE')}
                                                    >
                                                        {actionLoading === row.id ? '...' : 'Freeze'}
                                                    </button>
                                                    <button
                                                        style={{ ...btnStyle, borderColor: tokens.error, color: tokens.error }}
                                                        disabled={actionLoading === row.id}
                                                        onClick={() => openReasonModal(row.id, 'REJECT_FREEZE')}
                                                    >
                                                        Reddet
                                                    </button>
                                                </>
                                            )}
                                            {COMPLETED_STATUSES.includes(row.status) && (
                                                <span style={{ color: tokens.textMuted, fontSize: '0.8125rem' }}>{row.outcome ?? row.status}</span>
                                            )}
                                            {isEscalatedInvestigation(row) && (
                                                <>
                                                    <button
                                                        type="button"
                                                        style={{ ...btnStyle, borderColor: '#6366f1', color: '#a5b4fc' }}
                                                        disabled={actionLoading === row.id}
                                                        onClick={() => openForceModal(row.id)}
                                                    >
                                                        Zorla Ata
                                                    </button>
                                                    <button
                                                        type="button"
                                                        style={{ ...btnStyle, borderColor: '#22c55e', color: '#86efac' }}
                                                        disabled={actionLoading === row.id}
                                                        onClick={() => handleAdminPatch(row.id, { action: 'RESOLVE_ESCALATED_CLEAR' })}
                                                    >
                                                        Doğrudan Çöz (Temiz)
                                                    </button>
                                                    <button
                                                        type="button"
                                                        style={{ ...btnStyle, borderColor: '#f97316', color: '#fdba74' }}
                                                        disabled={actionLoading === row.id}
                                                        onClick={() => openReasonModal(row.id, 'RESOLVE_ESCALATED_FREEZE')}
                                                    >
                                                        Doğrudan Freeze
                                                    </button>
                                                </>
                                            )}
                                        </td>
                                    </tr>
                                    {expandedId === row.id && ctxCache[row.id] && (
                                        <tr>
                                            {renderContext(ctxCache[row.id], row)}
                                        </tr>
                                    )}
                                </Fragment>
                            );
                            })
                        )}
                        </tbody>
                    </table>
                </div>
            )}

            {totalPages > 1 && (
                <div style={{ marginTop: 16, display: 'flex', gap: 8, alignItems: 'center' }}>
                    <button
                        style={{ ...btnStyle, opacity: page === 0 ? 0.6 : 1 }}
                        onClick={() => setPage((p) => Math.max(0, p - 1))}
                        disabled={page === 0}
                    >Önceki</button>
                    <span style={{ color: tokens.textMuted }}>Sayfa {page + 1} / {totalPages}</span>
                    <button
                        style={{ ...btnStyle, opacity: page >= totalPages - 1 ? 0.6 : 1 }}
                        onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                        disabled={page >= totalPages - 1}
                    >Sonraki</button>
                </div>
            )}

            {reasonModal && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
                    <div style={{ background: tokens.bgCard, padding: 24, borderRadius: 12, border: `1px solid ${tokens.border}`, minWidth: 320 }}>
                        <h3 style={{ marginBottom: 16 }}>Sebep (isteğe bağlı)</h3>
                        <input
                            type="text"
                            value={reason}
                            onChange={(e) => setReason(e.target.value)}
                            placeholder="Sebep..."
                            style={{ width: '100%', padding: 8, marginBottom: 16, borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.inputBg, color: tokens.text }}
                        />
                        <div>
                            <button
                                style={btnStyle}
                                onClick={() => {
                                    if (reasonModal.action === 'RESOLVE_ESCALATED_FREEZE') {
                                        handleAdminPatch(reasonModal.taskId, { action: 'RESOLVE_ESCALATED_FREEZE', reason: reason || undefined });
                                    } else {
                                        handleAdminAction(reasonModal.taskId, reasonModal.action as 'FREEZE' | 'REJECT_FREEZE', reason);
                                    }
                                }}
                            >
                                Gönder
                            </button>
                            <button style={btnStyle} onClick={() => { setReasonModal(null); setReason(''); }}>İptal</button>
                        </div>
                    </div>
                </div>
            )}

            {forceModalTaskId != null && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
                    <div style={{ background: tokens.bgCard, padding: 24, borderRadius: 12, border: `1px solid ${tokens.border}`, minWidth: 360 }}>
                        <h3 style={{ marginBottom: 16 }}>FM seç — Zorla Ata</h3>
                        <select
                            value={selectedFmId}
                            onChange={(e) => setSelectedFmId(e.target.value)}
                            style={{ width: '100%', padding: 8, marginBottom: 16, borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.inputBg, color: tokens.text }}
                        >
                            <option value="">— Seçin —</option>
                            {fmOptions.map((fm) => (
                                <option key={fm.id} value={String(fm.id)}>
                                    {fm.username} ({fm.email})
                                </option>
                            ))}
                        </select>
                        <div>
                            <button style={btnStyle} onClick={submitForceAssign} disabled={!selectedFmId}>Ata</button>
                            <button style={btnStyle} onClick={() => { setForceModalTaskId(null); setSelectedFmId(''); }}>İptal</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
