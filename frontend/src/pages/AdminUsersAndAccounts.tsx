import { useState, useEffect, useMemo } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { Ban, Lock, RotateCcw, Settings, Snowflake, Unlock, UserPlus } from 'lucide-react';

type UserRow = { id: number; username: string; email: string; role: string; loginSuspended?: boolean };

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
    const [actionLoading, setActionLoading] = useState<string | null>(null);
    const [freezeReason, setFreezeReason] = useState('');
    const [freezeModal, setFreezeModal] = useState<number | null>(null);
    const [suspendLoginModal, setSuspendLoginModal] = useState<number | null>(null);
    const [suspendLoginReason, setSuspendLoginReason] = useState('');
    const [showTools, setShowTools] = useState(false);

    const adminUserCount = useMemo(() => users.filter((u) => u.role === 'ADMIN').length, [users]);

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
        setActionLoading(`a:${accountId}`);
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

    const handleSuspendLogin = (userId: number, reason?: string) => {
        setActionLoading(`u:${userId}`);
        financeClient
            .post(`/api/admin/users/${userId}/suspend-login`, reason != null ? { reason } : {})
            .then(() => {
                setSuspendLoginModal(null);
                setSuspendLoginReason('');
                return financeClient.get('/api/users');
            })
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                setUsers(Array.isArray(raw) ? raw : []);
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.message ?? err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'Askıya alma başarısız';
                setError(String(msg));
            })
            .finally(() => setActionLoading(null));
    };

    const handleUnsuspendLogin = (userId: number) => {
        setActionLoading(`u:${userId}`);
        financeClient
            .post(`/api/admin/users/${userId}/unsuspend-login`)
            .then(() => financeClient.get('/api/users'))
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                setUsers(Array.isArray(raw) ? raw : []);
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.message ?? err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'Askı kaldırma başarısız';
                setError(String(msg));
            })
            .finally(() => setActionLoading(null));
    };

    const reloadUsers = () =>
        financeClient.get('/api/users').then((res) => {
            const raw = res.data?.data ?? res.data;
            setUsers(Array.isArray(raw) ? raw : []);
        });

    const handleAssignRealmRole = (userId: number, newRole: string) => {
        setActionLoading(`r:${userId}`);
        financeClient
            .post(`/api/admin/users/${userId}/assign-role`, { role: newRole })
            .then(() => reloadUsers())
            .catch((err) => {
                const msg =
                    err.response?.data?.errors?.message ??
                    err.response?.data?.errors?.error ??
                    err.response?.data?.message ??
                    err.message ??
                    'Rol ataması başarısız';
                setError(String(msg));
                void reloadUsers().catch(() => undefined);
            })
            .finally(() => setActionLoading(null));
    };

    const handleUnfreeze = (accountId: number) => {
        setActionLoading(`a:${accountId}`);
        financeClient
            .post(`/api/admin/accounts/${accountId}/unfreeze`)
            .then(() => fetchAccounts())
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'Unfreeze başarısız';
                setError(msg);
            })
            .finally(() => setActionLoading(null));
    };

    const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8081';
    const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'nrs-finance';
    const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'nrs-frontend';

    const pageStyle: React.CSSProperties = { padding: 24, background: '#0A192F', color: '#CCD6F6', minHeight: '100%', fontFamily: 'Inter, Roboto, Arial, sans-serif' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const sectionTitleStyle: React.CSSProperties = { fontSize: '1.05rem', fontWeight: 600, marginTop: 20, marginBottom: 10, color: '#E6F1FF' };
    const mutedStyle: React.CSSProperties = { color: '#8892B0', fontSize: '0.84rem' };
    const cardStyle: React.CSSProperties = { padding: 14, borderRadius: 8, background: 'rgba(17,34,64,0.92)', border: '1px solid rgba(136,146,176,0.35)', boxShadow: '0 8px 22px rgba(2,12,27,0.35)' };
    const tableStyle: React.CSSProperties = { width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' };
    const thStyle: React.CSSProperties = { textAlign: 'left', padding: '8px 10px', borderBottom: '1px solid rgba(136,146,176,0.35)', color: '#8892B0', background: 'transparent', fontSize: '0.8rem' };
    const tdStyle: React.CSSProperties = { padding: '8px 10px', borderBottom: '1px solid rgba(136,146,176,0.22)' };
    const btnStyle: React.CSSProperties = { padding: '6px 10px', marginRight: 8, borderRadius: 8, border: '1px solid rgba(136,146,176,0.35)', background: 'rgba(10,25,47,0.65)', color: '#CCD6F6', cursor: 'pointer', fontSize: '0.82rem' };

    const formatDate = (s: string | null) => (s ? new Date(s).toLocaleString('tr-TR') : '–');

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>Kullanıcı & Hesap Yönetimi</h1>
            <p style={mutedStyle}>
                Hesap bazlı dondurma: yalnızca seçilen hesabın işlemleri (nakit işlemleri) kapatılır. Giriş askısı: kullanıcı Keycloak ile giriş yapamaz — giriş ekranında uyarı gösterilir.
            </p>

            <div style={{ ...cardStyle, marginTop: 14, marginBottom: 18, position: 'relative' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <Settings size={16} color="#64FFDA" />
                        <strong style={{ color: '#E6F1FF' }}>Admin Tools</strong>
                    </div>
                    <button style={{ ...btnStyle, marginRight: 0 }} onClick={() => setShowTools((v) => !v)}>
                        Araçlar
                    </button>
                </div>
                {showTools && (
                    <div style={{ marginTop: 10, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                        <button
                            style={btnStyle}
                            onClick={() => {
                                const redirectUri = encodeURIComponent(window.location.origin + '/admin/users');
                                window.open(`${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/registrations?client_id=${KEYCLOAK_CLIENT_ID}&redirect_uri=${redirectUri}&response_type=code&scope=openid`, '_blank');
                            }}
                        >
                            <UserPlus size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} />
                            Yeni Kullanıcı Ekle
                        </button>
                        <button style={btnStyle} onClick={() => alert('Genel limit yönetimi bir sonraki fazda bu panelde açılacak.')}>
                            <Lock size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} />
                            Genel Limit Tanımla
                        </button>
                    </div>
                )}
            </div>

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
                            <th style={thStyle}>Yetki (Keycloak + DB)</th>
                            <th style={thStyle}>Giriş (Keycloak)</th>
                        </tr>
                        </thead>
                        <tbody>
                        {users.map((u) => (
                            <tr key={u.id} style={{ borderBottom: `1px solid ${tokens.border}` }}>
                                <td style={tdStyle}>{u.id}</td>
                                <td style={tdStyle}>{u.username ?? '—'}</td>
                                <td style={tdStyle}>{u.email ?? '—'}</td>
                                <td style={tdStyle}>
                                    {u.role === 'ADMIN' && adminUserCount <= 1 ? (
                                        <span style={{ color: '#8892B0', fontSize: '0.82rem' }} title="Sistemde tek yönetici varken rol düşürülemez; ADMIN atanamaz">
                                            ADMIN <span style={{ opacity: 0.85 }}>(sabit)</span>
                                        </span>
                                    ) : u.role === 'ADMIN' && adminUserCount > 1 ? (
                                        <select
                                            aria-label="Yönetici rolünü düşür"
                                            defaultValue=""
                                            disabled={actionLoading === `r:${u.id}`}
                                            onChange={(e) => {
                                                const next = e.target.value;
                                                if (!next) return;
                                                handleAssignRealmRole(u.id, next);
                                                e.target.value = '';
                                            }}
                                            style={{
                                                padding: '6px 8px',
                                                borderRadius: 8,
                                                border: '1px solid rgba(136,146,176,0.45)',
                                                background: 'rgba(10,25,47,0.85)',
                                                color: '#E6F1FF',
                                                fontSize: '0.82rem',
                                                minWidth: 180,
                                            }}
                                        >
                                            <option value="">ADMIN → düşürmek için seç…</option>
                                            <option value="USER">USER</option>
                                            <option value="FINANCE_MANAGER">FINANCE_MANAGER</option>
                                        </select>
                                    ) : (
                                        <select
                                            aria-label="Yetki değiştir"
                                            value={u.role}
                                            disabled={actionLoading === `r:${u.id}`}
                                            onChange={(e) => {
                                                const next = e.target.value;
                                                if (next === u.role) return;
                                                handleAssignRealmRole(u.id, next);
                                            }}
                                            style={{
                                                padding: '6px 8px',
                                                borderRadius: 8,
                                                border: '1px solid rgba(136,146,176,0.45)',
                                                background: 'rgba(10,25,47,0.85)',
                                                color: '#E6F1FF',
                                                fontSize: '0.82rem',
                                                minWidth: 160,
                                            }}
                                        >
                                            <option value="USER">USER</option>
                                            <option value="FINANCE_MANAGER">FINANCE_MANAGER</option>
                                        </select>
                                    )}
                                </td>
                                <td style={tdStyle}>
                                    {u.loginSuspended ? (
                                        <button
                                            style={{ ...btnStyle, marginRight: 0 }}
                                            disabled={actionLoading === `u:${u.id}`}
                                            title="Keycloak girişini yeniden aç"
                                            onClick={() => handleUnsuspendLogin(u.id)}
                                        >
                                            {actionLoading === `u:${u.id}` ? '...' : <><RotateCcw size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} /> Askıyı Kaldır</>}
                                        </button>
                                    ) : (
                                        <button
                                            style={{ ...btnStyle, marginRight: 0 }}
                                            disabled={actionLoading === `u:${u.id}`}
                                            title="Girişi askıya al (Keycloak + oturumlar)"
                                            onClick={() => setSuspendLoginModal(u.id)}
                                        >
                                            {actionLoading === `u:${u.id}` ? '...' : <><Ban size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} /> Askıya Al</>}
                                        </button>
                                    )}
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </div>

            {/* Bölüm 2: Hesaplar (freeze/unfreeze) */}
            <h2 style={sectionTitleStyle}>Hesaplar (hesap bazlı işlem durdurma)</h2>
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
                <div style={{ overflowX: 'auto', border: '1px solid rgba(136,146,176,0.35)', borderRadius: 8, background: 'rgba(17,34,64,0.92)' }}>
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
                                            <button style={btnStyle} disabled={actionLoading === `a:${row.id}`} onClick={() => handleUnfreeze(row.id)} title="Hesabı Aç">
                                                {actionLoading === `a:${row.id}` ? '...' : <><Unlock size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} /> Aç</>}
                                            </button>
                                        ) : (
                                            <button style={btnStyle} disabled={actionLoading === `a:${row.id}`} onClick={() => setFreezeModal(row.id)} title="Hesabı Dondur">
                                                {actionLoading === `a:${row.id}` ? '...' : <><Snowflake size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} /> Dondur</>}
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
                        <h3 style={{ marginBottom: 16 }}>Hesap dondur — sebep (isteğe bağlı)</h3>
                        <p style={{ fontSize: '0.82rem', color: tokens.textMuted, marginBottom: 12 }}>Yalnızca bu hesap donar; nakit işlemleri (al/sat vb.) için engel oluşturur. Giriş (Keycloak) açık kalır.</p>
                        <input
                            type="text"
                            value={freezeReason}
                            onChange={(e) => setFreezeReason(e.target.value)}
                            placeholder="Sebep..."
                            style={{ width: '100%', padding: 8, marginBottom: 16, borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.inputBg ?? tokens.bgCard, color: tokens.text }}
                        />
                        <div>
                            <button style={btnStyle} onClick={() => handleFreeze(freezeModal, freezeReason || undefined)}>Dondur</button>
                            <button style={btnStyle} onClick={() => { setFreezeModal(null); setFreezeReason(''); }}>İptal</button>
                        </div>
                    </div>
                </div>
            )}

            {suspendLoginModal != null && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
                    <div style={{ background: tokens.bgCard, padding: 24, borderRadius: 12, border: `1px solid ${tokens.border}`, minWidth: 320 }}>
                        <h3 style={{ marginBottom: 16 }}>Giriş askısı (Keycloak)</h3>
                        <p style={{ fontSize: '0.82rem', color: tokens.textMuted, marginBottom: 12 }}>
                            Kullanıcı artık giriş yapamaz; mevcut oturumlarda finance API bloklanır. Keycloak için app.keycloak.admin client kimlik bilgileri gereklidir.
                        </p>
                        <input
                            type="text"
                            value={suspendLoginReason}
                            onChange={(e) => setSuspendLoginReason(e.target.value)}
                            placeholder="Sebep (isteğe bağlı)..."
                            style={{ width: '100%', padding: 8, marginBottom: 16, borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.inputBg ?? tokens.bgCard, color: tokens.text }}
                        />
                        <div>
                            <button style={btnStyle} onClick={() => handleSuspendLogin(suspendLoginModal, suspendLoginReason || undefined)}>Askıya Al</button>
                            <button style={btnStyle} onClick={() => { setSuspendLoginModal(null); setSuspendLoginReason(''); }}>İptal</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}