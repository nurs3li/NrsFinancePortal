import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import { useAuth } from '../auth/AuthContext';
import { useRefetchOnFocus } from '../hooks/useRefetchOnFocus';
import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts';

type WhaleTimelineItem = {
    id: number;
    userId: number;
    whaleLevel: string;
    impactScore: number | null;
    reason: string | null;
    dailyVolume: number | null;
    hourlyTransactionCount: number | null;
    maxSingleTransaction: number | null;
    pattern: string | null;
    behavior: string | null;
    risk: string | null;
    triggeredAt: string;
    createdAt: string;
};

type RiskMonitorUser = {
    userId: number;
    username: string;
    email: string;
    portfolioTotalTry: number;
    whaleLevel: string | null;
    risk: string | null;
    reason: string | null;
    lastTriggeredAt: string | null;
    eventCount: number;
};
type BasicUserOption = { id: number; username?: string; email?: string; role?: string };

type PortfolioPerformance = {
    totalCost: number;
    totalCurrentValue: number;
    totalPnl: number;
    totalPnlPct: number;
};

type PortfolioSlice = {
    assetType: string;
    valueTry: number;
    ratioPct: number;
};

type WhaleLatest = {
    whaleLevel: string | null;
    behavior: string | null;
    pattern: string | null;
    risk: string | null;
    impactScore: number | null;
    triggeredAt: string | null;
};

type TransactionRiskView = {
    id: number;
    type: string;
    amount: number;
    balanceAfter: number;
    createdAt: string;
    riskScore: number;
    riskReason: string | null;
};

type RiskMonitorUserDetail = {
    userId: number;
    username: string;
    email: string;
    portfolio: PortfolioPerformance | null;
    portfolioSlices: PortfolioSlice[];
    latestWhale: WhaleLatest | null;
    recentWhaleEvents: WhaleTimelineItem[];
    recentTransactions: TransactionRiskView[];
};

const DONUT_COLORS = ['#22c55e', '#f59e0b', '#818cf8', '#06b6d4', '#ec4899', '#94a3b8'];
const unwrapApiPayload = <T,>(payload: unknown): T => {
    if (payload && typeof payload === 'object' && 'data' in (payload as Record<string, unknown>)) {
        return (payload as { data: T }).data;
    }
    return payload as T;
};

