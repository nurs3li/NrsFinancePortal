import { useCallback, useEffect, useState } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { usePolling } from '../hooks/usePolling';

type FundRequestType = 'DEPOSIT' | 'WITHDRAWAL';
type FundRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

type FundRequestView = {
    id: number;
    userId: number;
    accountId: number;
    type: FundRequestType;
    status: FundRequestStatus;
    amount: number;
    currency: string;
    requestNote: string | null;
    reviewNote: string | null;
    bankAccountIban: string | null;
    receiptFileUrl: string | null;
    referenceNo: string | null;
    sourceBankName: string | null;
    approvedByUserId: number | null;
    approvedAt: string | null;
    rejectedAt: string | null;
    createdAt: string;
};

function unwrapData<T>(res: any): T {
    return (res?.data?.data ?? res?.data) as T;
}

export function FmFundRequests() {
    const { tokens } = useTheme();

    const [items, setItems] = useState<FundRequestView[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);

    const [rejectModalOpen, setRejectModalOpen] = useState(false);
    const [rejectTargetId, setRejectTargetId] = useState<number | null>(null);
    const [rejectNote, setRejectNote] = useState('');

    const fetchPending = useCallback(() => {
        setLoading(true);
        setError(null);

        financeClient
            .get('/api/admin/fund-requests/pending')
            .then((res) => {
                const list = unwrapData<FundRequestView[]>(res) ?? [];
                setItems(list);
            })
            .catch((err) => {
                const msg =
                    err.response?.data?.errors?.error ??
                    err.response?.data?.message ??
                    err.message ??
                    'Liste alınamadı';
                setError(msg);
            })
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        fetchPending();
    }, [fetchPending]);

    useRefetchOnFocus(fetchPending);
    usePolling(fetchPending, 30_000);

    const approve = async (id: number) => {
        setActionLoadingId(id);
        try {
            await financeClient.post(`/api/admin/fund-requests/${id}/approve`, {
                reviewNote: 'Frontend üzerinden onaylandı',
            });
            await fetchPending();
        } catch (err: any) {
            alert(
                err?.response?.data?.errors?.error ??
                    err?.response?.data?.message ??
                    err?.message ??
                    'Onay sırasında hata'
            );
        } finally {
            setActionLoadingId(null);
        }
    };

    const openRejectModal = (id: number) => {
        setRejectTargetId(id);
        setRejectNote('');
        setRejectModalOpen(true);
    };

    const reject = async () => {
        if (!rejectTargetId) return;
        setActionLoadingId(rejectTargetId);

        try {
            await financeClient.post(`/api/admin/fund-requests/${rejectTargetId}/reject`, {
                reviewNote: rejectNote?.trim() || 'Frontend üzerinden reddedildi',
            });
            setRejectModalOpen(false);
            setRejectTargetId(null);
            setRejectNote('');
            await fetchPending();
        } catch (err: any) {
            alert(
                err?.response?.data?.errors?.error ??
                    err?.response?.data?.message ??
                    err?.message ??
                    'Red sırasında hata'
            );
        } finally {
            setActionLoadingId(null);
        }
    };

    const fmtMoney = (v: number, ccy: string) =>
        `${Number(v).toLocaleString('tr-TR', { maximumFractionDigits: 2 })} ${ccy || 'TRY'}`;

    const pageStyle: React.CSSProperties = {
        padding: 24,
        background: tokens.bg,
        color: tokens.text,
        minHeight: '100%',
    };

    const cardStyle: React.CSSProperties = {
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        borderRadius: 12,
        padding: 16,
    };

    const tableStyle: React.CSSProperties = {
        width: '100%',
        borderCollapse: 'collapse',
        fontSize: '0.875rem',
    };

    const thStyle: React.CSSProperties = {
        textAlign: 'left',
        padding: '10px 12px',
        borderBottom: `2px solid ${tokens.border}`,
        background: tokens.bgCard,
        whiteSpace: 'nowrap',
    };

    const tdStyle: React.CSSProperties = {
        padding: '10px 12px',
        borderBottom: `1px solid ${tokens.tableBorder}`,
        verticalAlign: 'top',
    };

    const badgeStyle = (type: FundRequestType): React.CSSProperties => ({
        display: 'inline-block',
        borderRadius: 999,
        padding: '2px 10px',
        fontSize: '0.75rem',
        fontWeight: 600,
        color: '#fff',
        background:
            type === 'DEPOSIT'
                ? 'linear-gradient(90deg, #16a34a, #22c55e)'
                : 'linear-gradient(90deg, #f59e0b, #f97316)',
    });

    return (
        <div style={pageStyle}>
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: 6 }}>Para Talepleri</h1>
            <p style={{ color: tokens.textMuted, fontSize: '0.875rem', marginBottom: 16 }}>
                Finance Manager onay/reddet ekranı. Yalnızca PENDING talepler listelenir.
            </p>

            <div style={cardStyle}>
                {loading && <p style={{ color: tokens.textMuted }}>Yükleniyor...</p>}
                {error && <p style={{ color: tokens.error }}>Hata: {error}</p>}

                {!loading && !error && (
                    <div style={{ overflowX: 'auto' }}>
                        <table style={tableStyle}>
                            <thead>
                                <tr>
                                    <th style={thStyle}>Talep</th>
                                    <th style={thStyle}>Kullanıcı</th>
                                    <th style={thStyle}>Tip</th>
                                    <th style={thStyle}>Tutar</th>
                                    <th style={thStyle}>Banka / Ref</th>
                                    <th style={thStyle}>Dekont</th>
                                    <th style={thStyle}>Not</th>
                                    <th style={thStyle}>Tarih</th>
                                    <th style={thStyle}>İşlem</th>
                                </tr>
                            </thead>
                            <tbody>
                                {items.length === 0 ? (
                                    <tr>
                                        <td colSpan={9} style={{ ...tdStyle, textAlign: 'center', color: tokens.textMuted }}>
                                            Bekleyen talep yok.
                                        </td>
                                    </tr>
                                ) : (
                                    items.map((r) => (
                                        <tr key={r.id}>
                                            <td style={tdStyle}>
                                                <div>#{r.id}</div>
                                                <div style={{ color: tokens.textMuted, fontSize: '0.75rem' }}>
                                                    accountId: {r.accountId}
                                                </div>
                                            </td>

                                            <td style={tdStyle}>
                                                <div>userId: {r.userId}</div>
                                            </td>

                                            <td style={tdStyle}>
                                                <span style={badgeStyle(r.type)}>{r.type}</span>
                                            </td>

                                            <td style={tdStyle}>{fmtMoney(r.amount, r.currency)}</td>

                                            <td style={tdStyle}>
                                                <div>IBAN: {r.bankAccountIban || '-'}</div>
                                                <div>Banka: {r.sourceBankName || '-'}</div>
                                                <div>Ref No: {r.referenceNo || '-'}</div>
                                            </td>

                                            <td style={tdStyle}>
                                                {r.receiptFileUrl ? (
                                                    <a
                                                        href={r.receiptFileUrl}
                                                        target="_blank"
                                                        rel="noreferrer"
                                                        style={{ color: tokens.accent }}
                                                    >
                                                        Dekontu Aç
                                                    </a>
                                                ) : (
                                                    <span style={{ color: tokens.textMuted }}>-</span>
                                                )}
                                            </td>

                                            <td style={tdStyle}>{r.requestNote || '-'}</td>

                                            <td style={tdStyle}>{new Date(r.createdAt).toLocaleString('tr-TR')}</td>

                                            <td style={tdStyle}>
                                                <div style={{ display: 'flex', gap: 8 }}>
                                                    <button
                                                        type="button"
                                                        disabled={actionLoadingId === r.id}
                                                        onClick={() => approve(r.id)}
                                                        style={{
                                                            padding: '6px 10px',
                                                            borderRadius: 8,
                                                            border: 'none',
                                                            cursor: 'pointer',
                                                            color: '#fff',
                                                            background: 'linear-gradient(90deg,#16a34a,#22c55e)',
                                                            opacity: actionLoadingId === r.id ? 0.7 : 1,
                                                        }}
                                                    >
                                                        Onayla
                                                    </button>
                                                    <button
                                                        type="button"
                                                        disabled={actionLoadingId === r.id}
                                                        onClick={() => openRejectModal(r.id)}
                                                        style={{
                                                            padding: '6px 10px',
                                                            borderRadius: 8,
                                                            border: 'none',
                                                            cursor: 'pointer',
                                                            color: '#fff',
                                                            background: 'linear-gradient(90deg,#ef4444,#dc2626)',
                                                            opacity: actionLoadingId === r.id ? 0.7 : 1,
                                                        }}
                                                    >
                                                        Reddet
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {rejectModalOpen && (
                <div
                    style={{
                        position: 'fixed',
                        inset: 0,
                        background: 'rgba(0,0,0,0.45)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        zIndex: 9999,
                        padding: 16,
                    }}
                >
                    <div
                        style={{
                            width: 'min(560px, 100%)',
                            background: tokens.bgCard,
                            border: `1px solid ${tokens.border}`,
                            borderRadius: 12,
                            padding: 16,
                        }}
                    >
                        <h3 style={{ margin: 0, marginBottom: 8 }}>Talebi Reddet</h3>
                        <p style={{ color: tokens.textMuted, fontSize: '0.875rem', marginTop: 0 }}>
                            Talep ID: {rejectTargetId}
                        </p>
                        <textarea
                            value={rejectNote}
                            onChange={(e) => setRejectNote(e.target.value)}
                            rows={4}
                            placeholder="Red sebebi (review note)"
                            style={{
                                width: '100%',
                                padding: 10,
                                borderRadius: 8,
                                border: `1px solid ${tokens.border}`,
                                background: tokens.inputBg,
                                color: tokens.text,
                                resize: 'vertical',
                            }}
                        />
                        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}>
                            <button
                                type="button"
                                onClick={() => setRejectModalOpen(false)}
                                style={{
                                    padding: '8px 12px',
                                    borderRadius: 8,
                                    border: `1px solid ${tokens.border}`,
                                    background: tokens.bgCard,
                                    color: tokens.text,
                                    cursor: 'pointer',
                                }}
                            >
                                Vazgeç
                            </button>
                            <button
                                type="button"
                                onClick={reject}
                                style={{
                                    padding: '8px 12px',
                                    borderRadius: 8,
                                    border: 'none',
                                    background: 'linear-gradient(90deg,#ef4444,#dc2626)',
                                    color: '#fff',
                                    cursor: 'pointer',
                                }}
                            >
                                Reddet
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
