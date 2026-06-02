import { useState, useEffect, useMemo } from 'react';
import { readApiError } from '../api/envelope';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import { useAuth } from '../auth/AuthContext';
import { Eye, Settings, UserPlus, X, Ban, UserCheck, Trash2 } from 'lucide-react';
import './AdminUsersAndAccounts.css';

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
            .catch((err) => setError(readApiError(err).message || t('admin.usersLoadFailed', 'Kullanıcılar yüklenemedi')))
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
                setError(readApiError(err).message || t('admin.roleAssignFailed', 'Rol ataması başarısız'));
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
                setError(readApiError(err).message || t('admin.suspendFailed', 'Askıya alma başarısız'));
                void reloadUsers().catch(() => undefined);
            })
            .finally(() => setActionLoading(null));
    };

    const handleDeleteUser = (userId: number, username: string) => {
        if (
            !window.confirm(
                t(
                    'admin.deleteUserConfirm',
                    'Bu kullanıcı Keycloak ve portal veritabanından kalıcı olarak silinecek. Emin misiniz?'
                ) + ` (${username})`
            )
        ) {
            return;
        }
        setActionLoading(`d:${userId}`);
        financeClient
            .delete(`/api/admin/users/${userId}`)
            .then(() => reloadUsers())
            .catch((err) => {
                setError(readApiError(err).message || t('admin.deleteUserFailed', 'Kullanıcı silinemedi'));
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
                setError(readApiError(err).message || t('admin.unsuspendFailed', 'Askı kaldırma başarısız'));
                void reloadUsers().catch(() => undefined);
            })
            .finally(() => setActionLoading(null));
    };

    const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8081';
    const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'nrs-finance';
    const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'nrs-frontend';

    const pageThemeStyle: React.CSSProperties = {
        background: tokens.bg,
        color: tokens.text,
        ['--admin-border' as string]: tokens.border,
    };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.84rem' };
    const cardStyle: React.CSSProperties = { padding: 14, borderRadius: 8, background: tokens.bgCard, border: `1px solid ${tokens.border}`, boxShadow: '0 8px 22px rgba(0,0,0,0.12)' };
    const thStyle: React.CSSProperties = { borderBottom: `1px solid ${tokens.border}`, color: tokens.textMuted, background: 'transparent' };
    const tdStyle: React.CSSProperties = { borderBottom: `1px solid ${tokens.tableBorder}` };
    const btnStyle: React.CSSProperties = { border: `1px solid ${tokens.border}`, background: tokens.bgCard, color: tokens.text };

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
                setError(readApiError(err).message || 'Detaylar yüklenemedi');
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

    const scrollHint = t('admin.tableScrollHint', 'Geniş tablo — yatay kaydırabilirsiniz');

    return (
        <div className="admin-page" style={pageThemeStyle}>
            <h1 className="admin-page__title">{t('nav.userManagement', 'Kullanıcı Yönetimi')}</h1>
            <p className="admin-page__subtitle" style={mutedStyle}>
                {t(
                    'admin.usersPageSubtitle',
                    'Keycloak realm rolleri ile uyumlu kullanıcı listesi. Rol ataması (USER) Keycloak admin client gerektirir.',
                )}
            </p>

            <div className="admin-page__card" style={{ ...cardStyle, marginTop: 14, marginBottom: 18, position: 'relative' }}>
                <div className="admin-page__tools-header">
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <Settings size={16} color={tokens.accent} />
                        <strong style={{ color: tokens.text }}>{t('admin.toolsTitle', 'Yönetici araçları')}</strong>
                    </div>
                    <button type="button" className="admin-page__btn admin-page__btn--last" style={btnStyle} onClick={() => setShowTools((v) => !v)}>
                        {t('admin.toolsToggle', 'Araçlar')}
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
                            {t('admin.addUser', 'Yeni kullanıcı ekle')}
                        </button>
                    </div>
                )}
            </div>

            {error && <p style={{ color: tokens.error, marginBottom: 16 }}>{t('news.errorPrefix', 'Hata')}: {error}</p>}

            <h2 className="admin-page__section-title">{t('admin.users', 'Kullanıcılar')}</h2>
            <div className="admin-page__card" style={cardStyle}>
                {loadingUsers ? (
                    <p style={mutedStyle}>{t('common.loading', 'Yükleniyor...')}</p>
                ) : users.length === 0 ? (
                    <p style={mutedStyle}>{t('admin.userNotFound', 'Kullanıcı bulunamadı.')}</p>
                ) : (
                    <div
                        className="admin-page__table-wrap"
                        data-scroll-hint={scrollHint}
                    >
                    <table className="admin-page__table">
                        <thead>
                        <tr>
                            <th style={thStyle}>{t('admin.colId', 'ID')}</th>
                            <th style={thStyle}>{t('admin.colUsername', 'Kullanıcı adı')}</th>
                            <th style={thStyle}>{t('admin.colEmail', 'E-posta')}</th>
                            <th style={thStyle}>{t('admin.colRole', 'Yetki (Keycloak + DB)')}</th>
                            <th style={thStyle}>{t('admin.userLoginColumn', 'Giriş (Keycloak)')}</th>
                            <th style={thStyle}>{t('admin.colInspection', 'İnceleme')}</th>
                            <th style={thStyle}>{t('admin.colActions', 'İşlemler')}</th>
                        </tr>
                        </thead>
                        <tbody>
                        {users.map((u) => (
                            <tr key={u.id} style={{ borderBottom: `1px solid ${tokens.border}` }}>
                                <td style={tdStyle} data-label={t('admin.colId', 'ID')}>{u.id}</td>
                                <td style={tdStyle} data-label={t('admin.colUsername', 'Kullanıcı adı')}>{u.username ?? '—'}</td>
                                <td style={tdStyle} data-label={t('admin.colEmail', 'E-posta')}>{u.email ?? '—'}</td>
                                <td style={tdStyle} data-label={t('admin.colRole', 'Yetki')}>
                                    {u.role === 'ADMIN' && adminUserCount <= 1 ? (
                                        <span style={{ color: tokens.textMuted, fontSize: '0.82rem' }} title={t('admin.singleAdminHint', 'Sistemde tek yönetici varken rol düşürülemez; ADMIN atanamaz')}>
                                            ADMIN <span style={{ opacity: 0.85 }}>({t('admin.adminFixed', 'sabit')})</span>
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
                                            className="admin-page__select"
                                            style={{
                                                border: `1px solid ${tokens.border}`,
                                                background: tokens.inputBg ?? tokens.bgCard,
                                                color: tokens.text,
                                            }}
                                        >
                                            <option value="">{t('admin.demoteSelectPlaceholder', 'ADMIN → düşürmek için seç…')}</option>
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
                                            className="admin-page__select"
                                            style={{
                                                border: `1px solid ${tokens.border}`,
                                                background: tokens.inputBg ?? tokens.bgCard,
                                                color: tokens.text,
                                            }}
                                        >
                                            <option value="USER">USER</option>
                                        </select>
                                    )}
                                </td>
                                <td style={tdStyle} data-label={t('admin.userLoginColumn', 'Giriş')}>
                                    {u.role === 'ADMIN' || u.id === currentUser?.id ? (
                                        <span style={{ ...mutedStyle, fontSize: '0.8rem' }}>—</span>
                                    ) : (
                                        <div className="admin-page__actions-row">
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
                                <td style={tdStyle} data-label={t('admin.colInspection', 'İnceleme')}>
                                    <button
                                        type="button"
                                        className="admin-page__btn admin-page__btn--last"
                                        style={btnStyle}
                                        onClick={() => openInspection(u.id)}
                                        title={t('admin.inspectTitleTooltip', 'Kullanıcı detaylı inceleme')}
                                    >
                                        <Eye size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} />
                                        {t('admin.inspectBtn', 'İncele')}
                                    </button>
                                </td>
                                <td style={tdStyle} data-label={t('admin.colActions', 'İşlemler')}>
                                    {u.role === 'ADMIN' || u.id === currentUser?.id ? (
                                        <span style={{ ...mutedStyle, fontSize: '0.8rem' }}>—</span>
                                    ) : (
                                        <button
                                            type="button"
                                            className="admin-page__btn admin-page__btn--last"
                                            style={{
                                                ...btnStyle,
                                                borderColor: tokens.error,
                                                color: tokens.error,
                                            }}
                                            disabled={actionLoading != null}
                                            onClick={() => handleDeleteUser(u.id, u.username ?? String(u.id))}
                                        >
                                            <Trash2 size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} />
                                            {t('admin.deleteUser', 'Kullanıcıyı sil')}
                                        </button>
                                    )}
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                    </div>
                )}
            </div>

            {inspectUserId != null && (
                <div className="admin-page__inspect-backdrop">
                    <div
                        className="admin-page__inspect-panel"
                        style={{
                            background: tokens.bgCard,
                            border: `1px solid ${tokens.border}`,
                            boxShadow: '0 18px 40px rgba(0,0,0,0.25)',
                        }}
                    >
                        <div className="admin-page__inspect-header">
                            <div>
                                <div style={{ fontWeight: 800, fontSize: '1.05rem' }}>{t('admin.inspectTitle', 'Kullanıcı detaylı inceleme')}</div>
                                <div style={mutedStyle}>
                                    {inspectData?.username ?? '...'} • {inspectData?.email ?? '...'} • {inspectData?.role ?? '...'}
                                </div>
                            </div>
                            <button type="button" className="admin-page__btn admin-page__btn--last" style={btnStyle} onClick={() => { setInspectUserId(null); setInspectData(null); }}>
                                <X size={14} />
                            </button>
                        </div>

                        {inspectLoading ? (
                            <p style={mutedStyle}>{t('common.loading', 'Yükleniyor...')}</p>
                        ) : inspectData?.role === 'USER' ? (
                            <div className="admin-page__inspect-grid">
                                <div className="admin-page__inspect-kpis">
                                    <div style={cardStyle}><div style={mutedStyle}>{t('admin.inspectTotalPortfolio', 'Toplam portföy değeri')}</div><div style={{ fontWeight: 800, marginTop: 4 }}>{formatMoney(inspectData.dashboardSummary?.portfolio?.totalValueTry)}</div></div>
                                    <div style={cardStyle}><div style={mutedStyle}>{t('admin.inspectDashboardSummary', 'Özet (dashboard)')}</div><div style={{ fontWeight: 800, marginTop: 4 }}>{formatMoney(inspectData.dashboardSummary?.totalPortfolioValueTry)}</div></div>
                                </div>

                                <div style={{ ...cardStyle, padding: 12 }}>
                                    <div style={{ fontWeight: 700, marginBottom: 8 }}>{t('admin.inspectPortfolioDist', 'Portföy — dağılım')}</div>
                                    <div className="admin-page__inspect-dist">
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
                                                <span style={mutedStyle}>{t('admin.inspectNoDistData', 'Dağılım verisi yok')}</span>
                                            )}
                                        </div>
                                    </div>
                                    <div style={{ marginTop: 10, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 8 }}>
                                        <div style={mutedStyle}>{t('admin.inspectTotalCost', 'Toplam maliyet')}: <strong style={{ color: tokens.text }}>{formatMoney(inspectData.dashboardSummary?.portfolio?.totalCostTry)}</strong></div>
                                        <div style={mutedStyle}>{t('admin.inspectCurrentValue', 'Güncel değer')}: <strong style={{ color: tokens.text }}>{formatMoney(inspectData.dashboardSummary?.portfolio?.totalValueTry)}</strong></div>
                                        <div style={mutedStyle}>{t('admin.inspectTotalProfit', 'Toplam kar')}: <strong style={{ color: (Number(inspectData.dashboardSummary?.portfolio?.totalPnlTry ?? 0) >= 0 ? tokens.success : tokens.error) }}>{formatMoney(inspectData.dashboardSummary?.portfolio?.totalPnlTry)}</strong></div>
                                        <div style={mutedStyle}>{t('admin.inspectTotalPnlPct', 'Toplam PNL%')}: <strong style={{ color: (Number(inspectData.dashboardSummary?.portfolio?.totalPnlPct ?? 0) >= 0 ? tokens.success : tokens.error) }}>%{Number(inspectData.dashboardSummary?.portfolio?.totalPnlPct ?? 0).toLocaleString(lang === 'en' ? 'en-US' : 'tr-TR', { maximumFractionDigits: 2 })}</strong></div>
                                    </div>
                                </div>
                            </div>
                        ) : (
                            <p style={mutedStyle}>{t('admin.inspectNoPortfolioForRole', 'Bu rol için portföy özeti yok (yalnızca USER).')}</p>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
