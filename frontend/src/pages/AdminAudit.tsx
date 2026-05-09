import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { financeClient } from '../api/client';
import { useLanguage } from '../i18n/LanguageContext';
import { useTheme } from '../theme/ThemeContext';
import { ChevronDown, ChevronUp, Copy, ExternalLink, Filter, Loader2, Search } from 'lucide-react';

const INPUT_BG = '#1A2333';
const INPUT_BORDER = 'rgba(148, 163, 184, 0.45)';

type AuditTab = 'user' | 'system' | 'critical' | 'whale';

type AuditRow = {
    cursor: string;
    timestamp: string | null;
    level: string | null;
    serviceName: string | null;
    message: string | null;
    traceId: string | null;
    spanId: string | null;
    correlationId: string | null;
    logger: string | null;
};

type AuditPage = {
    enabled?: boolean;
    disabledReason?: string | null;
    items: AuditRow[];
    total: number;
    page: number;
    size: number;
};

type TraceSummary = {
    enabled?: boolean;
    disabledReason?: string | null;
    found: boolean;
    traceId: string | null;
    nodes: { id: string; label: string; durationMs: number }[];
    edges: { from: string; to: string; durationMs: number }[];
    message?: string | null;
};

const SERVICE_OPTIONS = [
    '',
    'finance-service',
    'market-data-service',
    'notification-service',
    'whale-analytics-service',
    'metrics-service',
    'log-consumer-service',
];

const LEVEL_OPTIONS = ['INFO', 'WARN', 'ERROR'] as const;

/** Grafana solo embed: `VITE_GRAFANA_SOLO_BASE` is typically `/d-solo/<dashboardUid>/<slug>` (share → copy link). */
function buildGrafanaSoloEmbedUrl(soloBase: string, orgId: string, panelId: string): string {
    const trimmed = soloBase.trim().replace(/\/$/, '');
    const url = new URL(trimmed);
    url.searchParams.set('orgId', orgId);
    url.searchParams.set('panelId', panelId);
    url.searchParams.set('theme', 'dark');
    url.searchParams.set('kiosk', 'tv');
    return url.toString();
}

function envTrim(key: keyof ImportMetaEnv): string | undefined {
    const v = import.meta.env[key];
    return typeof v === 'string' && v.trim() !== '' ? v.trim() : undefined;
}

/** `{traceId}` → encodeURIComponent(traceId). Template must include placeholder for stable behavior. */
function buildServiceMapUrlFromTemplate(template: string, traceId: string): string | null {
    if (!template.includes('{traceId}')) return null;
    return template.split('{traceId}').join(encodeURIComponent(traceId));
}

function unwrap<T>(raw: unknown): T | null {
    if (raw == null) return null;
    if (typeof raw === 'object' && raw !== null && 'data' in raw) {
        const d = (raw as { data?: T }).data;
        return d ?? null;
    }
    return raw as T;
}

function buildQueryParams(
    tab: AuditTab,
    pageNum: number,
    size: number,
    from: string,
    to: string,
    smartQ: string,
    userId: string,
    username: string,
    actionType: string,
    serviceName: string,
    levelSet: Set<string>,
    traceId: string,
    correlationId: string,
    techQ: string
): Record<string, string | number | undefined> {
    const base: Record<string, string | number | undefined> = {
        page: pageNum,
        size,
        from: from.trim() || undefined,
        to: to.trim() || undefined,
    };

    if (tab === 'user') {
        const out: Record<string, string | number | undefined> = { ...base };
        if (userId.trim()) out.userId = userId.trim();
        if (username.trim()) out.username = username.trim();
        if (actionType === 'login') {
            const loginQ = [smartQ.trim(), 'login', 'auth', 'session', 'Keycloak', 'giriş'].filter(Boolean).join(' ').trim();
            if (loginQ) out.q = loginQ;
        } else {
            if (actionType) out.actionType = actionType;
            if (smartQ.trim()) out.q = smartQ.trim();
        }
        return out;
    }

    if (tab === 'system') {
        const levels = [...levelSet].sort().join(',');
        const q = [techQ.trim(), smartQ.trim()].filter(Boolean).join(' ').trim();
        return {
            ...base,
            serviceName: serviceName.trim() || undefined,
            levels: levels || undefined,
            traceId: traceId.trim() || undefined,
            correlationId: correlationId.trim() || undefined,
            q: q || undefined,
        };
    }

    if (tab === 'critical') {
        const q = [techQ.trim(), smartQ.trim()].filter(Boolean).join(' ').trim();
        return {
            ...base,
            levels: 'ERROR',
            traceId: traceId.trim() || undefined,
            correlationId: correlationId.trim() || undefined,
            q: q || undefined,
        };
    }

    /* whale */
    const q = [smartQ.trim(), 'whale'].filter(Boolean).join(' ').trim();
    return {
        ...base,
        serviceName: 'whale-analytics-service',
        q,
    };
}

