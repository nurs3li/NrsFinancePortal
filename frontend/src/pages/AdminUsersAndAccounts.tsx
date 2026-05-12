import { useState, useEffect, useMemo } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import { Ban, Eye, Lock, RotateCcw, Settings, Snowflake, Unlock, UserPlus, X } from 'lucide-react';

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

type AdminUserInspection = {
    userId: number;
    username: string;
    email: string;
    role: string;
    dashboardSummary?: {
        cash?: { amountTry?: number };
        portfolio?: { totalValueTry?: number; totalCostTry?: number; totalPnlTry?: number; totalPnlPct?: number };
        netWorthTry?: number;
        whale?: { level?: string; impactScore?: number; triggeredAt?: string };
    } | null;
    riskMonitorDetail?: {
        portfolio?: { totalCost?: number; totalCurrentValue?: number; totalPnl?: number; totalPnlPct?: number } | null;
        portfolioSlices?: { assetType: string; valueTry: number; ratioPct: number }[];
    } | null;
    fmTaskSummary?: {
        poolOpenCount: number;
        myClaimedOpenCount: number;
        myCompletedCount: number;
    } | null;
};

export function AdminUsersAndAccounts() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
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
    const [inspectUserId, setInspectUserId] = useState<number | null>(null);
    const [inspectLoading, setInspectLoading] = useState(false);
    const [inspectData, setInspectData] = useState<AdminUserInspection | null>(null);

    const adminUserCount = useMemo(() => users.filter((u) => u.role === 'ADMIN').length, [users]);

    useEffect(() => {
        setLoadingUsers(true);
        financeClient
            .get('/api/users')
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                const list = Array.isArray(raw) ? raw : [];
                setUsers([...list].sort((a, b) => Number(a.id) - Number(b.id)));
            })
            .catch((err) => setError(err.response?.data?.message ?? err.message ?? t('admin.usersLoadFailed', 'Kullanıcılar yüklenemedi')))
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
                setTotalPages(pageData?.page?.totalPages ?? 0);
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? t('admin.accountsLoadFailed', 'Hesaplar yüklenemedi');
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
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? t('admin.freezeFailed', 'Freeze başarısız');
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
                const list = Array.isArray(raw) ? raw : [];
                setUsers([...list].sort((a, b) => Number(a.id) - Number(b.id)));
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.message ?? err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? t('admin.suspendFailed', 'Askıya alma başarısız');
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
                const list = Array.isArray(raw) ? raw : [];
                setUsers([...list].sort((a, b) => Number(a.id) - Number(b.id)));
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.message ?? err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? t('admin.unsuspendFailed', 'Askı kaldırma başarısız');
                setError(String(msg));
            })
            .finally(() => setActionLoading(null));
    };

    const reloadUsers = () =>
        financeClient.get('/api/users').then((res) => {
            const raw = res.data?.data ?? res.data;
            const list = Array.isArray(raw) ? raw : [];
            setUsers([...list].sort((a, b) => Number(a.id) - Number(b.id)));
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
                    t('admin.roleAssignFailed', 'Rol ataması başarısız');
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
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? t('admin.unfreezeFailed', 'Unfreeze başarısız');
                setError(msg);
            })
            .finally(() => setActionLoading(null));
    };

    const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8081';
    const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'nrs-finance';
    const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'nrs-frontend';

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%', fontFamily: 'Inter, Roboto, Arial, sans-serif' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4, color: tokens.text };
    const sectionTitleStyle: React.CSSProperties = { fontSize: '1.05rem', fontWeight: 600, marginTop: 20, marginBottom: 10, color: tokens.text };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.84rem' };
    const cardStyle: React.CSSProperties = { padding: 14, borderRadius: 8, background: tokens.bgCard, border: `1px solid ${tokens.border}`, boxShadow: '0 8px 22px rgba(0,0,0,0.12)' };
    const tableStyle: React.CSSProperties = { width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' };
    const thStyle: React.CSSProperties = { textAlign: 'left', padding: '8px 10px', borderBottom: `1px solid ${tokens.border}`, color: tokens.textMuted, background: 'transparent', fontSize: '0.8rem' };
    const tdStyle: React.CSSProperties = { padding: '8px 10px', borderBottom: `1px solid ${tokens.tableBorder}` };
    const btnStyle: React.CSSProperties = { padding: '6px 10px', marginRight: 8, borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text, cursor: 'pointer', fontSize: '0.82rem' };

    const formatDate = (s: string | null) => (s ? new Date(s).toLocaleString(lang === 'en' ? 'en-US' : 'tr-TR') : '–');
    const formatMoney = (v: number | undefined | null) => `₺${Number(v ?? 0).toLocaleString(lang === 'en' ? 'en-US' : 'tr-TR', { maximumFractionDigits: 2 })}`;

    const openInspection = (userId: number) => {
        setInspectUserId(userId);
        setInspectLoading(true);
        setInspectData(null);
        financeClient
            .get(`/api/admin/users/${userId}/inspection`)
            .then((res) => {
                const raw = res.data?.data ?? res.data;
                setInspectData(raw as AdminUserInspection);
            })
            .catch((err) => {
                const msg = err.response?.data?.errors?.error ?? err.response?.data?.message ?? err.message ?? 'Detaylar yüklenemedi';
                setError(msg);
            })
            .finally(() => setInspectLoading(false));
    };

    return (
        <div style={pageStyle}>
            <style>{`
                @keyframes adminInspectIn {
                    0% { opacity: 0; transform: translateY(8px) scale(0.98); }
                    100% { opacity: 1; transform: translateY(0) scale(1); }
                }
            `}</style>
            <h1 style={titleStyle}>{t('nav.userManagement', 'Kullanıcı & Hesap Yönetimi')}</h1>
            <p style={mutedStyle}>
                Hesap bazlı dondurma: yalnızca seçilen hesabın işlemleri (nakit işlemleri) kapatılır. Giriş askısı: kullanıcı Keycloak ile giriş yapamaz — giriş ekranında uyarı gösterilir.
            </p>

            <div style={{ ...cardStyle, marginTop: 14, marginBottom: 18, position: 'relative' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <Settings size={16} color={tokens.accent} />
                        <strong style={{ color: tokens.text }}>Admin Tools</strong>
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
                        <button style={btnStyle} onClick={() => alert(t('admin.limitComingSoon', 'Genel limit yönetimi bir sonraki fazda bu panelde açılacak.'))}>
                            <Lock size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} />
                            Genel Limit Tanımla
                        </button>
                    </div>
                )}
            </div>

            {error && <p style={{ color: tokens.error, marginBottom: 16 }}>{t('news.errorPrefix', 'Hata')}: {error}</p>}

            {/* Bölüm 1: Kullanıcılar */}
            <h2 style={sectionTitleStyle}>{t('admin.users', 'Kullanıcılar')}</h2>
            <div style={cardStyle}>
                {loadingUsers ? (
                    <p style={mutedStyle}>{t('common.loading', 'Yükleniyor...')}</p>
                ) : users.length === 0 ? (
                    <p style={mutedStyle}>{t('admin.userNotFound', 'Kullanıcı bulunamadı.')}</p>
                ) : (
                    <table style={tableStyle}>
                        <thead>
                        <tr>
                            <th style={thStyle}>ID</th>
                            <th style={thStyle}>Kullanıcı adı</th>
                            <th style={thStyle}>E-posta</th>
                            <th style={thStyle}>Yetki (Keycloak + DB)</th>
                            <th style={thStyle}>Giriş (Keycloak)</th>
                            <th style={thStyle}>İnceleme</th>
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
                                        <span style={{ color: tokens.textMuted, fontSize: '0.82rem' }} title={t('admin.singleAdminHint', 'Sistemde tek yönetici varken rol düşürülemez; ADMIN atanamaz')}>
                                            ADMIN <span style={{ opacity: 0.85 }}>(sabit)</span>
                                        </span>
                                    ) : u.role === 'ADMIN' && adminUserCount > 1 ? (
                                        <select
                                            aria-label={t('admin.demoteAdmin', 'Yönetici rolünü düşür')}
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
                                                border: `1px solid ${tokens.border}`,
                                                background: tokens.inputBg ?? tokens.bgCard,
                                                color: tokens.text,
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
                                            aria-label={t('admin.changeRole', 'Yetki değiştir')}
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
                                                border: `1px solid ${tokens.border}`,
                                                background: tokens.inputBg ?? tokens.bgCard,
                                                color: tokens.text,
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
                                            title={t('admin.reopenKeycloakLogin', 'Keycloak girişini yeniden aç')}
                                            onClick={() => handleUnsuspendLogin(u.id)}
                                        >
                                            {actionLoading === `u:${u.id}` ? '...' : <><RotateCcw size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} /> Askıyı Kaldır</>}
                                        </button>
                                    ) : (
                                        <button
                                            style={{ ...btnStyle, marginRight: 0 }}
                                            disabled={actionLoading === `u:${u.id}`}
                                            title={t('admin.suspendKeycloakLogin', 'Girişi askıya al (Keycloak + oturumlar)')}
                                            onClick={() => setSuspendLoginModal(u.id)}
                                        >
                                            {actionLoading === `u:${u.id}` ? '...' : <><Ban size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} /> Askıya Al</>}
                                        </button>
                                    )}
                                </td>
                                <td style={tdStyle}>
                                    <button
                                        style={{ ...btnStyle, marginRight: 0 }}
                                        onClick={() => openInspection(u.id)}
                                        title="Kullanıcı detaylı inceleme"
                                    >
                                        <Eye size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} />
                                        İncele
                                    </button>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </div>

            {/* Bölüm 2: Hesaplar (freeze/unfreeze) */}
            <h2 style={sectionTitleStyle}>{t('admin.accountsSection', 'Hesaplar (hesap bazlı işlem durdurma)')}</h2>
            <div style={{ marginBottom: 16 }}>
                <label style={{ marginRight: 8, color: tokens.textMuted }}>{t('wallet.status', 'Durum')}: </label>
                <select
                    style={{ padding: '6px 12px', borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text }}
                    value={status}
                    onChange={(e) => { setStatus(e.target.value); setPage(0); }}
                >
                    <option value="">{t('admin.all', 'Tümü')}</option>
                    <option value="ACTIVE">ACTIVE</option>
                    <option value="FROZEN">FROZEN</option>
                </select>
            </div>
            {loadingAccounts && <p style={mutedStyle}>{t('common.loading', 'Yükleniyor...')}</p>}
            {!loadingAccounts && (
                <div style={{ overflowX: 'auto', border: `1px solid ${tokens.border}`, borderRadius: 8, background: tokens.bgCard }}>
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
                                            <button style={btnStyle} disabled={actionLoading === `a:${row.id}`} onClick={() => handleUnfreeze(row.id)} title={t('admin.openAccount', 'Hesabı Aç')}>
                                                {actionLoading === `a:${row.id}` ? '...' : <><Unlock size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} /> Aç</>}
                                            </button>
                                        ) : (
                                            <button style={btnStyle} disabled={actionLoading === `a:${row.id}`} onClick={() => setFreezeModal(row.id)} title={t('admin.freezeAccount', 'Hesabı Dondur')}>
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
                    <button style={{ ...btnStyle, opacity: page === 0 ? 0.6 : 1 }} onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>{t('news.prev', 'Önceki')}</button>
                    <span style={{ color: tokens.textMuted }}>Sayfa {page + 1} / {totalPages}</span>
                    <button style={{ ...btnStyle, opacity: page >= totalPages - 1 ? 0.6 : 1 }} onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}>{t('news.next', 'Sonraki')}</button>
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
                            placeholder={t('admin.reasonOptionalPlaceholder', 'Sebep (isteğe bağlı)...')}
                            style={{ width: '100%', padding: 8, marginBottom: 16, borderRadius: 8, border: `1px solid ${tokens.border}`, background: tokens.inputBg ?? tokens.bgCard, color: tokens.text }}
                        />
                        <div>
                            <button style={btnStyle} onClick={() => handleSuspendLogin(suspendLoginModal, suspendLoginReason || undefined)}>Askıya Al</button>
                            <button style={btnStyle} onClick={() => { setSuspendLoginModal(null); setSuspendLoginReason(''); }}>İptal</button>
                        </div>
                    </div>
                </div>
            )}

            {inspectUserId != null && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.52)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1100 }}>
                    <div
                        style={{
                            width: 'min(1000px, 94vw)',
                            maxHeight: '88vh',
                            overflowY: 'auto',
                            background: tokens.bgCard,
                            borderRadius: 14,
                            border: `1px solid ${tokens.border}`,
                            boxShadow: '0 18px 40px rgba(0,0,0,0.25)',
                            padding: 18,
                            animation: 'adminInspectIn 220ms ease',
                        }}
                    >
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                            <div>
                                <div style={{ fontWeight: 800, fontSize: '1.05rem' }}>Kullanıcı detaylı inceleme</div>
                                <div style={mutedStyle}>
                                    {inspectData?.username ?? '...'} • {inspectData?.email ?? '...'} • {inspectData?.role ?? '...'}
                                </div>
                            </div>
                            <button style={{ ...btnStyle, marginRight: 0 }} onClick={() => { setInspectUserId(null); setInspectData(null); }}>
                                <X size={14} />
                            </button>
                        </div>

                        {inspectLoading ? (
                            <p style={mutedStyle}>{t('common.loading', 'Yükleniyor...')}</p>
                        ) : inspectData?.role === 'USER' ? (
                            <div style={{ display: 'grid', gap: 12 }}>
                                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 10 }}>
                                    <div style={cardStyle}><div style={mutedStyle}>Toplam portföy değeri</div><div style={{ fontWeight: 800, marginTop: 4 }}>{formatMoney(inspectData.dashboardSummary?.portfolio?.totalValueTry)}</div></div>
                                    <div style={cardStyle}><div style={mutedStyle}>Nakit (TRY)</div><div style={{ fontWeight: 800, marginTop: 4 }}>{formatMoney(inspectData.dashboardSummary?.cash?.amountTry)}</div></div>
                                    <div style={cardStyle}><div style={mutedStyle}>Toplam bakiye</div><div style={{ fontWeight: 800, marginTop: 4 }}>{formatMoney(inspectData.dashboardSummary?.netWorthTry)}</div></div>
                                    <div style={cardStyle}><div style={mutedStyle}>Balina seviyesi</div><div style={{ fontWeight: 800, marginTop: 4 }}>{inspectData.dashboardSummary?.whale?.level ?? '-'}</div></div>
                                </div>

                                <div style={{ ...cardStyle, padding: 12 }}>
                                    <div style={{ fontWeight: 700, marginBottom: 8 }}>Birleşik - Dağılım</div>
                                    <div style={{ display: 'grid', gridTemplateColumns: 'minmax(160px, 220px) 1fr', alignItems: 'center', gap: 12 }}>
                                        <div style={{ display: 'flex', justifyContent: 'center' }}>
                                            {(() => {
                                                const slices = inspectData.riskMonitorDetail?.portfolioSlices ?? [];
                                                const total = slices.reduce((s, x) => s + Number(x.ratioPct ?? 0), 0);
                                                const bg = slices.length
                                                    ? `conic-gradient(${slices.map((s, i) => {
                                                        const start = slices.slice(0, i).reduce((a, b) => a + Number(b.ratioPct ?? 0), 0);
                                                        const end = start + Number(s.ratioPct ?? 0);
                                                        const colors = ['#22c55e', '#f59e0b', '#818cf8', '#06b6d4', '#ec4899', '#94a3b8'];
                                                        return `${colors[i % colors.length]} ${(start / Math.max(total, 100)) * 360}deg ${(end / Math.max(total, 100)) * 360}deg`;
                                                    }).join(', ')})`
                                                    : tokens.border;
                                                return (
                                                    <div style={{ width: 120, height: 120, borderRadius: '50%', background: bg, position: 'relative' }}>
                                                        <div style={{ position: 'absolute', inset: 26, borderRadius: '50%', background: tokens.bgCard }} />
                                                    </div>
                                                );
                                            })()}
                                        </div>
                                        <div style={{ display: 'grid', gap: 6 }}>
                                            {(inspectData.riskMonitorDetail?.portfolioSlices ?? []).map((slice) => (
                                                <div key={slice.assetType} style={{ display: 'flex', justifyContent: 'space-between', ...mutedStyle }}>
                                                    <span>{slice.assetType}</span>
                                                    <span>%{Number(slice.ratioPct ?? 0).toLocaleString(lang === 'en' ? 'en-US' : 'tr-TR', { maximumFractionDigits: 2 })} - {formatMoney(slice.valueTry)}</span>
                                                </div>
                                            ))}
                                            {(inspectData.riskMonitorDetail?.portfolioSlices ?? []).length === 0 && (
                                                <span style={mutedStyle}>Dağılım verisi yok</span>
                                            )}
                                        </div>
                                    </div>
                                    <div style={{ marginTop: 10, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 8 }}>
                                        <div style={mutedStyle}>Toplam maliyet: <strong style={{ color: tokens.text }}>{formatMoney(inspectData.riskMonitorDetail?.portfolio?.totalCost)}</strong></div>
                                        <div style={mutedStyle}>Güncel değer: <strong style={{ color: tokens.text }}>{formatMoney(inspectData.riskMonitorDetail?.portfolio?.totalCurrentValue)}</strong></div>
                                        <div style={mutedStyle}>Toplam kar: <strong style={{ color: (Number(inspectData.riskMonitorDetail?.portfolio?.totalPnl ?? 0) >= 0 ? tokens.success : tokens.error) }}>{formatMoney(inspectData.riskMonitorDetail?.portfolio?.totalPnl)}</strong></div>
                                        <div style={mutedStyle}>Toplam PNL%: <strong style={{ color: (Number(inspectData.riskMonitorDetail?.portfolio?.totalPnlPct ?? 0) >= 0 ? tokens.success : tokens.error) }}>%{Number(inspectData.riskMonitorDetail?.portfolio?.totalPnlPct ?? 0).toLocaleString(lang === 'en' ? 'en-US' : 'tr-TR', { maximumFractionDigits: 2 })}</strong></div>
                                    </div>
                                </div>
                            </div>
                        ) : inspectData?.role === 'FINANCE_MANAGER' ? (
                            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
                                <div style={{ ...cardStyle, borderRadius: 24, padding: 18 }}>
                                    <div style={{ color: tokens.textMuted, fontSize: '2rem', lineHeight: 1.2 }}>Üzerimdeki işler</div>
                                    <div style={{ fontWeight: 800, color: tokens.accent, fontSize: '2.5rem' }}>{inspectData.fmTaskSummary?.myClaimedOpenCount ?? 0}</div>
                                </div>
                                <div style={{ ...cardStyle, borderRadius: 24, padding: 18 }}>
                                    <div style={{ color: tokens.textMuted, fontSize: '2rem', lineHeight: 1.2 }}>Tamamladıklarım</div>
                                    <div style={{ fontWeight: 800, fontSize: '2.5rem' }}>{inspectData.fmTaskSummary?.myCompletedCount ?? 0}</div>
                                </div>
                                <div style={{ ...cardStyle, borderRadius: 24, padding: 18 }}>
                                    <div style={{ color: tokens.textMuted, fontSize: '1.3rem' }}>Havuzdaki açık görev</div>
                                    <div style={{ fontWeight: 800, fontSize: '2rem' }}>{inspectData.fmTaskSummary?.poolOpenCount ?? 0}</div>
                                </div>
                            </div>
                        ) : (
                            <p style={mutedStyle}>Bu rol için detay kartı bulunmuyor.</p>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}