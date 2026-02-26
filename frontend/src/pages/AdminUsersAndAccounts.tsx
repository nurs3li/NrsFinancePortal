import { useState, useEffect } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';

type UserRow = { id: number; username: string; email: string; role: string };

type AdminAccountView = {
    id: number;
    userId: number;
    userEmail: string | null;
    username: string | null;
    type: string;
    status: string;
    frozenAt: string | null;
    frozenReason: string | null;
};

type PageResponse<T> = {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
};

export function AdminUsersAndAccounts() {
    const { tokens } = useTheme();
    const [users, setUsers] = useState<UserRow[]>([]);
    const [accounts, setAccounts] = useState<AdminAccountView[]>([]);
    const [totalPages, setTotalPages] = useState(0);
    const [page, setPage] = useState(0);
    const [loadingUsers, setLoadingUsers] = useState(true);
    const [loadingAccounts, setLoadingAccounts] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [status, setStatus] = useState<string>('');
    const [actionLoading, setActionLoading] = useState<number | null>(null);
    const [freezeReason, setFreezeReason] = useState('');
    const [freezeModal, setFreezeModal] = useState<number | null>(null);

    useEffect(() => {
        setLoadingUsers(true);
        financeClient
            .get('/api/users')
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                setUsers(Array.isArray(raw) ? raw : []);
            })
            .catch((err) => setError(err.response?.data?.message ?? err.message ?? 'Kullanıcılar yüklenemedi'))
            .finally(() => setLoadingUsers(false));
    }, []);

    const fetchAccounts = () => {
        setLoadingAccounts(true);
        const params: Record<string, string | number> = { page, size: 20 };
        if (status) params.status = status;
        financeClient
            .get('/api/admin/accounts', { params })
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                const pageData = raw as PageResponse<AdminAccountView>;
                setAccounts(pageData?.content ?? []);
                setTotalPages(pageData?.totalPages ?? 0);
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'Hesaplar yüklenemedi';
                setError(msg);
            })
            .finally(() => setLoadingAccounts(false));
    };

    useEffect(() => {
        fetchAccounts();
    }, [page, status]);

    const handleFreeze = (accountId: number, reason?: string) => {
        setActionLoading(accountId);
        financeClient
            .post(`/api/admin/accounts/${accountId}/freeze`, reason != null ? { reason } : {})
            .then(() => {
                setFreezeModal(null);
                setFreezeReason('');
                fetchAccounts();
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'Freeze başarısız';
                setError(msg);
            })
            .finally(() => setActionLoading(null));
    };

    const handleUnfreeze = (accountId: number) => {
        setActionLoading(accountId);
        financeClient
            .post(`/api/admin/accounts/${accountId}/unfreeze`)
            .then(() => fetchAccounts())
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'Unfreeze başarısız';
                setError(msg);
            })
            .finally(() => setActionLoading(null));
    };

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const sectionTitleStyle: React.CSSProperties = { fontSize: '1.25rem', fontWeight: 600, marginTop: 24, marginBottom: 12 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };
    const cardStyle: React.CSSProperties = { padding: 16, borderRadius: 12, background: tokens.bgCard, border: `1px solid ${tokens.border}` };
    const tableStyle: React.CSSProperties = { width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' };
    const thStyle: React.CSSProperties = { textAlign: 'left', padding: '10px 12px', borderBottom: `2px solid ${tokens.border}`, background: tokens.bgCard };
    const tdStyle: React.CSSProperties = { padding: '10px 12px', borderBottom: `1px solid ${tokens.border}` };
    const btnStyle: React.CSSProperties = { padding: '6px 12px', marginRight: 8, borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text, cursor: 'pointer', fontSize: '0.875rem' };

    const formatDate = (s: string | null) => (s ? new Date(s).toLocaleString('tr-TR') : '–');

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Kullanıcı & Hesap Yönetimi</h1>
            <p style={mutedStyle}>Kullanıcı listesi ve hesapları freeze / unfreeze.</p>

            {error && <p style={{ color: tokens.error, marginBottom: 16 }}>Hata: {error}</p>}

            {/* Bölüm 1: Kullanıcılar */}
            <h2 style={sectionTitleStyle}>Kullanıcılar</h2>
            <div style={cardStyle}>
                {loadingUsers ? (
                    <p style={mutedStyle}>Yükleniyor...</p>
                ) : users.length === 0 ? (
                    <p style={mutedStyle}>Kullanıcı bulunamadı.</p>
                ) : (
                    <table style={tableStyle}>
                        <thead>
                        <tr>
                            <th style={thStyle}>ID</th>
                            <th style={thStyle}>Kullanıcı adı</th>
                            <th style={thStyle}>E-posta</th>
                            <th style={thStyle}>Rol</th>
                        </tr>
                        </thead>
                        <tbody>
                        {users.map((u) => (
                            <tr key={u.id} style={{ borderBottom: `1px solid ${tokens.border}` }}>
                                <td style={tdStyle}>{u.id}</td>
                                <td style={tdStyle}>{u.username ?? '—'}</td>
                                <td style={tdStyle}>{u.email ?? '—'}</td>
                                <td style={tdStyle}>{u.role}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </div>

            {/* Bölüm 2: Hesaplar (freeze/unfreeze) */}
            <h2 style={sectionTitleStyle}>Hesaplar (Freeze / Unfreeze)</h2>
            <div style={{ marginBottom: 16 }}>
                <label style={{ marginRight: 8, color: tokens.textMuted }}>Durum: </label>
                <select
                    style={{ padding: '6px 12px', borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text }}
                    value={status}
                    onChange={(e) => { setStatus(e.target.value); setPage(0); }}
                >
                    <option value="">Tümü</option>
                    <option value="ACTIVE">ACTIVE</option>
                    <option value="FROZEN">FROZEN</option>
                </select>
            </div>
            {loadingAccounts && <p style={mutedStyle}>Yükleniyor...</p>}
            {!loadingAccounts && (
                <div style={{ overflowX: 'auto', border: `1px solid ${tokens.border}`, borderRadius: 12, background: tokens.bgCard }}>
                    <table style={tableStyle}>
                        <thead>
                        <tr>
                            <th style={thStyle}>ID</th>
                            <th style={thStyle}>Kullanıcı</th>
                            <th style={thStyle}>Tip</th>
                            <th style={thStyle}>Durum</th>
                            <th style={thStyle}>Freeze tarihi / Sebep</th>
                            <th style={thStyle}>İşlem</th>
                        </tr>
                        </thead>
                        <tbody>
                        {accounts.length === 0 ? (
                            <tr><td colSpan={6} style={{ ...tdStyle, color: tokens.textMuted, textAlign: 'center' }}>Hesap yok.</td></tr>
                        ) : (
                            accounts.map((row) => (
                                <tr key={row.id}>
                                    <td style={tdStyle}>{row.id}</td>
                                    <td style={tdStyle}>{row.userEmail ?? row.username ?? row.userId}</td>
                                    <td style={tdStyle}>{row.type}</td>
                                    <td style={tdStyle}>{row.status}</td>
                                    <td style={tdStyle}>
                                        {row.frozenAt ? formatDate(row.frozenAt) : '–'}
                                        {row.frozenReason && ` • ${row.frozenReason}`}
                                    </td>
                                    <td style={tdStyle}>
                                        {row.status === 'FROZEN' ? (
                                            <button style={btnStyle} disabled={actionLoading === row.id} onClick={() => handleUnfreeze(row.id)}>
                                                {actionLoading === row.id ? '...' : 'Unfreeze'}
                                            </button>
                                        ) : (
                                            <button style={btnStyle} disabled={actionLoading === row.id} onClick={() => setFreezeModal(row.id)}>
                                                {actionLoading === row.id ? '...' : 'Freeze'}
                                            </button>
                                        )}
                                    </td>
                                </tr>
                            ))
                        )}
                        </tbody>
                    </table>
                </div>
            )}
            {totalPages > 1 && (
                <div style={{ marginTop: 16, display: 'flex', gap: 8, alignItems: 'center' }}>
                    <button style={{ ...btnStyle, opacity: page === 0 ? 0.6 : 1 }} onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>Önceki</button>
                    <span style={{ color: tokens.textMuted }}>Sayfa {page + 1} / {totalPages}</span>
                    <button style={{ ...btnStyle, opacity: page >= totalPages - 1 ? 0.6 : 1 }} onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}>Sonraki</button>
                </div>
            )}

            {freezeModal != null && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
                    <div style={{ background: tokens.bgCard, padding: 24, borderRadius: 12, border: `1px solid ${tokens.border}`, minWidth: 320 }}>
                        <h3 style={{ marginBottom: 16 }}>Freeze sebebi (isteğe bağlı)</h3>
                        <input
                            type="text"
                            value={freezeReason}
                            onChange={(e) => setFreezeReason(e.target.value)}
                            placeholder="Sebep..."
                            style={{ width: '100%', padding: 8, marginBottom: 16, borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.inputBg ?? tokens.bgCard, color: tokens.text }}
                        />
                        <div>
                            <button style={btnStyle} onClick={() => handleFreeze(freezeModal, freezeReason || undefined)}>Freeze</button>
                            <button style={btnStyle} onClick={() => { setFreezeModal(null); setFreezeReason(''); }}>İptal</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}