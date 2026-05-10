import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { flushSync } from 'react-dom';
import { financeClient } from '../api/client';
import { useLanguage } from '../i18n/LanguageContext';
import { useTheme } from '../theme/ThemeContext';
import { ChevronDown, ChevronUp, Copy, ExternalLink, Filter, Loader2, Maximize2, Search, X } from 'lucide-react';

const INPUT_BG = '#1A2333';
const INPUT_BORDER = 'rgba(148, 163, 184, 0.45)';
const SILVER_MUTED = '#B8C1CC';
const SKELETON_LO = '#162032';
const SKELETON_HI = '#1A2333';

/** Canlı metrikler: görünür iframe alanı (px). */
const AUDIT_GRAFANA_EMBED_HEIGHT_PX = 250;
/** d-solo üst panel başlığı / time bar — translateY ile kırpılır (Grafana sürümüne göre 40–52 arası denenebilir). */
const AUDIT_GRAFANA_CLIP_TOP_PX = 48;
const ONE_H_MS = 3600000;

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

/** Discovery bar: odak WARN + ERROR (INFO gürültüyü azaltır). */
const DISCOVERY_LEVELS = ['WARN', 'ERROR'] as const;
const AUDIT_SERVICE_TAGS = SERVICE_OPTIONS.filter(Boolean);
type TimePreset = '15m' | '1h' | 'today' | 'yesterday' | 'custom';

type SmartBarParse = {
    remainder: string;
    username?: string;
    userId?: string;
    traceId?: string;
    correlationId?: string;
    services?: string[];
};

function parseSmartBar(q: string): SmartBarParse {
    const tokens = q.trim().split(/\s+/).filter(Boolean);
    const remainder: string[] = [];
    const out: SmartBarParse = { remainder: '' };
    const services: string[] = [];
    for (const tok of tokens) {
        const i = tok.indexOf(':');
        if (i > 0) {
            const key = tok.slice(0, i).toLowerCase();
            const val = tok.slice(i + 1).trim();
            if (!val) {
                remainder.push(tok);
                continue;
            }
            if (key === 'user' || key === 'username') out.username = val;
            else if (key === 'userid' || key === 'user_id') out.userId = val;
            else if (key === 'service') services.push(val);
            else if (key === 'trace' || key === 'traceid') out.traceId = val;
            else if (key === 'correlation' || key === 'correlationid') out.correlationId = val;
            else remainder.push(tok);
        } else remainder.push(tok);
    }
    out.remainder = remainder.join(' ');
    if (services.length > 0) out.services = services;
    return out;
}

function toIsoInput(d: Date): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function computeAuditTimeRange(preset: TimePreset, customFrom: string, customTo: string): { auditFrom: string; auditTo: string } {
    const now = new Date();
    if (preset === 'custom') {
        const cf = customFrom.trim();
        const ct = customTo.trim();
        if (!cf && !ct) {
            const fromD = new Date(now.getTime() - ONE_H_MS);
            return { auditFrom: fromD.toISOString(), auditTo: now.toISOString() };
        }
        const parseLocal = (s: string) => {
            const x = new Date(s);
            return Number.isNaN(x.getTime()) ? null : x;
        };
        if (cf && ct) {
            const a = parseLocal(cf);
            const b = parseLocal(ct);
            if (a && b) return { auditFrom: a.toISOString(), auditTo: b.toISOString() };
        }
        if (cf) {
            const a = parseLocal(cf);
            if (a) return { auditFrom: a.toISOString(), auditTo: now.toISOString() };
        }
        if (ct) {
            const b = parseLocal(ct);
            if (b) return { auditFrom: new Date(b.getTime() - ONE_H_MS).toISOString(), auditTo: b.toISOString() };
        }
        const fromD = new Date(now.getTime() - ONE_H_MS);
        return { auditFrom: fromD.toISOString(), auditTo: now.toISOString() };
    }
    if (preset === '15m') {
        const fromD = new Date(now.getTime() - 15 * 60 * 1000);
        return { auditFrom: fromD.toISOString(), auditTo: now.toISOString() };
    }
    if (preset === '1h') {
        const fromD = new Date(now.getTime() - ONE_H_MS);
        return { auditFrom: fromD.toISOString(), auditTo: now.toISOString() };
    }
    if (preset === 'today') {
        const start = new Date(now);
        start.setHours(0, 0, 0, 0);
        return { auditFrom: start.toISOString(), auditTo: now.toISOString() };
    }
    if (preset === 'yesterday') {
        const start = new Date(now);
        start.setDate(start.getDate() - 1);
        start.setHours(0, 0, 0, 0);
        const end = new Date(start);
        end.setHours(23, 59, 59, 999);
        return { auditFrom: start.toISOString(), auditTo: end.toISOString() };
    }
    const fromD = new Date(now.getTime() - ONE_H_MS);
    return { auditFrom: fromD.toISOString(), auditTo: now.toISOString() };
}

/** Zaman aralığı yoksa son 1 saat; seçiliyse Grafana epoch ms veya now ile uyumlu string. */
function grafanaFromToParams(fromInput: string, toInput: string): { from: string; to: string } {
    const f = fromInput.trim();
    const t = toInput.trim();
    if (!f && !t) return { from: 'now-1h', to: 'now' };
    const now = Date.now();
    const parseMs = (s: string): number | null => {
        const x = Date.parse(s);
        return Number.isFinite(x) ? x : null;
    };
    if (f && t) {
        const msF = parseMs(f);
        const msT = parseMs(t);
        if (msF == null || msT == null) return { from: 'now-1h', to: 'now' };
        return { from: String(msF), to: String(msT) };
    }
    if (f) {
        const msF = parseMs(f);
        if (msF == null) return { from: 'now-1h', to: 'now' };
        return { from: String(msF), to: String(now) };
    }
    const msT = parseMs(t);
    if (msT == null) return { from: 'now-1h', to: 'now' };
    return { from: String(msT - ONE_H_MS), to: String(msT) };
}

