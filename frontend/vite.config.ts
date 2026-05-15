import path from 'path'
import { fileURLToPath } from 'url'
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

/** AdminAudit Grafana embed: bu anahtarlar `define` içinde her zaman bulunur (yoksa `""`). Böylece Vite `import.meta.env.VITE_*` ifadesini asla “boş runtime nesne”de bırakmaz. */
const GRAFANA_AUDIT_VITE_KEYS = [
    'VITE_GRAFANA_SOLO_BASE',
    'VITE_GRAFANA_ORG_ID',
    'VITE_GRAFANA_SERVICE_MAP_TRACE_URL_TEMPLATE',
    'VITE_GRAFANA_AUDIT_PANEL_REQUEST_VOLUME',
    'VITE_GRAFANA_AUDIT_PANEL_ERROR_RATE',
    'VITE_GRAFANA_AUDIT_PANEL_P95_RESPONSE',
    'VITE_GRAFANA_AUDIT_PANEL_SERVICE_HEALTH',
    'VITE_GRAFANA_AUDIT_PANEL_TEMPO_SPAN_RATE',
    'VITE_GRAFANA_AUDIT_PANEL_TEMPO_NODE_GRAPH',
    'VITE_GRAFANA_TEMPO_EXPLORE_DRILLDOWN_URL',
    'VITE_GRAFANA_TEMPO_EXPLORE_NODE_GRAPH_URL',
] as const

/**
 * `VITE_*` birleştirme: `process.env` → repo kökü `.env` → `frontend/.env` (son kazanır).
 * Docker’da kök `.env` yok; `process.env` = Dockerfile ENV / compose build-arg.
 */
export default defineConfig(({ mode }) => {
    const fe = __dirname
    const repoRoot = path.resolve(fe, '..')
    const docker = process.env.DOCKER_BUILD === '1'
    const fromRoot = docker ? {} : loadEnv(mode, repoRoot, 'VITE_')
    const fromFe = loadEnv(mode, fe, 'VITE_')
    const fromProcess: Record<string, string> = {}
    for (const [k, v] of Object.entries(process.env)) {
        if (k.startsWith('VITE_') && v !== undefined && v !== '') fromProcess[k] = v
    }
    const merged = { ...fromProcess, ...fromRoot, ...fromFe }
    const viteEnv: Record<string, string> = { ...merged }
    for (const k of GRAFANA_AUDIT_VITE_KEYS) {
        const raw = viteEnv[k]
        viteEnv[k] =
            raw !== undefined && raw !== null && String(raw).trim() !== '' ? String(raw).trim() : ''
    }
    const defineEnv = Object.fromEntries(
        Object.entries(viteEnv).map(([k, v]) => [`import.meta.env.${k}`, JSON.stringify(v ?? '')] as const)
    )

    return {
        plugins: [react()],
        test: {
            globals: true,
            environment: 'node',
            include: ['src/**/*.test.ts'],
        },
        envDir: fe,
        define: defineEnv,
        server: {
            /** Docker’da port publish için (tarayıcı host makineden bağlanır) */
            host: true,
            port: 5173,
            strictPort: true,
            watch:
                process.env.CHOKIDAR_USEPOLLING === 'true'
                    ? { usePolling: true, interval: 1000 }
                    : undefined,
        },
    }
})
