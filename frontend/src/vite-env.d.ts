/// <reference types="vite/client" />

/**
 * Grafana embed / explore URLs — set in `.env` (see AdminAudit observability header).
 * Panel IDs must match Grafana → panel → Inspect → Panel JSON → "id".
 */
interface ImportMetaEnv {
    readonly VITE_GRAFANA_DASHBOARD_EMBED_URL?: string;
    readonly VITE_GRAFANA_SOLO_BASE?: string;
    readonly VITE_GRAFANA_ORG_ID?: string;
    readonly VITE_GRAFANA_AUDIT_PANEL_REQUEST_VOLUME?: string;
    readonly VITE_GRAFANA_AUDIT_PANEL_ERROR_RATE?: string;
    /** Opsiyonel: p95 / latency paneli (ör. histogram). */
    readonly VITE_GRAFANA_AUDIT_PANEL_P95_RESPONSE?: string;
    readonly VITE_GRAFANA_AUDIT_PANEL_SERVICE_HEALTH?: string;
    /** Aynı audit dashboard’unda Tempo TraceQL / drilldown panelinin JSON "id" değeri. */
    readonly VITE_GRAFANA_AUDIT_PANEL_TEMPO_SPAN_RATE?: string;
    /** Aynı dashboard’da Node graph / service map panel id. */
    readonly VITE_GRAFANA_AUDIT_PANEL_TEMPO_NODE_GRAPH?: string;
    /** Grafana’da kayıtlı tam URL (yeni sekme). Explore → Traces Drilldown sayfasından kopyalayın. */
    readonly VITE_GRAFANA_TEMPO_EXPLORE_DRILLDOWN_URL?: string;
    /** Node graph görünümünün tam Grafana URL’si (yeni sekme). */
    readonly VITE_GRAFANA_TEMPO_EXPLORE_NODE_GRAPH_URL?: string;
    /** Use `{traceId}` placeholder; replaced with encodeURIComponent(traceId). */
    readonly VITE_GRAFANA_SERVICE_MAP_TRACE_URL_TEMPLATE?: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}