export function AdminAudit() {
    const { tokens } = useTheme();
    const { t } = useLanguage();

    const [auditTab, setAuditTab] = useState<AuditTab>('system');
    const [smartQ, setSmartQ] = useState('');
    const [from, setFrom] = useState('');
    const [to, setTo] = useState('');

    const [userId, setUserId] = useState('');
    const [username, setUsername] = useState('');
    const [actionType, setActionType] = useState('');

    const [serviceName, setServiceName] = useState('');
    const [levelSet, setLevelSet] = useState<Set<string>>(new Set());
    const [traceId, setTraceId] = useState('');
    const [correlationId, setCorrelationId] = useState('');
    const [techQ, setTechQ] = useState('');

    const [showFilters, setShowFilters] = useState(true);
    const [page, setPage] = useState(0);
    const [size] = useState(20);

    const [rows, setRows] = useState<AuditRow[]>([]);
    const [total, setTotal] = useState(0);
    const [disabledReason, setDisabledReason] = useState<string | null>(null);
    const [listLoading, setListLoading] = useState(false);
    const [listError, setListError] = useState<string | null>(null);

    const [expandedCursor, setExpandedCursor] = useState<string | null>(null);
    const [expandLoading, setExpandLoading] = useState(false);
    const [expandDetail, setExpandDetail] = useState<Record<string, unknown> | null>(null);
    const [expandTrace, setExpandTrace] = useState<TraceSummary | null>(null);
    const [expandRow, setExpandRow] = useState<AuditRow | null>(null);
    const [exploreLoading, setExploreLoading] = useState(false);

    const smartPlaceholder = useMemo(() => {
        if (auditTab === 'user') return t('admin.auditSmartPlaceholderUser', 'Search username or action…');
        if (auditTab === 'whale') return t('admin.auditSmartPlaceholderWhale', 'Narrow whale logs…');
        return t('admin.auditSmartPlaceholderSystem', 'Keyword in message / logger…');
    }, [auditTab, t]);

    const grafanaSoloBase = envTrim('VITE_GRAFANA_SOLO_BASE');
    const grafanaOrgId = envTrim('VITE_GRAFANA_ORG_ID') ?? '1';
    const serviceMapTemplate = envTrim('VITE_GRAFANA_SERVICE_MAP_TRACE_URL_TEMPLATE');

    const grafanaAuditPanels = useMemo(() => {
        if (!grafanaSoloBase) return null;
        const pVol = envTrim('VITE_GRAFANA_AUDIT_PANEL_REQUEST_VOLUME') ?? '1';
        const pErr = envTrim('VITE_GRAFANA_AUDIT_PANEL_ERROR_RATE') ?? '2';
        const pHealth = envTrim('VITE_GRAFANA_AUDIT_PANEL_SERVICE_HEALTH') ?? '3';
        return [
            {
                id: pVol,
                title: t('admin.auditGrafanaPanelRequestVolume', 'Request volume'),
                iframeTitle: 'Grafana panel — request volume',
            },
            {
                id: pErr,
                title: t('admin.auditGrafanaPanelErrorRate', 'Error rate'),
                iframeTitle: 'Grafana panel — error rate',
            },
            {
                id: pHealth,
                title: t('admin.auditGrafanaPanelServiceHealth', 'Service health'),
                iframeTitle: 'Grafana panel — service health',
            },
        ].map((p) => ({
            ...p,
            src: buildGrafanaSoloEmbedUrl(grafanaSoloBase, grafanaOrgId, p.id),
        }));
    }, [grafanaOrgId, grafanaSoloBase, t]);

    useEffect(() => {
        setPage(0);
    }, [auditTab]);

    const fetchPage = useCallback(
        async (pageNum: number) => {
            setListLoading(true);
            setListError(null);
            try {
                const params = buildQueryParams(
                    auditTab,
                    pageNum,
                    size,
                    from,
                    to,
                    smartQ,
                    userId,
                    username,
                    actionType,
                    serviceName,
                    levelSet,
                    traceId,
                    correlationId,
                    techQ
                );
                const res = await financeClient.get('/api/admin/audit/logs', { params });
                const payload = unwrap<AuditPage>(res.data) ?? (res.data as AuditPage);
                if (payload && payload.enabled === false) {
                    setDisabledReason(payload.disabledReason ?? t('admin.auditDisabled', 'Observability disabled'));
                    setRows([]);
                    setTotal(0);
                } else {
                    setDisabledReason(null);
                    setRows(payload?.items ?? []);
                    setTotal(payload?.total ?? 0);
                }
            } catch (e: unknown) {
                const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
                setListError(msg ?? (e as Error).message ?? t('admin.auditLoadFailed', 'Logs could not be loaded'));
            } finally {
                setListLoading(false);
            }
        },
        [actionType, auditTab, correlationId, from, levelSet, serviceName, size, smartQ, t, techQ, to, traceId, userId, username]
    );

    useEffect(() => {
        void fetchPage(page);
    }, [fetchPage, page]);

    const runSearch = () => {
        if (page === 0) void fetchPage(0);
        else setPage(0);
    };

    const toggleLevel = (lv: string) => {
        setLevelSet((prev) => {
            const next = new Set(prev);
            if (next.has(lv)) next.delete(lv);
            else next.add(lv);
            return next;
        });
    };

    const toggleExpand = async (row: AuditRow) => {
        if (expandedCursor === row.cursor) {
            setExpandedCursor(null);
            setExpandRow(null);
            setExpandDetail(null);
            setExpandTrace(null);
            return;
        }
        setExpandedCursor(row.cursor);
        setExpandRow(row);
        setExpandDetail(null);
        setExpandTrace(null);
        setExpandLoading(true);
        try {
            const dRes = await financeClient.get('/api/admin/audit/logs/detail', { params: { cursor: row.cursor } });
            const detailPayload = unwrap<{ source?: Record<string, unknown> }>(dRes.data) ?? dRes.data;
            if (detailPayload && typeof detailPayload === 'object' && 'source' in detailPayload) {
                setExpandDetail((detailPayload as { source: Record<string, unknown> }).source ?? null);
            }
            if (row.traceId) {
                const tRes = await financeClient.get(`/api/admin/observability/traces/${encodeURIComponent(row.traceId)}/summary`);
                setExpandTrace(unwrap<TraceSummary>(tRes.data) ?? (tRes.data as TraceSummary) ?? null);
            }
        } catch {
            setExpandDetail(null);
        } finally {
            setExpandLoading(false);
        }
    };

    const openGrafanaExplore = async (tid: string | null) => {
        if (!tid) return;
        setExploreLoading(true);
        try {
            const res = await financeClient.get('/api/admin/observability/explore/trace', { params: { traceId: tid } });
            const payload = unwrap<{ url?: string | null }>(res.data) ?? res.data;
            const url = (payload as { url?: string })?.url;
            if (url) window.open(url, '_blank', 'noopener,noreferrer');
        } finally {
            setExploreLoading(false);
        }
    };

    const openServiceMap = (tid: string | null) => {
        if (!tid || !serviceMapTemplate) return;
        const url = buildServiceMapUrlFromTemplate(serviceMapTemplate, tid);
        if (url) window.open(url, '_blank', 'noopener,noreferrer');
    };

    const copyText = (text: string) => {
        void navigator.clipboard.writeText(text);
    };

    const tabBtn = (tab: AuditTab, label: string) => {
        const active = auditTab === tab;
        return (
            <button
                type="button"
                key={tab}
                onClick={() => setAuditTab(tab)}
                style={{
                    flex: 1,
                    minWidth: 120,
                    padding: '12px 16px',
                    borderRadius: 10,
                    border: active ? `2px solid ${tokens.accent}` : `1px solid ${INPUT_BORDER}`,
                    background: active ? 'rgba(37, 99, 235, 0.15)' : INPUT_BG,
                    color: active ? tokens.text : tokens.textMuted,
                    fontWeight: active ? 700 : 500,
                    fontSize: 13,
                    cursor: 'pointer',
                    transition: 'background 0.15s',
                }}
            >
                {label}
            </button>
        );
    };

    const pageStyle: React.CSSProperties = {
        padding: 24,
        background: tokens.bg,
        color: tokens.text,
        minHeight: '100%',
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
    };

    return (
        <div style={pageStyle}>
            <style>{`
                .audit-grafana-grid {
                    display: grid;
                    grid-template-columns: repeat(3, minmax(0, 1fr));
                    gap: 12px;
                }
                @media (max-width: 900px) {
                    .audit-grafana-grid { grid-template-columns: 1fr; }
                }
            `}</style>
            <div>
                <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 }}>{t('admin.auditCenterTitle', 'Audit Center')}</h1>
                <p style={{ color: tokens.textMuted, fontSize: '0.875rem' }}>{t('admin.auditCenterSubtitle', '')}</p>
            </div>

            {grafanaAuditPanels && (
                <>
                    {/*
                      Panel IDs: Grafana → panel title → Inspect → Panel JSON → "id" must match VITE_GRAFANA_AUDIT_PANEL_* env vars.
                      Solo base: share dashboard → embed → copy the /d-solo/... path (uid, not necessarily the short slug).
                    */}
                    <div>
                        <h2 style={{ fontSize: '0.95rem', fontWeight: 600, margin: '0 0 10px', color: tokens.text }}>
                            {t('admin.auditObservabilityTitle', 'Live metrics')}
                        </h2>
                        <div className="audit-grafana-grid">
                            {grafanaAuditPanels.map((p) => (
                                <div
                                    key={p.id}
                                    style={{
                                        background: 'rgba(11, 17, 32, 0.92)',
                                        border: `1px solid ${INPUT_BORDER}`,
                                        borderRadius: 12,
                                        boxShadow: '0 4px 24px rgba(0, 0, 0, 0.25)',
                                        overflow: 'hidden',
                                        display: 'flex',
                                        flexDirection: 'column',
                                        minWidth: 0,
                                    }}
                                >
                                    <div
                                        style={{
                                            fontSize: 11,
                                            textTransform: 'uppercase',
                                            letterSpacing: '0.05em',
                                            color: tokens.textMuted,
                                            padding: '8px 10px 0',
                                        }}
                                    >
                                        {p.title}
                                    </div>
                                    {/*
                                      pointer-events: none — disables clicks inside Grafana (view-only). Remove wrapper style to allow drill-down.
                                    */}
                                    <div style={{ pointerEvents: 'none', flex: 1, minHeight: 200 }}>
                                        <iframe
                                            title={p.iframeTitle}
                                            src={p.src}
                                            loading="lazy"
                                            referrerPolicy="no-referrer-when-downgrade"
                                            style={{ border: 'none', width: '100%', height: 200, display: 'block' }}
                                        />
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                    <div
                        style={{
                            height: 1,
                            background: tokens.border ?? INPUT_BORDER,
                            margin: '4px 0 8px',
                            opacity: 0.85,
                        }}
                    />
                </>
            )}

            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                {tabBtn('user', t('admin.auditTabUser', 'User activity'))}
                {tabBtn('system', t('admin.auditTabSystem', 'System logs'))}
                {tabBtn('critical', t('admin.auditTabCritical', 'Critical errors'))}
                {tabBtn('whale', t('admin.auditTabWhale', 'Whale alerts'))}
            </div>

            {disabledReason && (
                <div style={{ padding: 12, borderRadius: 8, background: tokens.bgCard, border: `1px solid ${tokens.border}` }}>
                    {disabledReason}
                </div>
            )}

            <div style={{ display: 'flex', gap: 12, alignItems: 'stretch', flexWrap: 'wrap' }}>
                <input
                    type="search"
                    value={smartQ}
                    onChange={(e) => setSmartQ(e.target.value)}
                    placeholder={smartPlaceholder}
                    onKeyDown={(e) => e.key === 'Enter' && runSearch()}
                    style={{
                        flex: 1,
                        minWidth: 220,
                        padding: '12px 16px',
                        borderRadius: 10,
                        border: `1px solid ${INPUT_BORDER}`,
                        background: INPUT_BG,
                        color: tokens.text,
                        fontSize: 15,
                    }}
                />
                <button type="button" onClick={runSearch} style={btnPrimaryLarge()}>
                    <Search size={18} style={{ marginRight: 8 }} />
                    {t('admin.auditSearch', 'Search')}
                </button>
            </div>

            <button
                type="button"
                onClick={() => setShowFilters((v) => !v)}
                style={{
                    alignSelf: 'flex-start',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: '8px 14px',
                    borderRadius: 8,
                    border: `1px solid ${INPUT_BORDER}`,
                    background: INPUT_BG,
                    color: tokens.textMuted,
                    cursor: 'pointer',
                    fontSize: 13,
                }}
            >
                <Filter size={16} />
                {t('admin.auditFiltersToggle', 'Filters')}
                {showFilters ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>

            {showFilters && (
                <div
                    style={{
                        padding: 16,
                        borderRadius: 12,
                        background: INPUT_BG,
                        border: `1px solid ${INPUT_BORDER}`,
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 16,
                    }}
                >
                    <div>
                        <div style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em', color: tokens.textMuted, marginBottom: 8 }}>
                            {t('admin.auditGroupTime', 'Time range')}
                        </div>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
                            <label style={labelCompact}>
                                {t('admin.auditFrom', 'From (ISO)')}
                                <input value={from} onChange={(e) => setFrom(e.target.value)} placeholder="optional" style={filterInput(tokens)} />
                            </label>
                            <label style={labelCompact}>
                                {t('admin.auditTo', 'To (ISO)')}
                                <input value={to} onChange={(e) => setTo(e.target.value)} placeholder="optional" style={filterInput(tokens)} />
                            </label>
                        </div>
                    </div>

                    {auditTab === 'user' && (
                        <div>
                            <div style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em', color: tokens.textMuted, marginBottom: 8 }}>
                                {t('admin.auditGroupIdentity', 'User')}
                            </div>
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
                                <label style={labelCompact}>
                                    {t('admin.auditUserId', 'User ID')}
                                    <input value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="DB id" style={filterInput(tokens)} />
                                </label>
                                <label style={labelCompact}>
                                    {t('admin.auditUsername', 'Username')}
                                    <input
                                        value={username}
                                        onChange={(e) => setUsername(e.target.value)}
                                        placeholder={t('admin.auditUsernamePh', 'Keycloak preferred_username')}
                                        style={filterInput(tokens)}
                                    />
                                </label>
                                <label style={labelCompact}>
                                    {t('admin.auditActionType', 'Action')}
                                    <select value={actionType} onChange={(e) => setActionType(e.target.value)} style={filterInput(tokens)}>
                                        <option value="">{t('admin.auditActionAll', 'All')}</option>
                                        <option value="TRADE">{t('admin.auditActionTrade', 'Buy / Sell')}</option>
                                        <option value="PROFILE">{t('admin.auditActionProfile', 'Profile update')}</option>
                                        <option value="FUND_REQUEST">{t('admin.auditActionFundRequest', 'Fund request')}</option>
                                        <option value="PORTFOLIO">{t('admin.auditActionPortfolio', 'Portfolio')}</option>
                                        <option value="BALANCE">{t('admin.auditActionBalance', 'Balance')}</option>
                                        <option value="TRANSACTION">{t('admin.auditActionTransaction', 'Transaction')}</option>
                                        <option value="REGISTRATION">{t('admin.auditActionRegistration', 'Registration')}</option>
                                        <option value="login">{t('admin.auditActionLogin', 'Login / session (text)')}</option>
                                    </select>
                                </label>
                            </div>
                        </div>
                    )}

                    {(auditTab === 'system' || auditTab === 'critical') && (
                        <>
                            <div>
                                <div style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em', color: tokens.textMuted, marginBottom: 8 }}>
                                    {t('admin.auditGroupService', 'Service & level')}
                                </div>
                                {auditTab === 'system' && (
                                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center' }}>
                                        <label style={labelCompact}>
                                            {t('admin.auditService', 'Service')}
                                            <select value={serviceName} onChange={(e) => setServiceName(e.target.value)} style={filterInput(tokens)}>
                                                {SERVICE_OPTIONS.map((s) => (
                                                    <option key={s || 'all'} value={s}>
                                                        {s || t('admin.auditAllServices', 'All services')}
                                                    </option>
                                                ))}
                                            </select>
                                        </label>
                                        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                                            <span style={{ fontSize: 12, color: tokens.textMuted }}>{t('admin.auditLevels', 'Levels')}:</span>
                                            {LEVEL_OPTIONS.map((lv) => (
                                                <label key={lv} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, cursor: 'pointer' }}>
                                                    <input type="checkbox" checked={levelSet.has(lv)} onChange={() => toggleLevel(lv)} />
                                                    {lv}
                                                </label>
                                            ))}
                                        </div>
                                    </div>
                                )}
                                {auditTab === 'critical' && (
                                    <p style={{ margin: 0, fontSize: 13, color: tokens.textMuted }}>{t('admin.auditCriticalHint', 'Only ERROR level logs.')}</p>
                                )}
                            </div>
                            <div>
                                <div style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em', color: tokens.textMuted, marginBottom: 8 }}>
                                    {t('admin.auditGroupTechnical', 'Trace & correlation')}
                                </div>
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
                                    <label style={labelCompact}>
                                        traceId
                                        <input value={traceId} onChange={(e) => setTraceId(e.target.value)} style={filterMono(tokens)} />
                                    </label>
                                    <label style={labelCompact}>
                                        correlationId
                                        <input value={correlationId} onChange={(e) => setCorrelationId(e.target.value)} style={filterMono(tokens)} />
                                    </label>
                                </div>
                                {auditTab === 'system' && (
                                    <label style={{ ...labelCompact, marginTop: 12, display: 'block', maxWidth: 480 }}>
                                        {t('admin.auditTechSearch', 'Extra keywords')}
                                        <input value={techQ} onChange={(e) => setTechQ(e.target.value)} style={filterInput(tokens)} />
                                    </label>
                                )}
                                {auditTab === 'critical' && (
                                    <label style={{ ...labelCompact, marginTop: 12, display: 'block', maxWidth: 480 }}>
                                        {t('admin.auditTechSearch', 'Extra keywords')}
                                        <input value={techQ} onChange={(e) => setTechQ(e.target.value)} style={filterInput(tokens)} />
                                    </label>
                                )}
                            </div>
                        </>
                    )}

                    {auditTab === 'whale' && (
                        <p style={{ margin: 0, fontSize: 13, color: tokens.textMuted }}>{t('admin.auditWhaleHint', 'Filtered to whale-analytics-service + whale keywords.')}</p>
                    )}
                </div>
            )}

            {listError && <div style={{ color: '#f87171' }}>{listError}</div>}

            <div style={{ position: 'relative', flex: 1, minHeight: 200 }}>
                {listLoading && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: tokens.textMuted }}>
                        <Loader2 size={18} />
                        {t('admin.loading', 'Loading…')}
                    </div>
                )}
                {!listLoading && (
                    <div style={{ overflowX: 'auto', borderRadius: 10, border: `1px solid ${INPUT_BORDER}` }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                            <thead>
                                <tr style={{ background: INPUT_BG }}>
                                    <th style={{ ...th, width: 28 }} />
                                    <th style={th}>{t('admin.auditColTime', 'Time')}</th>
                                    <th style={th}>{t('admin.auditColService', 'Service')}</th>
                                    <th style={th}>Level</th>
                                    <th style={th}>traceId</th>
                                    <th style={th}>{t('admin.auditColMessage', 'Message')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {rows.map((r) => (
                                    <React.Fragment key={r.cursor}>
                                        <tr
                                            style={{
                                                borderTop: `1px solid ${INPUT_BORDER}`,
                                                cursor: 'pointer',
                                                background: expandedCursor === r.cursor ? 'rgba(37, 99, 235, 0.08)' : undefined,
                                            }}
                                            onClick={() => void toggleExpand(r)}
                                        >
                                            <td style={td}>{expandedCursor === r.cursor ? <ChevronUp size={16} /> : <ChevronDown size={16} />}</td>
                                            <td style={td}>{r.timestamp ?? '—'}</td>
                                            <td style={td}>{r.serviceName ?? '—'}</td>
                                            <td style={td}>{r.level ?? '—'}</td>
                                            <td style={{ ...td, maxWidth: 160 }}>
                                                {r.traceId ? (
                                                    <button
                                                        type="button"
                                                        title="Copy"
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            copyText(r.traceId!);
                                                        }}
                                                        style={badgeBtn}
                                                    >
                                                        <span style={{ fontFamily: 'monospace', fontSize: 11 }}>{r.traceId.slice(0, 12)}…</span>
                                                        <Copy size={12} />
                                                    </button>
                                                ) : (
                                                    '—'
                                                )}
                                            </td>
                                            <td style={{ ...td, maxWidth: 360, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                                {r.message ?? '—'}
                                            </td>
                                        </tr>
                                        {expandedCursor === r.cursor && (
                                            <tr>
                                                <td colSpan={6} style={{ padding: 0, background: tokens.bg }}>
                                                    <div style={{ padding: 16, borderTop: `1px solid ${INPUT_BORDER}` }}>
                                                        {expandLoading && (
                                                            <div style={{ display: 'flex', gap: 8, color: tokens.textMuted }}>
                                                                <Loader2 size={18} />
                                                            </div>
                                                        )}
                                                        {!expandLoading && expandRow && (
                                                            <>
                                                                <div style={{ fontSize: 12, color: tokens.textMuted, marginBottom: 8 }}>
                                                                    {t('admin.auditTraceTopology', 'Service topology (Tempo)')}
                                                                </div>
                                                                {!expandRow.traceId && (
                                                                    <div style={{ fontSize: 13, color: tokens.textMuted, marginBottom: 12 }}>
                                                                        {t('admin.auditNoTrace', 'No traceId')}
                                                                    </div>
                                                                )}
                                                                {expandRow.traceId && expandTrace && !expandTrace.found && (
                                                                    <div style={{ fontSize: 13, color: tokens.textMuted, marginBottom: 12 }}>
                                                                        {expandTrace.message ?? t('admin.auditTraceNotFound', 'Trace not found')}
                                                                    </div>
                                                                )}
                                                                {expandRow.traceId && expandTrace?.found && (
                                                                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
                                                                        {expandTrace.nodes.map((n) => (
                                                                            <span key={n.id} style={nodePill}>
                                                                                {n.label}{' '}
                                                                                <span style={{ opacity: 0.7 }}>({n.durationMs} ms)</span>
                                                                            </span>
                                                                        ))}
                                                                    </div>
                                                                )}
                                                                <div style={{ fontSize: 12, color: tokens.textMuted, marginBottom: 6 }}>JSON</div>
                                                                <pre
                                                                    style={{
                                                                        margin: '0 0 12px',
                                                                        padding: 12,
                                                                        borderRadius: 8,
                                                                        background: '#0f172a',
                                                                        color: '#e2e8f0',
                                                                        fontSize: 11,
                                                                        maxHeight: 240,
                                                                        overflow: 'auto',
                                                                    }}
                                                                >
                                                                    {expandDetail ? JSON.stringify(expandDetail, null, 2) : '—'}
                                                                </pre>
                                                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'center' }}>
                                                                    <button
                                                                        type="button"
                                                                        disabled={!expandRow.traceId || exploreLoading}
                                                                        onClick={(e) => {
                                                                            e.stopPropagation();
                                                                            void openGrafanaExplore(expandRow.traceId);
                                                                        }}
                                                                        style={{
                                                                            ...btnPrimaryLarge(),
                                                                            opacity: !expandRow.traceId ? 0.5 : 1,
                                                                        }}
                                                                    >
                                                                        {exploreLoading ? (
                                                                            <Loader2 size={18} />
                                                                        ) : (
                                                                            <ExternalLink size={18} style={{ marginRight: 8 }} />
                                                                        )}
                                                                        {t('admin.auditOpenGrafana', 'Open in Grafana')}
                                                                    </button>
                                                                    {serviceMapTemplate &&
                                                                        expandRow.traceId &&
                                                                        buildServiceMapUrlFromTemplate(serviceMapTemplate, expandRow.traceId) && (
                                                                            <button
                                                                                type="button"
                                                                                onClick={(e) => {
                                                                                    e.stopPropagation();
                                                                                    openServiceMap(expandRow.traceId);
                                                                                }}
                                                                                style={btnGhost(tokens)}
                                                                            >
                                                                                <ExternalLink size={16} style={{ marginRight: 8 }} />
                                                                                {t('admin.auditServiceMapOpen', 'View in Service Map')}
                                                                            </button>
                                                                        )}
                                                                </div>
                                                            </>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                        )}
                                    </React.Fragment>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
                <div style={{ marginTop: 8, fontSize: 12, color: tokens.textMuted }}>
                    {t('admin.auditTotal', 'Total')}: {total} — {t('admin.auditPage', 'Page')} {page + 1}
                </div>
                <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                    <button type="button" disabled={page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))} style={btnGhost(tokens)}>
                        {t('admin.auditPrev', 'Previous')}
                    </button>
                    <button type="button" disabled={(page + 1) * size >= total} onClick={() => setPage((p) => p + 1)} style={btnGhost(tokens)}>
                        {t('admin.auditNext', 'Next')}
                    </button>
                </div>
            </div>
        </div>
    );
}

const labelCompact: React.CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6, fontSize: 12, color: '#94a3b8' };

const th: React.CSSProperties = { textAlign: 'left', padding: '10px 12px', fontSize: 11, textTransform: 'uppercase', color: '#94a3b8' };
const td: React.CSSProperties = { padding: '10px 12px', verticalAlign: 'middle' };

const badgeBtn: React.CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    padding: '4px 8px',
    borderRadius: 6,
    border: '1px solid rgba(148, 163, 184, 0.5)',
    background: '#0f172a',
    color: '#e2e8f0',
    cursor: 'pointer',
    maxWidth: '100%',
};

const nodePill: React.CSSProperties = {
    padding: '4px 10px',
    borderRadius: 999,
    background: '#1A2333',
    border: '1px solid rgba(148, 163, 184, 0.35)',
    fontSize: 12,
};

function filterInput(tokens: { text: string }): React.CSSProperties {
    return {
        padding: '8px 10px',
        borderRadius: 8,
        border: `1px solid ${INPUT_BORDER}`,
        background: '#0f172a',
        color: tokens.text,
        minWidth: 160,
        fontSize: 13,
    };
}

function filterMono(tokens: { text: string }): React.CSSProperties {
    return { ...filterInput(tokens), fontFamily: 'ui-monospace, monospace', fontSize: 12 };
}

function btnPrimaryLarge(): React.CSSProperties {
    return {
        display: 'inline-flex',
        alignItems: 'center',
        padding: '12px 22px',
        borderRadius: 10,
        border: 'none',
        cursor: 'pointer',
        background: '#2563eb',
        color: '#fff',
        fontWeight: 700,
        fontSize: 14,
        boxShadow: '0 2px 12px rgba(37, 99, 235, 0.35)',
    };
}

function btnGhost(tokens: { border: string; bg: string; text: string }): React.CSSProperties {
    return {
        padding: '8px 14px',
        borderRadius: 8,
        border: `1px solid ${INPUT_BORDER}`,
        background: INPUT_BG,
        color: tokens.text,
        cursor: 'pointer',
    };
}
