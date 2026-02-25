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
};

const ACTIONS = [
    { key: 'APPROVE', label: 'Onayla' },
    { key: 'REJECT', label: 'Reddet' },
    { key: 'TAKE_UNDER_MONITORING', label: 'Takibe al' },
    { key: 'SUGGEST_FREEZE', label: 'Freeze öner' },
] as const;

export function FmTaskDetail() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const { tokens } = useTheme();
    const [task, setTask] = useState<ReviewTaskView | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [actionLoading, setActionLoading] = useState<string | null>(null);

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

    const handleAction = (action: string) => {
        if (!id) return;
        setActionLoading(action);
        financeClient
            .patch(`/api/tasks/${id}`, { action, accountId: task?.accountId ?? undefined })
            .then(() => {
                loadTask();
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'İşlem başarısız';
                setError(msg);
            })
            .finally(() => setActionLoading(null));
    };

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };
    const cardStyle: React.CSSProperties = { padding: 16, borderRadius: 12, background: tokens.bgCard, border: `1px solid ${tokens.border}`, marginBottom: 16 };
    const btnStyle: React.CSSProperties = { marginRight: 8, marginTop: 8, padding: '8px 16px', borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text, cursor: 'pointer' };

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

    const formatDate = (s: string | null) => (s ? new Date(s).toLocaleString('tr-TR') : '–');

    return (
        <div style={pageStyle}>
            <div style={{ marginBottom: 16 }}>
                <button style={btnStyle} onClick={() => navigate('/fm/tasks')}>← Listeye dön</button>
            </div>
            <h1 style={titleStyle}>Görev #{task.id}</h1>
            <p style={mutedStyle}>{task.type} • Referans: {task.referenceId}</p>

            {error && <p style={{ color: tokens.error, marginBottom: 16 }}>{error}</p>}

            <div style={cardStyle}>
                <div style={{ display: 'grid', gap: 8, marginBottom: 16 }}>
                    <div><strong>Tip:</strong> {task.type}</div>
                    <div><strong>Durum:</strong> {task.status}</div>
                    <div><strong>Öncelik:</strong> {task.priority}</div>
                    <div><strong>Son tarih:</strong> {formatDate(task.dueAt)}</div>
                    <div><strong>Oluşturulma:</strong> {formatDate(task.createdAt)}</div>
                    {task.outcome && <div><strong>Sonuç:</strong> {task.outcome}</div>}
                    {task.accountId != null && <div><strong>Hesap ID:</strong> {task.accountId}</div>}
                </div>
                <div style={{ borderTop: `1px solid ${tokens.border}`, paddingTop: 16 }}>
                    <div style={{ fontSize: '0.875rem', color: tokens.textMuted, marginBottom: 8 }}>Aksiyon</div>
                    {ACTIONS.map((a) => (
                        <button
                            key={a.key}
                            style={{ ...btnStyle, opacity: actionLoading === a.key ? 0.7 : 1 }}
                            disabled={!!actionLoading}
                            onClick={() => handleAction(a.key)}
                        >
                            {actionLoading === a.key ? '...' : a.label}
                        </button>
                    ))}
                </div>
            </div>
        </div>
    );
}