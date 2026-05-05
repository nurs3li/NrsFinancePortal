import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
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
    subjectUserId: number | null;
    subjectUsername: string | null;
    assignedFmKeycloakId: string | null;
    claimedAt: string | null;
    claimState: string;
    readOnlyHint: string | null;
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

const COMPLETED_STATUSES = ['APPROVED', 'REJECTED', 'FREEZE_REQUESTED'];

const ACTIONS = [
    { key: 'APPROVE', label: 'Onayla (Temiz)', color: undefined },
    { key: 'SUGGEST_FREEZE', label: 'Freeze Öner', color: 'warning' },
] as const;

export function FmTaskDetail() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const { tokens } = useTheme();
    const [task, setTask] = useState<ReviewTaskView | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [actionLoading, setActionLoading] = useState<string | null>(null);
    const [claimLoading, setClaimLoading] = useState(false);

    const [ctx, setCtx] = useState<InvestigationContext | null>(null);
    const [ctxLoading, setCtxLoading] = useState(false);
    const [ctxOpen, setCtxOpen] = useState(false);

    const loadTask = () => {
        if (!id) return;
        financeClient
            .get(`/api/tasks/${id}`)
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                setTask(raw as ReviewTaskView);
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'Görev alınamadı';
                setError(msg);
            })
            .finally(() => setLoading(false));
    };

    useEffect(() => {
        loadTask();
    }, [id]);

    const loadContext = () => {
        if (!id || ctx) { setCtxOpen(true); return; }
        setCtxLoading(true);
        financeClient
            .get(`/api/tasks/${id}/context`)
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                setCtx(raw as InvestigationContext);
                setCtxOpen(true);
            })
            .catch(() => setError('İnceleme verisi yüklenemedi'))
            .finally(() => setCtxLoading(false));
    };

    const handleAction = (action: string) => {
        if (!id) return;
        setActionLoading(action);
        financeClient
            .patch(`/api/tasks/${id}`, { action, accountId: task?.accountId ?? undefined })
            .then(() => { loadTask(); })
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'İşlem başarısız';
                setError(msg);
            })
            .finally(() => setActionLoading(null));
    };

    const handleClaim = () => {
        if (!id) return;
        setClaimLoading(true);
        financeClient
            .post(`/api/tasks/${id}/claim`)
            .then(() => loadTask())
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'Üstlenilemedi';
                setError(msg);
            })
            .finally(() => setClaimLoading(false));
    };

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };
    const cardStyle: React.CSSProperties = { padding: 16, borderRadius: 12, background: tokens.bgCard, border: `1px solid ${tokens.border}`, marginBottom: 16 };
    const btnStyle: React.CSSProperties = { marginRight: 8, marginTop: 8, padding: '8px 16px', borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text, cursor: 'pointer' };
    const sectionTitle: React.CSSProperties = { fontSize: '0.8125rem', fontWeight: 700, color: tokens.textMuted, textTransform: 'uppercase' as const, letterSpacing: 1, marginBottom: 8, marginTop: 20 };
    const tableStyle: React.CSSProperties = { width: '100%', borderCollapse: 'collapse' as const, fontSize: '0.8125rem' };
    const thStyle: React.CSSProperties = { textAlign: 'left' as const, padding: '6px 10px', borderBottom: `1px solid ${tokens.border}`, color: tokens.textMuted, fontWeight: 600 };
    const tdStyle: React.CSSProperties = { padding: '6px 10px', borderBottom: `1px solid ${tokens.border}` };

    const formatDate = (s: string | null) => (s ? new Date(s).toLocaleString('tr-TR') : '–');
    const formatMoney = (n: number | null | undefined) => n != null ? `₺${Number(n).toLocaleString('tr-TR', { minimumFractionDigits: 2 })}` : '–';

    if (loading) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Görev detayı</h1>
                <p style={mutedStyle}>Yükleniyor...</p>
            </div>
        );
    }
    if (error && !task) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Görev detayı</h1>
                <p style={{ color: tokens.error }}>Hata: {error}</p>
                <button style={btnStyle} onClick={() => navigate('/fm/tasks')}>← Listeye dön</button>
            </div>
        );
    }
    if (!task) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>Görev detayı</h1>
                <p style={mutedStyle}>Görev bulunamadı.</p>
                <button style={btnStyle} onClick={() => navigate('/fm/tasks')}>← Listeye dön</button>
            </div>
        );
    }

    const isCompleted = COMPLETED_STATUSES.includes(task.status);

    return (
        <div style={pageStyle}>
            <div style={{ marginBottom: 16 }}>
                <button style={btnStyle} onClick={() => navigate('/fm/tasks')}>← Listeye dön</button>
            </div>
            <h1 style={titleStyle}>Görev #{task.id}</h1>
            <p style={mutedStyle}>{task.type} • Referans: {task.referenceId}</p>

            {error && <p style={{ color: tokens.error, marginBottom: 16 }}>{error}</p>}
            {task.readOnlyHint && (
                <div style={{
                    marginBottom: 16,
                    padding: '10px 14px',
                    borderRadius: 10,
                    background: 'rgba(148,163,184,0.15)',
                    border: `1px solid ${tokens.border}`,
                    fontSize: '0.875rem',
                }}>
                    {task.readOnlyHint}
                </div>
            )}

            <div style={cardStyle}>
                <div style={{ display: 'grid', gap: 8, marginBottom: 16 }}>
                    <div><strong>Tip:</strong> {task.type}</div>
                    <div><strong>Kullanıcı:</strong> {task.subjectUsername ?? '–'} <span style={{ color: tokens.textMuted }}>(ID: {task.subjectUserId ?? '–'})</span></div>
                    <div><strong>Durum:</strong> {task.status}</div>
                    <div><strong>Öncelik:</strong> {task.priority}</div>
                    <div><strong>Son tarih:</strong> {formatDate(task.dueAt)}</div>
                    <div><strong>Oluşturulma:</strong> {formatDate(task.createdAt)}</div>
                    {task.outcome && <div><strong>Sonuç:</strong> {task.outcome}</div>}
                    {task.accountId != null && <div><strong>Hesap ID:</strong> {task.accountId}</div>}
                </div>

                {/* İncele butonu */}
                <div style={{ borderTop: `1px solid ${tokens.border}`, paddingTop: 12 }}>
                    <button
                        style={{
                            ...btnStyle,
                            background: ctxOpen ? tokens.accent : tokens.bgCard,
                            color: ctxOpen ? '#fff' : tokens.accent,
                            borderColor: tokens.accent,
                            fontWeight: 600,
                        }}
                        onClick={() => ctxOpen ? setCtxOpen(false) : loadContext()}
                        disabled={ctxLoading}
                    >
                        {ctxLoading ? 'Yükleniyor...' : ctxOpen ? 'İncelemeyi Kapat ▲' : 'İncele ▼'}
                    </button>
                </div>

                {/* İnceleme paneli */}
                {ctxOpen && ctx && (
                    <div style={{ marginTop: 16 }}>
                        {/* Kullanıcı bilgisi */}
                        <div style={sectionTitle}>Kullanıcı Bilgisi</div>
                        <div style={{ ...cardStyle, background: tokens.bg }}>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6, fontSize: '0.875rem' }}>
                                <div><strong>Kullanıcı:</strong> {ctx.user.username ?? '–'}</div>
                                <div><strong>E-posta:</strong> {ctx.user.email ?? '–'}</div>
                                <div><strong>Rol:</strong> {ctx.user.role}</div>
                                <div>
                                    <strong>Whale:</strong>{' '}
                                    {ctx.user.whale ? (
                                        <span style={{ color: '#e74c3c', fontWeight: 600 }}>{ctx.user.whaleLevel}</span>
                                    ) : (
                                        <span style={{ color: tokens.textMuted }}>Hayır</span>
                                    )}
                                </div>
                            </div>
                        </div>

                        {/* Hesap bilgisi */}
                        <div style={sectionTitle}>Hesap Durumu</div>
                        <div style={{ ...cardStyle, background: tokens.bg }}>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6, fontSize: '0.875rem' }}>
                                <div><strong>Hesap ID:</strong> {ctx.account.id}</div>
                                <div><strong>Tip:</strong> {ctx.account.type}</div>
                                <div>
                                    <strong>Durum:</strong>{' '}
                                    <span style={{
                                        fontWeight: 600,
                                        color: ctx.account.status === 'FROZEN' ? '#e74c3c' : '#27ae60',
                                    }}>
                                        {ctx.account.status}
                                    </span>
                                </div>
                                {ctx.account.frozenAt && (
                                    <div><strong>Dondurulma:</strong> {formatDate(ctx.account.frozenAt)}</div>
                                )}
                                {ctx.account.frozenReason && (
                                    <div style={{ gridColumn: '1 / -1' }}><strong>Sebep:</strong> {ctx.account.frozenReason}</div>
                                )}
                            </div>
                        </div>

                        {/* Şüpheli olay detayı */}
                        {ctx.suspiciousEvent && (
                            <>
                                <div style={sectionTitle}>Şüpheli Olay Detayı</div>
                                <div style={{ ...cardStyle, background: tokens.bg, borderLeft: '3px solid #e74c3c' }}>
                                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6, fontSize: '0.875rem' }}>
                                        <div><strong>Sebep:</strong> <span style={{ color: '#e74c3c', fontWeight: 600 }}>{ctx.suspiciousEvent.reason}</span></div>
                                        <div><strong>Tarih:</strong> {formatDate(ctx.suspiciousEvent.occurredAt)}</div>
                                        <div><strong>İşlem tutarı:</strong> {formatMoney(ctx.suspiciousEvent.amount)}</div>
                                        <div><strong>Penceredeki işlem sayısı:</strong> {ctx.suspiciousEvent.countInWindow ?? '–'}</div>
                                        <div><strong>Eşik tutar:</strong> {formatMoney(ctx.suspiciousEvent.thresholdAmount)}</div>
                                        <div><strong>Eşik sayı:</strong> {ctx.suspiciousEvent.thresholdCount ?? '–'}</div>
                                    </div>
                                </div>
                            </>
                        )}

                        {/* Whale geçmişi */}
                        {ctx.whaleHistory.length > 0 && (
                            <>
                                <div style={sectionTitle}>Whale Geçmişi (son 10)</div>
                                <div style={{ ...cardStyle, background: tokens.bg, overflowX: 'auto' }}>
                                    <table style={tableStyle}>
                                        <thead>
                                            <tr>
                                                <th style={thStyle}>Seviye</th>
                                                <th style={thStyle}>Impact</th>
                                                <th style={thStyle}>Sebep</th>
                                                <th style={thStyle}>Tarih</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {ctx.whaleHistory.map((w) => (
                                                <tr key={w.id}>
                                                    <td style={{ ...tdStyle, fontWeight: 600 }}>{w.whaleLevel}</td>
                                                    <td style={tdStyle}>{w.impactScore != null ? `${w.impactScore}/100` : '–'}</td>
                                                    <td style={tdStyle}>{w.reason ?? '–'}</td>
                                                    <td style={tdStyle}>{formatDate(w.triggeredAt)}</td>
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
                                <div style={{ ...cardStyle, background: tokens.bg, overflowX: 'auto' }}>
                                    <table style={tableStyle}>
                                        <thead>
                                            <tr>
                                                <th style={thStyle}>#</th>
                                                <th style={thStyle}>Tip</th>
                                                <th style={thStyle}>Tutar</th>
                                                <th style={thStyle}>Bakiye</th>
                                                <th style={thStyle}>Tarih</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {ctx.recentTransactions.map((tx) => (
                                                <tr key={tx.id}>
                                                    <td style={tdStyle}>{tx.id}</td>
                                                    <td style={{
                                                        ...tdStyle,
                                                        fontWeight: 600,
                                                        color: tx.type === 'DEPOSIT' ? '#27ae60'
                                                            : tx.type === 'WITHDRAW' ? '#e74c3c'
                                                            : tokens.text,
                                                    }}>
                                                        {tx.type}
                                                    </td>
                                                    <td style={tdStyle}>{formatMoney(tx.amount)}</td>
                                                    <td style={tdStyle}>{formatMoney(tx.balanceAfter)}</td>
                                                    <td style={tdStyle}>{formatDate(tx.createdAt)}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            </>
                        )}
                    </div>
                )}

                {!isCompleted && task.assigneeRole === 'FINANCE_MANAGER' && task.status === 'PENDING' && (task.claimState ?? 'POOL') === 'POOL' && (
                    <div style={{ borderTop: `1px solid ${tokens.border}`, paddingTop: 16, marginTop: 16 }}>
                        <button
                            type="button"
                            style={{
                                padding: '10px 20px',
                                borderRadius: 10,
                                border: 'none',
                                fontWeight: 700,
                                cursor: claimLoading ? 'wait' : 'pointer',
                                background: 'linear-gradient(135deg, #059669, #10b981)',
                                color: '#fff',
                            }}
                            disabled={claimLoading}
                            onClick={handleClaim}
                        >
                            {claimLoading ? '…' : 'Üzerime Al'}
                        </button>
                    </div>
                )}

                {/* Aksiyon butonları */}
                {isCompleted ? (
                    <div style={{
                        borderTop: `1px solid ${tokens.border}`,
                        paddingTop: 16,
                        marginTop: 16,
                        color: tokens.textMuted,
                        fontSize: '0.875rem',
                    }}>
                        Bu görev tamamlanmış: <strong>{task.outcome ?? task.status}</strong>
                    </div>
                ) : (
                    <div style={{ borderTop: `1px solid ${tokens.border}`, paddingTop: 16, marginTop: 16 }}>
                        <div style={{ fontSize: '0.875rem', color: tokens.textMuted, marginBottom: 8 }}>Aksiyon</div>
                        {ACTIONS.map((a) => {
                            const blocked = !!task.readOnlyHint || (task.claimState ?? '') === 'OTHER';
                            return (
                                <button
                                    key={a.key}
                                    style={{
                                        ...btnStyle,
                                        opacity: actionLoading === a.key ? 0.7 : blocked ? 0.45 : 1,
                                        ...(a.color === 'warning' ? { borderColor: '#e67e22', color: '#e67e22' } : {}),
                                    }}
                                    disabled={!!actionLoading || blocked}
                                    onClick={() => handleAction(a.key)}
                                >
                                    {actionLoading === a.key ? '...' : a.label}
                                </button>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
}