/** Grafana solo embed: `VITE_GRAFANA_SOLO_BASE` is typically `/d-solo/<dashboardUid>/<slug>` (share → copy link). */
function buildGrafanaSoloEmbedUrl(
    soloBase: string,
    orgId: string,
    panelId: string,
    rangeFrom: string,
    rangeTo: string,
    reloadNonce?: number
): string {
    const trimmed = soloBase.trim().replace(/\/$/, '');
    const url = new URL(trimmed);
    url.searchParams.set('orgId', orgId);
    url.searchParams.set('panelId', panelId);
    url.searchParams.set('theme', 'dark');
    url.searchParams.set('viewPanel', 'true');
    url.searchParams.set('kiosk', 'tv');
    url.searchParams.set('from', rangeFrom);
    url.searchParams.set('to', rangeTo);
    if (reloadNonce != null && reloadNonce > 0) url.searchParams.set('_nc', String(reloadNonce));
    return url.toString();
}

/** Tam dashboard (yeni sekme): `/d-solo/…` → `/d/…`, aynı zaman + panel odağı. */
function buildGrafanaFullDashboardUrl(
    soloBase: string,
    orgId: string,
    panelId: string,
    rangeFrom: string,
    rangeTo: string
): string {
    const trimmed = soloBase.trim().replace(/\/$/, '');
    const url = new URL(trimmed);
    url.pathname = url.pathname.replace(/\/d-solo\//, '/d/');
    url.searchParams.set('orgId', orgId);
    url.searchParams.set('from', rangeFrom);
    url.searchParams.set('to', rangeTo);
    url.searchParams.set('theme', 'dark');
    url.searchParams.set('viewPanel', panelId);
    return url.toString();
}

type GrafanaExplorePane = {
    range?: Record<string, unknown>;
    [key: string]: unknown;
};

/**
 * Explore / Traces Drilldown URL’lerine Audit sayfasındaki zaman aralığını yazar:
 * - Üst düzey `from` / `to` (ör. exploretraces-app)
 * - `panes` query’sindeki JSON içinde her pane’in `range.from` / `range.to`
 */
function mergeGrafanaExploreTimeRange(baseUrl: string, rangeFrom: string, rangeTo: string): string {
    try {
        const url = new URL(baseUrl);
        url.searchParams.set('from', rangeFrom);
        url.searchParams.set('to', rangeTo);
        const panesRaw = url.searchParams.get('panes');
        if (panesRaw) {
            try {
                const panes = JSON.parse(panesRaw) as Record<string, GrafanaExplorePane>;
                for (const k of Object.keys(panes)) {
                    const pane = panes[k];
                    if (!pane || typeof pane !== 'object') continue;
                    const prevRange =
                        pane.range && typeof pane.range === 'object' && !Array.isArray(pane.range) ? { ...pane.range } : {};
                    panes[k] = {
                        ...pane,
                        range: { ...prevRange, from: rangeFrom, to: rangeTo },
                    };
                }
                url.searchParams.set('panes', JSON.stringify(panes));
            } catch {
                /* panes parse edilemezse üst from/to yeterli olabilir */
            }
        }
        return url.toString();
    } catch {
        return baseUrl;
    }
}

/**
 * Vite derlemede yalnızca `import.meta.env.VITE_*` için **statik** erişimi (`import.meta.env.VITE_FOO`)
 * sabitlere çevirir. `import.meta.env[key]` kullanılırsa p95 / Tempo gibi değişkenler üretimde boş kalır.
 */
function viteStr(v: unknown): string | undefined {
    if (v == null) return undefined;
    const s = typeof v === 'string' ? v : String(v);
    const t = s.trim();
    return t !== '' ? t : undefined;
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
    selectedServices: Set<string>,
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
        const svcCsv =
            selectedServices.size === 0 ? undefined : [...selectedServices].sort().join(',');
        return {
            ...base,
            serviceName: svcCsv,
            levels: levels || undefined,
            username: username.trim() || undefined,
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
            username: username.trim() || undefined,
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
    const [timePreset, setTimePreset] = useState<TimePreset>('1h');
    const [customFrom, setCustomFrom] = useState('');
    const [customTo, setCustomTo] = useState('');

    const [userId, setUserId] = useState('');
    const [username, setUsername] = useState('');
    const [actionType, setActionType] = useState('');

    const [selectedServices, setSelectedServices] = useState<Set<string>>(() => new Set());
    const [levelSet, setLevelSet] = useState<Set<string>>(() => new Set([...DISCOVERY_LEVELS]));
    const [traceId, setTraceId] = useState('');
    const [correlationId, setCorrelationId] = useState('');
    const [techQ, setTechQ] = useState('');

    const [advancedFiltersOpen, setAdvancedFiltersOpen] = useState(false);
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
    /** Ara: aynı zaman aralığında Grafana iframe’lerini yeniden yüklemek için src’ye güvenli cache-bust. */
    const [grafanaReloadNonce, setGrafanaReloadNonce] = useState(0);
    const [grafanaModal, setGrafanaModal] = useState<{ src: string; title: string } | null>(null);
    const [grafanaIframeLoaded, setGrafanaIframeLoaded] = useState<Record<string, boolean>>({});

    const grafanaSoloBase = viteStr(import.meta.env.VITE_GRAFANA_SOLO_BASE);
    const grafanaOrgId = viteStr(import.meta.env.VITE_GRAFANA_ORG_ID) ?? '1';
    const serviceMapTemplate = viteStr(import.meta.env.VITE_GRAFANA_SERVICE_MAP_TRACE_URL_TEMPLATE);
    const { auditFrom, auditTo } = useMemo(
        () => computeAuditTimeRange(timePreset, customFrom, customTo),
        [customFrom, customTo, timePreset]
    );
    const grafanaRange = useMemo(() => grafanaFromToParams(auditFrom, auditTo), [auditFrom, auditTo]);

    const grafanaAuditPanels = useMemo(() => {
        if (!grafanaSoloBase) return null;
        const pVol = viteStr(import.meta.env.VITE_GRAFANA_AUDIT_PANEL_REQUEST_VOLUME) ?? '1';
        const pErr = viteStr(import.meta.env.VITE_GRAFANA_AUDIT_PANEL_ERROR_RATE) ?? '2';
        const pP95 = viteStr(import.meta.env.VITE_GRAFANA_AUDIT_PANEL_P95_RESPONSE);
        const pHealth = viteStr(import.meta.env.VITE_GRAFANA_AUDIT_PANEL_SERVICE_HEALTH) ?? '3';
        const base: Array<{ id: string; title: string; iframeTitle: string }> = [
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
        ];
        if (pP95) {
            base.push({
                id: pP95,
                title: t('admin.auditGrafanaPanelP95', 'p95 response time'),
                iframeTitle: 'Grafana panel — p95 latency',
            });
        }
        base.push({
            id: pHealth,
            title: t('admin.auditGrafanaPanelServiceHealth', 'Service health'),
            iframeTitle: 'Grafana panel — service health',
        });
        const pTempoSpan = viteStr(import.meta.env.VITE_GRAFANA_AUDIT_PANEL_TEMPO_SPAN_RATE);
        const pTempoNode = viteStr(import.meta.env.VITE_GRAFANA_AUDIT_PANEL_TEMPO_NODE_GRAPH);
        const extra: Array<{ id: string; title: string; iframeTitle: string }> = [];
        if (pTempoSpan) {
            extra.push({
                id: pTempoSpan,
                title: t('admin.auditGrafanaPanelTempoTraces', 'Tempo — iz özeti / drilldown'),
                iframeTitle: 'Grafana — Tempo traces',
            });
        }
        if (pTempoNode) {
            extra.push({
                id: pTempoNode,
                title: t('admin.auditGrafanaPanelTempoNodeGraph', 'Tempo — servis haritası (node graph)'),
                iframeTitle: 'Grafana — Tempo node graph',
            });
        }
        return [...base, ...extra].map((p) => ({
            ...p,
            src: buildGrafanaSoloEmbedUrl(
                grafanaSoloBase,
                grafanaOrgId,
                p.id,
                grafanaRange.from,
                grafanaRange.to,
                grafanaReloadNonce > 0 ? grafanaReloadNonce : undefined
            ),
            externalDashboardUrl: buildGrafanaFullDashboardUrl(
                grafanaSoloBase,
                grafanaOrgId,
                p.id,
                grafanaRange.from,
                grafanaRange.to
            ),
        }));
    }, [grafanaOrgId, grafanaReloadNonce, grafanaRange.from, grafanaRange.to, grafanaSoloBase, t]);

    const grafanaPanelsSrcSignature = useMemo(
        () => grafanaAuditPanels?.map((p) => p.src).join('\0') ?? '',
        [grafanaAuditPanels]
    );

    useEffect(() => {
        setGrafanaIframeLoaded({});
    }, [grafanaPanelsSrcSignature]);

    useEffect(() => {
        if (!grafanaModal) return;
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setGrafanaModal(null);
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [grafanaModal]);

    const tempoExploreDrilldownUrl = viteStr(import.meta.env.VITE_GRAFANA_TEMPO_EXPLORE_DRILLDOWN_URL);
    const tempoExploreNodeGraphUrl = viteStr(import.meta.env.VITE_GRAFANA_TEMPO_EXPLORE_NODE_GRAPH_URL);
    const tempoExploreDrilldownSynced = useMemo(
        () =>
            tempoExploreDrilldownUrl
                ? mergeGrafanaExploreTimeRange(tempoExploreDrilldownUrl, grafanaRange.from, grafanaRange.to)
                : undefined,
        [grafanaRange.from, grafanaRange.to, tempoExploreDrilldownUrl]
    );
    const tempoExploreNodeGraphSynced = useMemo(
        () =>
            tempoExploreNodeGraphUrl
                ? mergeGrafanaExploreTimeRange(tempoExploreNodeGraphUrl, grafanaRange.from, grafanaRange.to)
                : undefined,
        [grafanaRange.from, grafanaRange.to, tempoExploreNodeGraphUrl]
    );

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
                    auditFrom,
                    auditTo,
                    smartQ,
                    userId,
                    username,
                    actionType,
                    selectedServices,
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
        [
            actionType,
            auditFrom,
            auditTab,
            auditTo,
            correlationId,
            levelSet,
            selectedServices,
            size,
            smartQ,
            t,
            techQ,
            traceId,
            userId,
            username,
        ]
    );

    useEffect(() => {
        void fetchPage(page);
    }, [fetchPage, page]);

    const runSearch = () => {
        flushSync(() => {
            const p = parseSmartBar(smartQ);
            setSmartQ(p.remainder);
            if (p.username !== undefined) setUsername(p.username);
            if (p.userId !== undefined) setUserId(p.userId);
            if (p.traceId !== undefined) setTraceId(p.traceId);
            if (p.correlationId !== undefined) setCorrelationId(p.correlationId);
            if (p.services !== undefined) setSelectedServices(new Set(p.services));
        });
        setGrafanaReloadNonce((n) => n + 1);
        if (page === 0) void fetchPage(0);
        else setPage(0);
    };

    const toggleLevel = (lv: string) => {
        setLevelSet((prev) => {
            const next = new Set(prev);
            if (next.has(lv)) next.delete(lv);
            else next.add(lv);
            if (next.size === 0) next.add('ERROR');
            return next;
        });
    };

    const toggleServiceTag = (svc: string) => {
        setSelectedServices((prev) => {
            const n = new Set(prev);
            if (n.has(svc)) n.delete(svc);
            else n.add(svc);
            return n;
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
                @keyframes audit-grafana-shimmer {
                    0% { background-position: 200% 0; }
                    100% { background-position: -200% 0; }
                }
                .audit-grafana-skeleton {
                    position: absolute;
                    inset: 0;
                    z-index: 1;
                    background: linear-gradient(
                        90deg,
                        ${SKELETON_LO} 0%,
                        ${SKELETON_HI} 38%,
                        #243047 50%,
                        ${SKELETON_HI} 62%,
                        ${SKELETON_LO} 100%
                    );
                    background-size: 220% 100%;
                    animation: audit-grafana-shimmer 1.25s ease-in-out infinite;
                    pointer-events: none;
                }
                .audit-grafana-card:hover .audit-grafana-card-tools {
                    opacity: 1;
                }
                .audit-grafana-card-tools {
                    opacity: 0;
                    transition: opacity 0.18s ease;
                    display: flex;
                    gap: 6px;
                    align-items: flex-start;
                }
                .audit-grafana-grid {
                    display: grid;
                    grid-template-columns: repeat(3, 1fr);
                    gap: 14px;
                }
                @media (max-width: 1100px) {
                    .audit-grafana-grid {
                        grid-template-columns: repeat(2, 1fr);
                    }
                }
                @media (max-width: 640px) {
                    .audit-grafana-grid {
                        grid-template-columns: 1fr;
                    }
                }
                .audit-discovery-input {
                    box-sizing: border-box;
                    border-radius: 10px;
                    border: 1px solid rgba(184, 193, 204, 0.35);
                    background: rgba(22, 32, 50, 0.65);
                    color: #e2e8f0;
                    font-size: 14px;
                    transition: border-color 0.15s ease, box-shadow 0.15s ease;
                }
                .audit-discovery-input::placeholder {
                    color: rgba(184, 193, 204, 0.55);
                }
                .audit-discovery-input:focus {
                    outline: none;
                    border-color: rgba(56, 189, 248, 0.65);
                    box-shadow:
                        0 0 0 2px rgba(37, 99, 235, 0.28),
                        0 0 18px rgba(56, 189, 248, 0.28);
                }
            `}</style>
            <div>
                <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: 4 }}>{t('admin.auditCenterTitle', 'Audit Center')}</h1>
                <p style={{ color: tokens.textMuted, fontSize: '0.875rem' }}>{t('admin.auditCenterSubtitle', '')}</p>
            </div>

            {(grafanaAuditPanels || tempoExploreDrilldownUrl || tempoExploreNodeGraphUrl) && (
                <>
                    {/*
                      Panel IDs: Grafana → panel title → Inspect → Panel JSON → "id" must match VITE_GRAFANA_AUDIT_PANEL_* env vars.
                      Solo base: share dashboard → embed → copy the /d-solo/... path (uid, not necessarily the short slug).
                      Opsiyonel p95: VITE_GRAFANA_AUDIT_PANEL_P95_RESPONSE. Tempo: VITE_GRAFANA_AUDIT_PANEL_TEMPO_*.
                    */}
                    <div>
                        <h2 style={{ fontSize: '0.95rem', fontWeight: 600, margin: '0 0 10px', color: tokens.text }}>
                            {t('admin.auditObservabilityTitle', 'Live metrics')}
                        </h2>
                        {grafanaSoloBase && (
                            <p style={{ fontSize: 11, color: tokens.textMuted, margin: '0 0 8px', lineHeight: 1.45 }}>
                                {t('admin.auditGrafanaEmbedStatus', 'Grafana iframe panels')}: {grafanaAuditPanels?.length ?? 0}
                                {tempoExploreDrilldownUrl || tempoExploreNodeGraphUrl
                                    ? ` · ${t('admin.auditGrafanaExploreLinks', 'Explore links')}: ${[tempoExploreDrilldownUrl, tempoExploreNodeGraphUrl].filter(Boolean).length}`
                                    : ''}
                                {import.meta.env.DEV ? ' · dev' : ''}
                                {!viteStr(import.meta.env.VITE_GRAFANA_AUDIT_PANEL_P95_RESPONSE) ? ` · ${t('admin.auditGrafanaMissingP95', 'p95 id missing at build')}` : ''}
                                {!viteStr(import.meta.env.VITE_GRAFANA_AUDIT_PANEL_TEMPO_SPAN_RATE)
                                    ? ` · ${t('admin.auditGrafanaMissingTempoSpan', 'Tempo span id missing at build')}`
                                    : ''}
                                {!viteStr(import.meta.env.VITE_GRAFANA_AUDIT_PANEL_TEMPO_NODE_GRAPH)
                                    ? ` · ${t('admin.auditGrafanaMissingTempoNode', 'Tempo node id missing at build')}`
                                    : ''}
                            </p>
                        )}
                        {grafanaAuditPanels && (
                            <div className="audit-grafana-grid">
                                {grafanaAuditPanels.map((p) => {
                                    const iframeInnerH = AUDIT_GRAFANA_EMBED_HEIGHT_PX + AUDIT_GRAFANA_CLIP_TOP_PX;
                                    const loaded = !!grafanaIframeLoaded[p.id];
                                    const toolBtn: React.CSSProperties = {
                                        display: 'inline-flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        width: 30,
                                        height: 30,
                                        borderRadius: 8,
                                        border: `1px solid rgba(184, 193, 204, 0.4)`,
                                        background: 'rgba(15, 23, 42, 0.82)',
                                        color: SILVER_MUTED,
                                        cursor: 'pointer',
                                        padding: 0,
                                    };
                                    return (
                                        <div
                                            key={`${p.id}-${p.iframeTitle}`}
                                            className="audit-grafana-card"
                                            style={{
                                                position: 'relative',
                                                background: 'transparent',
                                                border: `1px solid rgba(184, 193, 204, 0.28)`,
                                                borderRadius: 12,
                                                boxShadow: `
                                                    0 0 0 1px rgba(59, 130, 246, 0.12),
                                                    0 4px 24px rgba(37, 99, 235, 0.14),
                                                    0 0 32px rgba(226, 232, 240, 0.06)
                                                `,
                                                overflow: 'hidden',
                                                minWidth: 0,
                                                isolation: 'isolate',
                                            }}
                                        >
                                            <div
                                                style={{
                                                    position: 'absolute',
                                                    top: 0,
                                                    left: 0,
                                                    right: 0,
                                                    zIndex: 3,
                                                    padding: '8px 10px 10px 12px',
                                                    display: 'flex',
                                                    alignItems: 'flex-start',
                                                    justifyContent: 'space-between',
                                                    gap: 8,
                                                    pointerEvents: 'none',
                                                    background: `linear-gradient(180deg, rgba(15, 23, 42, 0.9) 0%, rgba(15, 23, 42, 0.5) 55%, transparent 100%)`,
                                                    borderBottom: `1px solid rgba(59, 130, 246, 0.15)`,
                                                }}
                                            >
                                                <div
                                                    aria-hidden
                                                    style={{
                                                        fontSize: 11,
                                                        fontWeight: 700,
                                                        textTransform: 'uppercase',
                                                        letterSpacing: '0.06em',
                                                        color: tokens.text,
                                                        textShadow: '0 1px 2px rgba(0,0,0,0.85)',
                                                        lineHeight: 1.3,
                                                        paddingTop: 2,
                                                        minWidth: 0,
                                                    }}
                                                >
                                                    {p.title}
                                                </div>
                                                <div className="audit-grafana-card-tools" style={{ pointerEvents: 'auto', flexShrink: 0 }}>
                                                    <button
                                                        type="button"
                                                        title={t('admin.auditGrafanaEnlarge', 'Enlarge')}
                                                        aria-label={t('admin.auditGrafanaEnlarge', 'Enlarge')}
                                                        onClick={() => setGrafanaModal({ src: p.src, title: p.title })}
                                                        style={toolBtn}
                                                    >
                                                        <Maximize2 size={15} strokeWidth={2} />
                                                    </button>
                                                    <button
                                                        type="button"
                                                        title={t('admin.auditGrafanaOpenDashboard', 'Open in Grafana')}
                                                        aria-label={t('admin.auditGrafanaOpenDashboard', 'Open in Grafana')}
                                                        onClick={() =>
                                                            window.open(p.externalDashboardUrl, '_blank', 'noopener,noreferrer')
                                                        }
                                                        style={toolBtn}
                                                    >
                                                        <ExternalLink size={15} strokeWidth={2} />
                                                    </button>
                                                </div>
                                            </div>
                                            {/*
                                              Üst Grafana panel başlığı cross-origin; translateY + overflow ile kırpılır.
                                              Kart gövdesindeki iframe: görüntüleme — araç çubuğu üstte.
                                            */}
                                            <div
                                                style={{
                                                    position: 'relative',
                                                    height: AUDIT_GRAFANA_EMBED_HEIGHT_PX,
                                                    overflow: 'hidden',
                                                    marginTop: 0,
                                                }}
                                            >
                                                {!loaded ? <div className="audit-grafana-skeleton" aria-hidden /> : null}
                                                <iframe
                                                    title={p.iframeTitle}
                                                    src={p.src}
                                                    loading="lazy"
                                                    referrerPolicy="no-referrer-when-downgrade"
                                                    onLoad={() => setGrafanaIframeLoaded((prev) => ({ ...prev, [p.id]: true }))}
                                                    style={{
                                                        border: 'none',
                                                        width: '100%',
                                                        height: iframeInnerH,
                                                        display: 'block',
                                                        transform: `translateY(-${AUDIT_GRAFANA_CLIP_TOP_PX}px)`,
                                                        transformOrigin: 'top center',
                                                        opacity: loaded ? 1 : 0,
                                                        transition: 'opacity 0.35s ease',
                                                        pointerEvents: 'none',
                                                    }}
                                                />
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                        {grafanaModal ? (
                            <div
                                role="dialog"
                                aria-modal="true"
                                aria-label={grafanaModal.title}
                                style={{
                                    position: 'fixed',
                                    inset: 0,
                                    zIndex: 10000,
                                    background: 'rgba(2, 6, 23, 0.72)',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    padding: 24,
                                }}
                                onClick={() => setGrafanaModal(null)}
                                onKeyDown={(e) => e.key === 'Escape' && setGrafanaModal(null)}
                            >
                                <div
                                    style={{
                                        position: 'relative',
                                        width: 'min(1100px, 96vw)',
                                        maxHeight: '90vh',
                                        borderRadius: 14,
                                        border: `1px solid rgba(184, 193, 204, 0.35)`,
                                        boxShadow: `
                                            0 0 0 1px rgba(59, 130, 246, 0.2),
                                            0 24px 64px rgba(0, 0, 0, 0.55),
                                            0 0 48px rgba(59, 130, 246, 0.12)
                                        `,
                                        background: 'rgba(15, 23, 42, 0.95)',
                                        overflow: 'hidden',
                                        display: 'flex',
                                        flexDirection: 'column',
                                    }}
                                    onClick={(e) => e.stopPropagation()}
                                >
                                    <div
                                        style={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'space-between',
                                            padding: '12px 14px',
                                            borderBottom: `1px solid rgba(184, 193, 204, 0.2)`,
                                            color: SILVER_MUTED,
                                            fontSize: 13,
                                            fontWeight: 600,
                                        }}
                                    >
                                        <span style={{ color: tokens.text }}>{grafanaModal.title}</span>
                                        <button
                                            type="button"
                                            aria-label={t('admin.auditGrafanaModalClose', 'Close')}
                                            onClick={() => setGrafanaModal(null)}
                                            style={{
                                                display: 'inline-flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                width: 36,
                                                height: 36,
                                                borderRadius: 8,
                                                border: `1px solid rgba(184, 193, 204, 0.35)`,
                                                background: 'rgba(22, 32, 50, 0.9)',
                                                color: SILVER_MUTED,
                                                cursor: 'pointer',
                                            }}
                                        >
                                            <X size={18} />
                                        </button>
                                    </div>
                                    <div style={{ padding: 12, flex: 1, minHeight: 0 }}>
                                        {(() => {
                                            const modalH = 480;
                                            const modalInner = modalH + AUDIT_GRAFANA_CLIP_TOP_PX;
                                            return (
                                                <div
                                                    style={{
                                                        position: 'relative',
                                                        height: modalH,
                                                        overflow: 'hidden',
                                                        borderRadius: 10,
                                                        border: `1px solid rgba(184, 193, 204, 0.15)`,
                                                    }}
                                                >
                                                    <iframe
                                                        title={grafanaModal.title}
                                                        src={grafanaModal.src}
                                                        referrerPolicy="no-referrer-when-downgrade"
                                                        style={{
                                                            border: 'none',
                                                            width: '100%',
                                                            height: modalInner,
                                                            display: 'block',
                                                            transform: `translateY(-${AUDIT_GRAFANA_CLIP_TOP_PX}px)`,
                                                            transformOrigin: 'top center',
                                                            background: SKELETON_LO,
                                                        }}
                                                    />
                                                </div>
                                            );
                                        })()}
                                    </div>
                                </div>
                            </div>
                        ) : null}
                        {(tempoExploreDrilldownUrl || tempoExploreNodeGraphUrl) && (
                            <div
                                style={{
                                    marginTop: 12,
                                    display: 'flex',
                                    flexWrap: 'wrap',
                                    gap: 10,
                                    alignItems: 'center',
                                }}
                            >
                                <span style={{ fontSize: 12, color: tokens.textMuted }}>
                                    {t('admin.auditGrafanaTempoLinksLead', 'Tam etkileşimli Tempo (Grafana’da yeni sekme):')}
                                </span>
                                {tempoExploreDrilldownSynced ? (
                                    <a
                                        href={tempoExploreDrilldownSynced}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        style={{
                                            display: 'inline-flex',
                                            alignItems: 'center',
                                            gap: 6,
                                            padding: '8px 14px',
                                            borderRadius: 8,
                                            background: 'linear-gradient(90deg,#0ea5e9,#2563eb)',
                                            color: '#fff',
                                            fontWeight: 600,
                                            fontSize: 13,
                                            textDecoration: 'none',
                                        }}
                                    >
                                        {t('admin.auditGrafanaOpenTempoDrilldown', 'Traces Drilldown')}
                                    </a>
                                ) : null}
                                {tempoExploreNodeGraphSynced ? (
                                    <a
                                        href={tempoExploreNodeGraphSynced}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        style={{
                                            display: 'inline-flex',
                                            alignItems: 'center',
                                            gap: 6,
                                            padding: '8px 14px',
                                            borderRadius: 8,
                                            border: `1px solid ${INPUT_BORDER}`,
                                            background: INPUT_BG,
                                            color: tokens.text,
                                            fontWeight: 600,
                                            fontSize: 13,
                                            textDecoration: 'none',
                                        }}
                                    >
                                        {t('admin.auditGrafanaOpenTempoNodeGraph', 'Node graph / servis haritası')}
                                    </a>
                                ) : null}
                            </div>
                        )}
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

            <div
                style={{
                    background: 'rgba(16, 24, 39, 0.8)',
                    backdropFilter: 'blur(12px)',
                    WebkitBackdropFilter: 'blur(12px)',
                    border: '1px solid rgba(184, 193, 204, 0.18)',
                    borderBottom: '2px solid rgba(184, 193, 204, 0.24)',
                    borderRadius: 14,
                    padding: '18px 20px',
                    marginBottom: 10,
                }}
            >
                <div style={{ display: 'flex', gap: 12, alignItems: 'stretch', flexWrap: 'wrap', marginBottom: 8 }}>
                    <input
                        type="search"
                        className="audit-discovery-input"
                        value={smartQ}
                        onChange={(e) => setSmartQ(e.target.value)}
                        placeholder={t(
                            'admin.auditDiscoverySearchPh',
                            'Ara… İpucu: user:admin1 service:finance-service trace:abc'
                        )}
                        onKeyDown={(e) => e.key === 'Enter' && runSearch()}
                        style={{
                            flex: 1,
                            minWidth: 260,
                            padding: '14px 18px',
                        }}
                    />
                    <button type="button" onClick={runSearch} style={btnPrimaryLarge()}>
                        <Search size={18} style={{ marginRight: 8 }} />
                        {t('admin.auditSearch', 'Search')}
                    </button>
                </div>
                <p style={{ margin: '0 0 14px', fontSize: 11, color: SILVER_MUTED, opacity: 0.88, lineHeight: 1.45 }}>
                    {t(
                        'admin.auditDiscoverySmartHint',
                        'Enter ile arama: user:, userid:, service:, trace:, correlation: anahtarları ilgili filtreleri doldurur; kalan metin mesaj/logger aramasına gider.'
                    )}
                </p>

                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 16 }}>
                    {(
                        [
                            {
                                key: 'errors',
                                label: t('admin.auditChipErrorsOnly', 'Sadece hatalar'),
                                onClick: () => setAuditTab('critical'),
                            },
                            {
                                key: 'whale',
                                label: t('admin.auditChipWhale', 'Whale hareketleri'),
                                onClick: () => setAuditTab('whale'),
                            },
                            {
                                key: 'balance',
                                label: t('admin.auditChipBalance', 'Bakiye / yetersiz bakiye'),
                                onClick: () => {
                                    setAuditTab('system');
                                    setTechQ('insufficient balance bakiye yetersiz');
                                },
                            },
                            {
                                key: '15m',
                                label: t('admin.auditChip15m', 'Son 15 dk'),
                                onClick: () => {
                                    setTimePreset('15m');
                                    setGrafanaReloadNonce((n) => n + 1);
                                },
                            },
                        ] as const
                    ).map((chip) => (
                        <button
                            key={chip.key}
                            type="button"
                            onClick={chip.onClick}
                            style={{
                                padding: '6px 12px',
                                borderRadius: 999,
                                border: '1px solid rgba(184, 193, 204, 0.35)',
                                background: 'rgba(22, 32, 50, 0.55)',
                                color: SILVER_MUTED,
                                fontSize: 12,
                                fontWeight: 600,
                                cursor: 'pointer',
                            }}
                        >
                            {chip.label}
                        </button>
                    ))}
                </div>

                <div style={{ marginBottom: 14 }}>
                    <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: '0.04em', color: SILVER_MUTED, marginBottom: 8 }}>
                        {t('admin.auditDiscoveryTimeLabel', 'Zaman aralığı')}
                    </div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
                        {(
                            [
                                { k: '15m' as TimePreset, lab: t('admin.auditTime15m', 'Son 15 dakika') },
                                { k: '1h' as TimePreset, lab: t('admin.auditTime1h', 'Son 1 saat') },
                                { k: 'today' as TimePreset, lab: t('admin.auditTimeToday', 'Bugün') },
                                { k: 'yesterday' as TimePreset, lab: t('admin.auditTimeYesterday', 'Dün') },
                            ] as const
                        ).map(({ k, lab }) => (
                            <button
                                key={k}
                                type="button"
                                onClick={() => {
                                    setTimePreset(k);
                                    setGrafanaReloadNonce((n) => n + 1);
                                }}
                                style={{
                                    padding: '8px 14px',
                                    borderRadius: 10,
                                    border:
                                        timePreset === k
                                            ? `1px solid rgba(56, 189, 248, 0.65)`
                                            : '1px solid rgba(184, 193, 204, 0.3)',
                                    background:
                                        timePreset === k ? 'rgba(37, 99, 235, 0.22)' : 'rgba(22, 32, 50, 0.5)',
                                    color: timePreset === k ? tokens.text : SILVER_MUTED,
                                    fontSize: 12,
                                    fontWeight: timePreset === k ? 700 : 500,
                                    cursor: 'pointer',
                                }}
                            >
                                {lab}
                            </button>
                        ))}
                        <button
                            type="button"
                            onClick={() => {
                                setTimePreset('custom');
                                if (!customFrom) setCustomFrom(toIsoInput(new Date(Date.now() - ONE_H_MS)));
                                if (!customTo) setCustomTo(toIsoInput(new Date()));
                                setGrafanaReloadNonce((n) => n + 1);
                            }}
                            style={{
                                padding: '8px 14px',
                                borderRadius: 10,
                                border:
                                    timePreset === 'custom'
                                        ? `1px solid rgba(56, 189, 248, 0.65)`
                                        : '1px solid rgba(184, 193, 204, 0.3)',
                                background:
                                    timePreset === 'custom' ? 'rgba(37, 99, 235, 0.22)' : 'rgba(22, 32, 50, 0.5)',
                                color: timePreset === 'custom' ? tokens.text : SILVER_MUTED,
                                fontSize: 12,
                                fontWeight: timePreset === 'custom' ? 700 : 500,
                                cursor: 'pointer',
                            }}
                        >
                            {t('admin.auditTimeCustom', 'Özel aralık…')}
                        </button>
                    </div>
                    {timePreset === 'custom' && (
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 14, marginTop: 12 }}>
                            <label style={{ ...labelCompact, color: SILVER_MUTED }}>
                                {t('admin.auditDiscoveryCustomFrom', 'Başlangıç')}
                                <input
                                    type="datetime-local"
                                    className="audit-discovery-input"
                                    value={customFrom}
                                    onChange={(e) => {
                                        setCustomFrom(e.target.value);
                                        setGrafanaReloadNonce((n) => n + 1);
                                    }}
                                    style={{ ...filterInput(tokens), marginTop: 6, minWidth: 200 }}
                                />
                            </label>
                            <label style={{ ...labelCompact, color: SILVER_MUTED }}>
                                {t('admin.auditDiscoveryCustomTo', 'Bitiş')}
                                <input
                                    type="datetime-local"
                                    className="audit-discovery-input"
                                    value={customTo}
                                    onChange={(e) => {
                                        setCustomTo(e.target.value);
                                        setGrafanaReloadNonce((n) => n + 1);
                                    }}
                                    style={{ ...filterInput(tokens), marginTop: 6, minWidth: 200 }}
                                />
                            </label>
                        </div>
                    )}
                </div>

                {(auditTab === 'system' || auditTab === 'critical') && (
                    <div style={{ marginBottom: 14 }}>
                        <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: '0.04em', color: SILVER_MUTED, marginBottom: 8 }}>
                            {t('admin.auditDiscoveryServices', 'Hizmet seçimi')}
                        </div>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                            {AUDIT_SERVICE_TAGS.map((svc) => {
                                const on = selectedServices.has(svc);
                                return (
                                    <button
                                        key={svc}
                                        type="button"
                                        onClick={() => toggleServiceTag(svc)}
                                        style={{
                                            padding: '6px 12px',
                                            borderRadius: 999,
                                            border: `1px solid ${on ? 'rgba(56, 189, 248, 0.55)' : 'rgba(184, 193, 204, 0.32)'}`,
                                            background: on ? 'rgba(37, 99, 235, 0.25)' : 'rgba(22, 32, 50, 0.45)',
                                            color: on ? tokens.text : SILVER_MUTED,
                                            fontSize: 11,
                                            fontWeight: on ? 700 : 500,
                                            cursor: 'pointer',
                                            fontFamily: 'ui-monospace, monospace',
                                        }}
                                    >
                                        {svc}
                                    </button>
                                );
                            })}
                        </div>
                        <p style={{ margin: '6px 0 0', fontSize: 11, color: SILVER_MUTED, opacity: 0.8 }}>
                            {t('admin.auditDiscoveryServicesHint', 'Hiçbiri seçili değilse tüm hizmetler. Birden çok seçim: OR (sunucu virgüllü sorgu).')}
                        </p>
                    </div>
                )}

                {auditTab === 'system' && (
                    <div style={{ marginBottom: 14 }}>
                        <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: '0.04em', color: SILVER_MUTED, marginBottom: 8 }}>
                            {t('admin.auditDiscoveryLevels', 'Kayıt seviyesi')}
                        </div>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
                            {DISCOVERY_LEVELS.map((lv) => {
                                const on = levelSet.has(lv);
                                const err = lv === 'ERROR';
                                return (
                                    <button
                                        key={lv}
                                        type="button"
                                        onClick={() => toggleLevel(lv)}
                                        style={{
                                            padding: '10px 20px',
                                            borderRadius: 11,
                                            border: `1px solid ${
                                                on
                                                    ? err
                                                        ? 'rgba(248, 113, 113, 0.65)'
                                                        : 'rgba(251, 191, 36, 0.65)'
                                                    : 'rgba(184, 193, 204, 0.35)'
                                            }`,
                                            background: on
                                                ? err
                                                    ? 'rgba(127, 29, 29, 0.35)'
                                                    : 'rgba(120, 53, 15, 0.35)'
                                                : 'rgba(22, 32, 50, 0.45)',
                                            color: on ? (err ? '#fecaca' : '#fde68a') : SILVER_MUTED,
                                            fontSize: 12,
                                            fontWeight: 800,
                                            letterSpacing: '0.06em',
                                            cursor: 'pointer',
                                            boxShadow: on
                                                ? err
                                                    ? '0 0 16px rgba(239, 68, 68, 0.35)'
                                                    : '0 0 16px rgba(245, 158, 11, 0.32)'
                                                : 'none',
                                        }}
                                    >
                                        {lv}
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                )}

                {auditTab === 'critical' && (
                    <p style={{ margin: '0 0 12px', fontSize: 12, color: SILVER_MUTED }}>{t('admin.auditCriticalHint', 'Only ERROR level logs.')}</p>
                )}

                {auditTab === 'whale' && (
                    <p style={{ margin: '0 0 12px', fontSize: 12, color: SILVER_MUTED }}>
                        {t('admin.auditWhaleHint', 'Filtered to whale-analytics-service + whale keywords.')}
                    </p>
                )}

                <details
                    open={advancedFiltersOpen}
                    onToggle={(e) => setAdvancedFiltersOpen((e.target as HTMLDetailsElement).open)}
                    style={{ marginTop: 4 }}
                >
                    <summary
                        style={{
                            cursor: 'pointer',
                            fontSize: 12,
                            fontWeight: 700,
                            color: SILVER_MUTED,
                            listStyle: 'none',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 8,
                        }}
                    >
                        <Filter size={14} />
                        {t('admin.auditDiscoveryAdvanced', 'Gelişmiş filtreler')}
                        {advancedFiltersOpen ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                    </summary>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, marginTop: 14, paddingTop: 14, borderTop: '1px solid rgba(184, 193, 204, 0.15)' }}>
                        {auditTab === 'user' && (
                            <div>
                                <div style={{ fontSize: 11, fontWeight: 700, color: SILVER_MUTED, marginBottom: 8 }}>
                                    {t('admin.auditGroupIdentity', 'User')}
                                </div>
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
                                    <label style={{ ...labelCompact, color: SILVER_MUTED }}>
                                        {t('admin.auditUserId', 'User ID')}
                                        <input
                                            value={userId}
                                            onChange={(e) => setUserId(e.target.value)}
                                            placeholder="DB id"
                                            className="audit-discovery-input"
                                            style={{ ...filterInput(tokens), marginTop: 6 }}
                                        />
                                    </label>
                                    <label style={{ ...labelCompact, color: SILVER_MUTED }}>
                                        {t('admin.auditUsername', 'Username')}
                                        <input
                                            value={username}
                                            onChange={(e) => setUsername(e.target.value)}
                                            placeholder={t('admin.auditUsernamePh', 'Keycloak preferred_username')}
                                            className="audit-discovery-input"
                                            style={{ ...filterInput(tokens), marginTop: 6 }}
                                        />
                                    </label>
                                    <label style={{ ...labelCompact, color: SILVER_MUTED }}>
                                        {t('admin.auditActionType', 'Action')}
                                        <select
                                            value={actionType}
                                            onChange={(e) => setActionType(e.target.value)}
                                            className="audit-discovery-input"
                                            style={{ ...filterInput(tokens), marginTop: 6 }}
                                        >
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
                                    <div style={{ fontSize: 11, fontWeight: 700, color: SILVER_MUTED, marginBottom: 8 }}>
                                        {t('admin.auditGroupByUser', 'Kullanıcıya göre (opsiyonel)')}
                                    </div>
                                    <label style={{ ...labelCompact, color: SILVER_MUTED, display: 'block', maxWidth: 400 }}>
                                        {t('admin.auditUsername', 'Username')}
                                        <input
                                            value={username}
                                            onChange={(e) => setUsername(e.target.value)}
                                            placeholder={t('admin.auditUsernamePh', 'Keycloak preferred_username')}
                                            className="audit-discovery-input"
                                            style={{ ...filterInput(tokens), marginTop: 6, width: '100%' }}
                                        />
                                    </label>
                                    <p style={{ margin: '8px 0 0', fontSize: 11, color: SILVER_MUTED, opacity: 0.85, maxWidth: 520 }}>
                                        {t(
                                            'admin.auditUsernameSystemHint',
                                            'Sistem loglarında userId/username alanı dolu olan kayıtları süzer. Trace ID yerine çoğu senaryoda bunu kullanın.'
                                        )}
                                    </p>
                                </div>
                                <div>
                                    <div style={{ fontSize: 11, fontWeight: 700, color: SILVER_MUTED, marginBottom: 8 }}>
                                        {t('admin.auditAdvancedTrace', 'İleri düzey: traceId / correlationId')}
                                    </div>
                                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
                                        <label style={{ ...labelCompact, color: SILVER_MUTED }}>
                                            traceId
                                            <input
                                                value={traceId}
                                                onChange={(e) => setTraceId(e.target.value)}
                                                className="audit-discovery-input"
                                                style={{ ...filterMono(tokens), marginTop: 6 }}
                                            />
                                        </label>
                                        <label style={{ ...labelCompact, color: SILVER_MUTED }}>
                                            correlationId
                                            <input
                                                value={correlationId}
                                                onChange={(e) => setCorrelationId(e.target.value)}
                                                className="audit-discovery-input"
                                                style={{ ...filterMono(tokens), marginTop: 6 }}
                                            />
                                        </label>
                                    </div>
                                </div>
                                <label style={{ ...labelCompact, color: SILVER_MUTED, display: 'block', maxWidth: 520 }}>
                                    {t('admin.auditTechSearch', 'Extra keywords')}
                                    <input
                                        value={techQ}
                                        onChange={(e) => setTechQ(e.target.value)}
                                        placeholder={t('admin.auditTechSearchPh', 'message / logger içinde kelimeler')}
                                        className="audit-discovery-input"
                                        style={{ ...filterInput(tokens), marginTop: 6, width: '100%' }}
                                    />
                                </label>
                            </>
                        )}
                    </div>
                </details>
            </div>

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
