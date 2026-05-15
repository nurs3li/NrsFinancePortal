import { useState, useEffect, useMemo } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import { useAuth } from '../auth/AuthContext';
import { Eye, Settings, UserPlus, X, Ban, UserCheck } from 'lucide-react';

type UserRow = { id: number; username: string; email: string; role: string; loginSuspended?: boolean };

type DashboardCategory = {
    assetType: string;
    valueTry: number;
    costTry: number;
    pnlTry: number;
    pnlPct: number;
};

type AdminUserInspection = {
    userId: number;
    username: string;
    email: string;
    role: string;
    dashboardSummary?: {
        portfolio?: {
            totalValueTry?: number;
            totalCostTry?: number;
            totalPnlTry?: number;
            totalPnlPct?: number;
            categories?: DashboardCategory[];
        };
        totalPortfolioValueTry?: number;
    } | null;
};

export function AdminUsersAndAccounts() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const { user: currentUser } = useAuth();
    const [users, setUsers] = useState<UserRow[]>([]);
    const [loadingUsers, setLoadingUsers] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [actionLoading, setActionLoading] = useState<string | null>(null);
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
    }, [t]);

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

    const handleSuspendLogin = (userId: number) => {
        const reason = window.prompt(t('admin.suspendReasonPrompt', 'Gerekçe (boş bırakılabilir):'), '');
        if (reason === null) return;
        const body = reason.trim() ? { reason: reason.trim() } : {};
        setActionLoading(`s:${userId}`);
        financeClient
            .post(`/api/admin/users/${userId}/suspend-login`, body)
            .then(() => reloadUsers())
            .catch((err) => {
                const msg =
                    err.response?.data?.errors?.message ??
                    err.response?.data?.errors?.error ??
                    err.response?.data?.message ??
                    err.message ??
                    t('admin.suspendFailed', 'Askıya alma başarısız');
                setError(String(msg));
                void reloadUsers().catch(() => undefined);
            })
            .finally(() => setActionLoading(null));
    };

    const handleUnsuspendLogin = (userId: number) => {
        if (!window.confirm(t('admin.unsuspendConfirm', 'Bu kullanıcının giriş kısıtlamasını kaldırmak istiyor musunuz?'))) return;
        setActionLoading(`u:${userId}`);
        financeClient
            .post(`/api/admin/users/${userId}/unsuspend-login`, {})
            .then(() => reloadUsers())
            .catch((err) => {
                const msg =
                    err.response?.data?.errors?.message ??
                    err.response?.data?.errors?.error ??
                    err.response?.data?.message ??
                    err.message ??
                    t('admin.unsuspendFailed', 'Askı kaldırma başarısız');
                setError(String(msg));
                void reloadUsers().catch(() => undefined);
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

    const portfolioSlices = (d: AdminUserInspection | null) => {
        const cats = d?.dashboardSummary?.portfolio?.categories ?? [];
        const total = Number(d?.dashboardSummary?.portfolio?.totalValueTry ?? 0);
        return cats.map((c) => ({
            assetType: c.assetType,
            valueTry: c.valueTry,
            ratioPct: total > 0 ? (Number(c.valueTry) / total) * 100 : 0,
        }));
    };

    return (
        <div style={pageStyle}>
            <style>{`
                @keyframes adminInspectIn {
                    0% { opacity: 0; transform: translateY(8px) scale(0.98); }
                    100% { opacity: 1; transform: translateY(0) scale(1); }
                }
            `}</style>
            <h1 style={titleStyle}>{t('nav.userManagement', 'Kullanıcı Yönetimi')}</h1>
            <p style={mutedStyle}>
                Keycloak realm rolleri ile uyumlu kullanıcı listesi. Rol ataması (USER) Keycloak admin client gerektirir.
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
                    </div>
                )}
            </div>

            {error && <p style={{ color: tokens.error, marginBottom: 16 }}>{t('news.errorPrefix', 'Hata')}: {error}</p>}

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
                            <th style={thStyle}>{t('admin.userLoginColumn', 'Giriş (Keycloak)')}</th>
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
                                        </select>
                                    )}
                                </td>
                                <td style={tdStyle}>
                                    {u.role === 'ADMIN' || u.id === currentUser?.id ? (
                                        <span style={{ ...mutedStyle, fontSize: '0.8rem' }}>—</span>
                                    ) : (
                                        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8 }}>
                                            {u.loginSuspended && (
                                                <span
                                                    style={{
                                                        fontSize: '0.72rem',
                                                        fontWeight: 700,
                                                        padding: '3px 8px',
                                                        borderRadius: 999,
                                                        background: 'rgba(239,68,68,0.15)',
                                                        border: `1px solid ${tokens.error}`,
                                                        color: tokens.error,
                                                    }}
                                                >
                                                    {t('admin.loginSuspendedShort', 'Askıda')}
                                                </span>
                                            )}
                                            {!u.loginSuspended && (
                                                <button
                                                    type="button"
                                                    style={{
                                                        ...btnStyle,
                                                        marginRight: 0,
                                                        borderColor: tokens.error,
                                                        color: tokens.error,
                                                    }}
                                                    disabled={actionLoading === `s:${u.id}` || actionLoading === `u:${u.id}` || actionLoading === `r:${u.id}`}
                                                    onClick={() => handleSuspendLogin(u.id)}
                                                >
                                                    <Ban size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} />
                                                    {t('admin.suspendUser', 'Girişi askıya al')}
                                                </button>
                                            )}
                                            {u.loginSuspended && (
                                                <button
                                                    type="button"
                                                    style={{ ...btnStyle, marginRight: 0, borderColor: tokens.success, color: tokens.success }}
                                                    disabled={actionLoading === `s:${u.id}` || actionLoading === `u:${u.id}` || actionLoading === `r:${u.id}`}
                                                    onClick={() => handleUnsuspendLogin(u.id)}
                                                >
                                                    <UserCheck size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} />
                                                    {t('admin.unsuspendUser', 'Askıyı kaldır')}
                                                </button>
                                            )}
                                        </div>
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
                                    <div style={cardStyle}><div style={mutedStyle}>Özet (dashboard)</div><div style={{ fontWeight: 800, marginTop: 4 }}>{formatMoney(inspectData.dashboardSummary?.totalPortfolioValueTry)}</div></div>
                                </div>

                                <div style={{ ...cardStyle, padding: 12 }}>
                                    <div style={{ fontWeight: 700, marginBottom: 8 }}>Portföy — dağılım</div>
                                    <div style={{ display: 'grid', gridTemplateColumns: 'minmax(160px, 220px) 1fr', alignItems: 'center', gap: 12 }}>
                                        <div style={{ display: 'flex', justifyContent: 'center' }}>
                                            {(() => {
                                                const slices = portfolioSlices(inspectData);
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
                                            {portfolioSlices(inspectData).map((slice) => (
                                                <div key={slice.assetType} style={{ display: 'flex', justifyContent: 'space-between', ...mutedStyle }}>
                                                    <span>{slice.assetType}</span>
                                                    <span>%{Number(slice.ratioPct ?? 0).toLocaleString(lang === 'en' ? 'en-US' : 'tr-TR', { maximumFractionDigits: 2 })} — {formatMoney(slice.valueTry)}</span>
                                                </div>
                                            ))}
                                            {portfolioSlices(inspectData).length === 0 && (
                                                <span style={mutedStyle}>Dağılım verisi yok</span>
                                            )}
                                        </div>
                                    </div>
                                    <div style={{ marginTop: 10, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 8 }}>
                                        <div style={mutedStyle}>Toplam maliyet: <strong style={{ color: tokens.text }}>{formatMoney(inspectData.dashboardSummary?.portfolio?.totalCostTry)}</strong></div>
                                        <div style={mutedStyle}>Güncel değer: <strong style={{ color: tokens.text }}>{formatMoney(inspectData.dashboardSummary?.portfolio?.totalValueTry)}</strong></div>
                                        <div style={mutedStyle}>Toplam kar: <strong style={{ color: (Number(inspectData.dashboardSummary?.portfolio?.totalPnlTry ?? 0) >= 0 ? tokens.success : tokens.error) }}>{formatMoney(inspectData.dashboardSummary?.portfolio?.totalPnlTry)}</strong></div>
                                        <div style={mutedStyle}>Toplam PNL%: <strong style={{ color: (Number(inspectData.dashboardSummary?.portfolio?.totalPnlPct ?? 0) >= 0 ? tokens.success : tokens.error) }}>%{Number(inspectData.dashboardSummary?.portfolio?.totalPnlPct ?? 0).toLocaleString(lang === 'en' ? 'en-US' : 'tr-TR', { maximumFractionDigits: 2 })}</strong></div>
                                    </div>
                                </div>
                            </div>
                        ) : (
                            <p style={mutedStyle}>Bu rol için portföy özeti yok (yalnızca USER).</p>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