export function FmRisk() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const { role } = useAuth();
    const [users, setUsers] = useState<RiskMonitorUser[]>([]);
    const [query, setQuery] = useState('');
    const [sortMode, setSortMode] = useState<'RISK' | 'PORTFOLIO'>('RISK');
    const [expandedUserId, setExpandedUserId] = useState<number | null>(null);
    const [detailByUser, setDetailByUser] = useState<Record<number, RiskMonitorUserDetail>>({});
    const [loadingUsers, setLoadingUsers] = useState(false);
    const [loadingDetail, setLoadingDetail] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const canViewAllUsers = role === 'ADMIN' || role === 'FINANCE_MANAGER';

    const loadUsers = useCallback(() => {
        if (!canViewAllUsers) {
            setUsers([]);
            return;
        }
        setLoadingUsers(true);
        setError(null);
        financeClient
            .get<RiskMonitorUser[]>('/api/whales/monitor/users')
            .then((res) => {
                const raw = unwrapApiPayload<RiskMonitorUser[] | unknown>(res.data);
                const list = Array.isArray(raw) ? raw : [];
                if (list.length > 0) {
                    const sorted = list.sort((a, b) => Number(a.userId ?? 0) - Number(b.userId ?? 0));
                    setUsers(sorted);
                    return;
                }
                // Fallback: monitor endpoint bos donerse USER listesini dogrudan users API'den al.
                return financeClient.get('/api/users').then((uRes) => {
                    const payload = (uRes.data?.data ?? uRes.data) as BasicUserOption[] | undefined;
                    const usersRaw = Array.isArray(payload) ? payload : [];
                    const mapped = usersRaw
                        .filter((u) => String(u.role ?? '').toUpperCase() === 'USER')
                        .map((u) => ({
                            userId: Number(u.id),
                            username: u.username ?? '',
                            email: u.email ?? '',
                            portfolioTotalTry: 0,
                            whaleLevel: null,
                            risk: null,
                            reason: null,
                            lastTriggeredAt: null,
                            eventCount: 0,
                        } satisfies RiskMonitorUser))
                        .sort((a, b) => Number(a.userId) - Number(b.userId));
                    setUsers(mapped);
                });
            })
            .catch((err) => {
                setUsers([]);
                setError(err.response?.data?.message ?? err.message ?? t('fm.timelineLoadFailed', 'Timeline yüklenemedi'));
            })
            .finally(() => setLoadingUsers(false));
    }, [canViewAllUsers, t]);

    const loadDetail = useCallback((userId: number) => {
        setLoadingDetail(true);
        setError(null);
        financeClient
            .get<RiskMonitorUserDetail>(`/api/whales/monitor/users/${userId}/detail`)
            .then((res) => {
                const detail = unwrapApiPayload<RiskMonitorUserDetail | unknown>(res.data);
                if (!detail || typeof detail !== 'object') {
                    throw new Error('Invalid risk detail payload');
                }
                setDetailByUser((prev) => ({ ...prev, [userId]: detail as RiskMonitorUserDetail }));
            })
            .catch((err) => {
                setError(err.response?.data?.message ?? err.message ?? t('fm.timelineLoadFailed', 'Timeline yüklenemedi'));
            })
            .finally(() => setLoadingDetail(false));
    }, [t]);

    const refetchTimeline = useCallback(() => {
        if (expandedUserId != null) {
            loadDetail(expandedUserId);
        }
    }, [expandedUserId, loadDetail]);

    useEffect(() => {
        loadUsers();
    }, [loadUsers]);
    useRefetchOnFocus(refetchTimeline);

    useEffect(() => {
        if (expandedUserId == null) return;
        if (detailByUser[expandedUserId]) return;
        loadDetail(expandedUserId);
    }, [expandedUserId, detailByUser, loadDetail]);

    const visibleUsers = useMemo(() => {
        const riskRank = (risk: string | null | undefined) => {
            if (risk === 'CRITICAL') return 4;
            if (risk === 'HIGH') return 3;
            if (risk === 'MEDIUM') return 2;
            if (risk === 'LOW') return 1;
            return 0;
        };
        const q = query.trim().toLowerCase();
        const filtered = users.filter((u) => {
            const matchesText = !q || `${u.username ?? ''} ${u.email ?? ''}`.toLowerCase().includes(q);
            return matchesText;
        });
        return filtered.sort((a, b) => {
            if (sortMode === 'RISK') {
                const byRisk = riskRank(b.risk) - riskRank(a.risk);
                if (byRisk !== 0) return byRisk;
                return Number(b.portfolioTotalTry ?? 0) - Number(a.portfolioTotalTry ?? 0);
            }
            return Number(b.portfolioTotalTry ?? 0) - Number(a.portfolioTotalTry ?? 0);
        });
    }, [query, sortMode, users]);

    useEffect(() => {
        if (expandedUserId == null) return;
        const exists = visibleUsers.some((u) => u.userId === expandedUserId);
        if (!exists) {
            setExpandedUserId(visibleUsers[0]?.userId ?? null);
        }
    }, [expandedUserId, visibleUsers]);

    const pageStyle: React.CSSProperties = { padding: 24, background: tokens.bg, color: tokens.text, minHeight: '100%' };
    const titleStyle: React.CSSProperties = { fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };
    const cardStyle: React.CSSProperties = {
        padding: 16,
        borderRadius: 12,
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
    };
    const currentDetail = expandedUserId != null ? detailByUser[expandedUserId] : undefined;
    const currentTimeline = currentDetail?.recentWhaleEvents ?? [];
    const currentTransactions = currentDetail?.recentTransactions ?? [];
    const currentSlices = currentDetail?.portfolioSlices ?? [];
    const perf = currentDetail?.portfolio;
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const assetLabel = (type: string) => {
        const tpe = String(type || '').toUpperCase();
        if (tpe === 'FX') return lang === 'en' ? 'FX' : 'Doviz';
        if (tpe === 'CRYPTO') return lang === 'en' ? 'Crypto' : 'Kripto';
        if (tpe === 'FUND') return lang === 'en' ? 'Fund' : 'Fon';
        if (tpe === 'STOCK') return lang === 'en' ? 'Stock' : 'Hisse';
        if (tpe === 'METAL') return lang === 'en' ? 'Metal' : 'Metal';
        return type;
    };

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>{t('nav.riskMonitor', 'Risk Monitör')}</h1>
            <p style={mutedStyle}>
                {t('fm.riskSubtitle', 'Whale timeline ve risk görünümü. Sadece USER hesapları listelenir.')}
            </p>
            <div style={{ ...cardStyle, marginBottom: 16, display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center' }}>
                <input
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder={t('admin.searchUser', 'Kullanıcı ara')}
                    style={{
                        flex: '1 1 300px',
                        minWidth: 220,
                        padding: '10px 12px',
                        borderRadius: 10,
                        border: `1px solid ${tokens.border}`,
                        background: tokens.inputBg,
                        color: tokens.text,
                    }}
                />
                <span style={mutedStyle}>
                    {t('notifications.total', 'Toplam')} {visibleUsers.length} {t('admin.user', 'kullanıcı')}
                </span>
                <select
                    value={sortMode}
                    onChange={(e) => setSortMode(e.target.value === 'PORTFOLIO' ? 'PORTFOLIO' : 'RISK')}
                    style={{
                        padding: '8px 10px',
                        borderRadius: 8,
                        border: `1px solid ${tokens.border}`,
                        background: tokens.bgCard,
                        color: tokens.text,
                    }}
                >
                    <option value="RISK">{t('fm.sortByRisk', 'Sırala: Risk önceliği')}</option>
                    <option value="PORTFOLIO">{t('fm.sortByPortfolio', 'Sırala: Portföy değeri')}</option>
                </select>
            </div>
            {error && <p style={{ ...mutedStyle, color: tokens.error, marginBottom: 8 }}>{t('news.errorPrefix', 'Hata')}: {error}</p>}
            {!canViewAllUsers ? (
                <div style={cardStyle}>
                    <p style={mutedStyle}>{t('common.noPermission', 'Bu sayfayı görüntüleme yetkiniz yok.')}</p>
                </div>
            ) : loadingUsers ? (
                <div style={cardStyle}>
                    <p style={mutedStyle}>{t('common.loading', 'Yükleniyor...')}</p>
                </div>
            ) : visibleUsers.length === 0 ? (
                <div style={cardStyle}>
                    <p style={mutedStyle}>{t('fm.noWhaleData', 'Risk izlenecek kullanıcı bulunamadı.')}</p>
                </div>
            ) : (
                <div style={{ display: 'grid', gap: 12 }}>
                    {visibleUsers.map((u) => {
                        const expanded = expandedUserId === u.userId;
                        return (
                            <div key={u.userId} style={cardStyle}>
                                <button
                                    type="button"
                                    onClick={() => setExpandedUserId(expanded ? null : u.userId)}
                                    style={{
                                        width: '100%',
                                        textAlign: 'left',
                                        border: `1px solid ${tokens.border}`,
                                        background: tokens.inputBg,
                                        color: tokens.text,
                                        borderRadius: 10,
                                        padding: 12,
                                        cursor: 'pointer',
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'center',
                                        gap: 10,
                                    }}
                                >
                                    <div style={{ minWidth: 0 }}>
                                        <div style={{ fontWeight: 700, fontSize: '0.98rem' }}>{u.username || u.email || `#${u.userId}`}</div>
                                        <div style={mutedStyle}>{u.email}</div>
                                    </div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                                        <span style={{ ...mutedStyle, fontWeight: 600 }}>
                                            {t('portfolio.title', 'Portföy')}: ₺{Number(u.portfolioTotalTry ?? 0).toLocaleString(locale, { maximumFractionDigits: 2 })}
                                        </span>
                                        <span
                                            style={{
                                                fontSize: '0.72rem',
                                                fontWeight: 700,
                                                padding: '3px 10px',
                                                borderRadius: 999,
                                                background: u.risk === 'CRITICAL' ? tokens.error
                                                    : u.risk === 'HIGH' ? '#f59e0b'
                                                        : u.risk === 'MEDIUM' ? '#facc15'
                                                            : tokens.accent,
                                                color: '#fff',
                                            }}
                                        >
                                            {u.risk || t('common.none', 'Yok')}
                                        </span>
                                        <span style={mutedStyle}>{t('fm.hourlyTransactions', 'Kayıt')}: {u.eventCount ?? 0}</span>
                                        <span style={{ ...mutedStyle, fontSize: '1rem' }}>{expanded ? '▴' : '▾'}</span>
                                    </div>
                                </button>

                                {expanded && (
                                    <div style={{ marginTop: 12 }}>
                                        <div
                                            style={{
                                                display: 'grid',
                                                gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
                                                gap: 8,
                                                marginBottom: 10,
                                            }}
                                        >
                                            <div style={{ ...cardStyle, padding: 8 }}>
                                                <div style={mutedStyle}>{t('portfolio.totalCost', 'Toplam Maliyet')}</div>
                                                <div style={{ fontWeight: 700, marginTop: 2, fontSize: '0.95rem' }}>
                                                    ₺{Number(perf?.totalCost ?? 0).toLocaleString(locale, { maximumFractionDigits: 2 })}
                                                </div>
                                            </div>
                                            <div style={{ ...cardStyle, padding: 8 }}>
                                                <div style={mutedStyle}>{t('portfolio.currentValue', 'Guncel Deger')}</div>
                                                <div style={{ fontWeight: 700, marginTop: 2, fontSize: '0.95rem' }}>
                                                    ₺{Number(perf?.totalCurrentValue ?? 0).toLocaleString(locale, { maximumFractionDigits: 2 })}
                                                </div>
                                            </div>
                                            <div style={{ ...cardStyle, padding: 8 }}>
                                                <div style={mutedStyle}>{t('portfolio.totalPnl', 'Toplam Kar')}</div>
                                                <div
                                                    style={{
                                                        fontWeight: 700,
                                                        marginTop: 2,
                                                        fontSize: '0.95rem',
                                                        color: Number(perf?.totalPnl ?? 0) >= 0 ? '#22c55e' : tokens.error,
                                                    }}
                                                >
                                                    ₺{Number(perf?.totalPnl ?? 0).toLocaleString(locale, { maximumFractionDigits: 2 })}
                                                </div>
                                            </div>
                                            <div style={{ ...cardStyle, padding: 8 }}>
                                                <div style={mutedStyle}>{t('portfolio.totalPnlPct', 'Toplam PNL %')}</div>
                                                <div
                                                    style={{
                                                        fontWeight: 700,
                                                        marginTop: 2,
                                                        fontSize: '0.95rem',
                                                        color: Number(perf?.totalPnlPct ?? 0) >= 0 ? '#22c55e' : tokens.error,
                                                    }}
                                                >
                                                    %{Number(perf?.totalPnlPct ?? 0).toLocaleString(locale, { maximumFractionDigits: 2 })}
                                                </div>
                                            </div>
                                        </div>

                                        <div style={{ ...cardStyle, marginBottom: 12 }}>
                                            <div style={{ fontWeight: 700, marginBottom: 10 }}>
                                                {t('portfolio.combined', 'Birlesik')} - {t('portfolio.distribution', 'Dagilim')}
                                            </div>
                                            {currentSlices.length === 0 ? (
                                                <p style={mutedStyle}>{t('portfolio.noData', 'Portfoy verisi yok')}</p>
                                            ) : (
                                                <div
                                                    style={{
                                                        display: 'grid',
                                                        gridTemplateColumns: 'minmax(180px, 260px) 1fr',
                                                        gap: 12,
                                                        alignItems: 'center',
                                                    }}
                                                >
                                                    <div style={{ width: '100%', height: 170 }}>
                                                        <ResponsiveContainer>
                                                            <PieChart>
                                                                <Pie
                                                                    data={currentSlices}
                                                                    dataKey="valueTry"
                                                                    nameKey="assetType"
                                                                    innerRadius={42}
                                                                    outerRadius={66}
                                                                    stroke={tokens.bgCard}
                                                                    strokeWidth={2}
                                                                >
                                                                    {currentSlices.map((entry, index) => (
                                                                        <Cell key={`${entry.assetType}-${index}`} fill={DONUT_COLORS[index % DONUT_COLORS.length]} />
                                                                    ))}
                                                                </Pie>
                                                            </PieChart>
                                                        </ResponsiveContainer>
                                                    </div>
                                                    <div style={{ display: 'grid', gap: 5 }}>
                                                        {currentSlices.map((slice, i) => (
                                                            <div key={slice.assetType} style={{ display: 'flex', justifyContent: 'space-between', gap: 10 }}>
                                                                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                                                                    <span style={{ width: 10, height: 10, borderRadius: 999, background: DONUT_COLORS[i % DONUT_COLORS.length], display: 'inline-block' }} />
                                                                    {assetLabel(slice.assetType)}
                                                                </span>
                                                                <span style={{ ...mutedStyle, fontSize: '0.8rem' }}>
                                                                    %{Number(slice.ratioPct ?? 0).toLocaleString(locale, { maximumFractionDigits: 2 })} - ₺{Number(slice.valueTry ?? 0).toLocaleString(locale, { maximumFractionDigits: 2 })}
                                                                </span>
                                                            </div>
                                                        ))}
                                                    </div>
                                                </div>
                                            )}
                                        </div>

                                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, marginBottom: 10 }}>
                                            {currentDetail?.latestWhale?.triggeredAt && (
                                                <span style={mutedStyle}>
                                                    {t('fm.lastActivity', 'Son aktivite')}: {new Date(currentDetail.latestWhale.triggeredAt).toLocaleString(locale)}
                                                </span>
                                            )}
                                            {currentDetail?.latestWhale?.behavior && <span style={mutedStyle}>Behavior: {currentDetail.latestWhale.behavior}</span>}
                                            {currentDetail?.latestWhale?.pattern && <span style={mutedStyle}>Pattern: {currentDetail.latestWhale.pattern}</span>}
                                            {currentDetail?.latestWhale?.impactScore != null && <span style={mutedStyle}>Risk Score: {currentDetail.latestWhale.impactScore}/100</span>}
                                        </div>
                                        <div
                                            style={{
                                                marginTop: 12,
                                                display: 'grid',
                                                gap: 10,
                                                gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                                            }}
                                        >
                                            <div style={{ ...cardStyle, padding: 10 }}>
                                                <div style={{ fontWeight: 700, marginBottom: 8, fontSize: '0.92rem' }}>
                                                    {t('fm.last10Activities', 'Son 10 whale aktivitesi')}
                                                </div>
                                                {loadingDetail && currentTimeline.length === 0 ? (
                                                    <p style={mutedStyle}>{t('common.loading', 'Yükleniyor...')}</p>
                                                ) : currentTimeline.length === 0 ? (
                                                    <p style={mutedStyle}>
                                                        {t('fm.noWhaleData', 'Bu kullanıcı için whale kaydı bulunmuyor. Whale verisi oluştuğunda burada listelenecektir.')}
                                                    </p>
                                                ) : (
                                                    <ul style={{ listStyle: 'none', padding: 0, margin: 0, maxHeight: 220, overflowY: 'auto' }}>
                                                        {currentTimeline.map((item) => (
                                                            <li
                                                                key={item.id}
                                                                style={{
                                                                    borderTop: `1px solid ${tokens.border}`,
                                                                    padding: '8px 2px',
                                                                }}
                                                            >
                                                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center', marginBottom: 3 }}>
                                                                    <strong style={{ fontSize: '0.82rem' }}>{item.whaleLevel}</strong>
                                                                    <span style={{ ...mutedStyle, fontSize: '0.75rem' }}>{new Date(item.triggeredAt).toLocaleString(locale)}</span>
                                                                    {item.risk && <span style={{ ...mutedStyle, fontSize: '0.75rem' }}>{item.risk}</span>}
                                                                </div>
                                                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, fontSize: '0.75rem', color: tokens.textMuted }}>
                                                                    {item.dailyVolume != null && <span>{t('fm.dailyVolume', 'Günlük hacim')}: ₺{Number(item.dailyVolume).toLocaleString(locale)}</span>}
                                                                    {item.hourlyTransactionCount != null && <span>{t('fm.hourlyTransactions', 'Saatlik işlem')}: {item.hourlyTransactionCount}</span>}
                                                                    {item.maxSingleTransaction != null && <span>{t('fm.maxSingleTransaction', 'Maks. tek işlem')}: ₺{Number(item.maxSingleTransaction).toLocaleString(locale)}</span>}
                                                                    {item.impactScore != null && <span>Impact: {item.impactScore}/100</span>}
                                                                </div>
                                                            </li>
                                                        ))}
                                                    </ul>
                                                )}
                                            </div>
                                            <div style={{ ...cardStyle, padding: 10 }}>
                                                <div style={{ fontWeight: 700, marginBottom: 8, fontSize: '0.92rem' }}>
                                                    {t('fm.recentRiskTriggers', 'Riski tetikleyen son 10 hareket')}
                                                </div>
                                                <div style={{ overflowX: 'auto', maxHeight: 220 }}>
                                                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.78rem' }}>
                                                        <thead>
                                                            <tr>
                                                                <th style={{ textAlign: 'left', padding: 6, borderBottom: `1px solid ${tokens.border}` }}>ID</th>
                                                                <th style={{ textAlign: 'left', padding: 6, borderBottom: `1px solid ${tokens.border}` }}>{t('wallet.requestType', 'Tip')}</th>
                                                                <th style={{ textAlign: 'left', padding: 6, borderBottom: `1px solid ${tokens.border}` }}>{t('wallet.amount', 'Tutar')}</th>
                                                                <th style={{ textAlign: 'left', padding: 6, borderBottom: `1px solid ${tokens.border}` }}>{t('wallet.currentBalance', 'Bakiye')}</th>
                                                                <th style={{ textAlign: 'left', padding: 6, borderBottom: `1px solid ${tokens.border}` }}>{t('news.date', 'Tarih')}</th>
                                                                <th style={{ textAlign: 'left', padding: 6, borderBottom: `1px solid ${tokens.border}` }}>Risk</th>
                                                            </tr>
                                                        </thead>
                                                        <tbody>
                                                            {currentTransactions.length === 0 ? (
                                                                <tr>
                                                                    <td colSpan={6} style={{ padding: 6, color: tokens.textMuted }}>
                                                                        {t('transactions.empty', 'Islem bulunamadi')}
                                                                    </td>
                                                                </tr>
                                                            ) : (
                                                                currentTransactions.map((tx) => (
                                                                    <tr key={tx.id}>
                                                                        <td style={{ padding: 6, borderBottom: `1px solid ${tokens.border}` }}>#{tx.id}</td>
                                                                        <td style={{ padding: 6, borderBottom: `1px solid ${tokens.border}` }}>{tx.type}</td>
                                                                        <td style={{ padding: 6, borderBottom: `1px solid ${tokens.border}` }}>
                                                                            ₺{Number(tx.amount ?? 0).toLocaleString(locale, { maximumFractionDigits: 2 })}
                                                                        </td>
                                                                        <td style={{ padding: 6, borderBottom: `1px solid ${tokens.border}` }}>
                                                                            ₺{Number(tx.balanceAfter ?? 0).toLocaleString(locale, { maximumFractionDigits: 2 })}
                                                                        </td>
                                                                        <td style={{ padding: 6, borderBottom: `1px solid ${tokens.border}` }}>
                                                                            {new Date(tx.createdAt).toLocaleString(locale)}
                                                                        </td>
                                                                        <td style={{ padding: 6, borderBottom: `1px solid ${tokens.border}` }}>
                                                                            <span style={{ color: Number(tx.riskScore ?? 0) >= 70 ? tokens.error : Number(tx.riskScore ?? 0) > 0 ? '#f59e0b' : tokens.textMuted }}>
                                                                                {tx.riskScore ?? 0}
                                                                            </span>
                                                                            {tx.riskReason ? ` (${tx.riskReason})` : ''}
                                                                        </td>
                                                                    </tr>
                                                                ))
                                                            )}
                                                        </tbody>
                                                    </table>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}