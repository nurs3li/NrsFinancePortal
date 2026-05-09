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
    readonly VITE_GRAFANA_AUDIT_PANEL_SERVICE_HEALTH?: string;
    /** Use `{traceId}` placeholder; replaced with encodeURIComponent(traceId). */
    readonly VITE_GRAFANA_SERVICE_MAP_TRACE_URL_TEMPLATE?: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}
