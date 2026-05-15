import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { financeClient, metricsClient, readFinanceBinaryErrorMessage } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import { Activity, AlertTriangle, ShieldAlert } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

type TradeCountBySymbol = { symbol: string; count: number };
type WhaleCountByLevel = { level: string; count: number };
type DashboardMetricsDto = {
    totalTrades: number;
    totalWhaleAlerts: number;
    totalSuspiciousEvents: number;
    topSymbols: TradeCountBySymbol[];
    whaleByLevel: WhaleCountByLevel[];
    totalInvestorBehaviorEvents?: number;
    investorByLevel?: WhaleCountByLevel[];
    avgPortfolioImpactScore?: number;
    maxPortfolioImpactScore?: number;
    maxTotalPortfolioValueTry?: number;
};
type MetricsApiResponse = { success: boolean; data: DashboardMetricsDto | null; errors: unknown };

const COLORS = ['#64FFDA', '#22c55e', '#38bdf8', '#8892B0', '#eab308', '#f97316', '#a855f7', '#ef4444'];

/** İş Yatırım USD/ons kıymetli maden — dashboard sparkline için tam kapsama. */
const ISYATIRIM_METAL_USD_OZ_SYMBOLS = 'XAU_USD_OZ,XAG_USD_OZ,XPT_USD_OZ,XPD_USD_OZ';

const MARKET_BACKFILL_HTTP_TIMEOUT_MS = 900_000;

function formatIstanbulDateOnly(d: Date): string {
    return d.toLocaleDateString('en-CA', { timeZone: 'Europe/Istanbul' });
}

export function AdminDashboard() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const [metrics, setMetrics] = useState<DashboardMetricsDto | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [backfillBusy, setBackfillBusy] = useState<'bist' | 'metals' | null>(null);
    const [backfillResult, setBackfillResult] = useState<string | null>(null);
    const [backfillError, setBackfillError] = useState<string | null>(null);

    const runBistBackfill = useCallback(async () => {
        setBackfillBusy('bist');
        setBackfillError(null);
        setBackfillResult(null);
        try {
            const { data } = await financeClient.post<unknown>(
                '/api/admin/market/equities/bist/backfill',
                {},
                { timeout: MARKET_BACKFILL_HTTP_TIMEOUT_MS },
            );
            setBackfillResult(JSON.stringify(data, null, 2));
        } catch (e: unknown) {
            const msg =
                readFinanceBinaryErrorMessage(e) ??
                (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
                (e as Error)?.message ??
                'BIST backfill başarısız';
            setBackfillError(msg);
        } finally {
            setBackfillBusy(null);
        }
    }, []);

    const runMetalsIsyatirimBackfill = useCallback(async () => {
        setBackfillBusy('metals');
        setBackfillError(null);
        setBackfillResult(null);
        const now = new Date();
        const from = new Date(now);
        from.setFullYear(from.getFullYear() - 2);
        try {
            const { data } = await financeClient.post<unknown>(
                '/api/admin/market/metals/isyatirim/backfill',
                {},
                {
                    timeout: MARKET_BACKFILL_HTTP_TIMEOUT_MS,
                    params: {
                        symbols: ISYATIRIM_METAL_USD_OZ_SYMBOLS,
                        from: formatIstanbulDateOnly(from),
                        to: formatIstanbulDateOnly(now),
                        force: false,
                    },
                },
            );
            setBackfillResult(JSON.stringify(data, null, 2));
        } catch (e: unknown) {
            const msg =
                readFinanceBinaryErrorMessage(e) ??
                (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
                (e as Error)?.message ??
                'Kıymetli maden backfill başarısız';
            setBackfillError(msg);
        } finally {
            setBackfillBusy(null);
        }
    }, []);

    useEffect(() => {
        metricsClient
            .get<MetricsApiResponse>('/api/admin/metrics/dashboard')
            .then((metricsRes) => {
                const raw = metricsRes.data as DashboardMetricsDto | MetricsApiResponse | null | undefined;
                const m =
                    raw && typeof raw === 'object' && 'data' in raw
                        ? (raw as MetricsApiResponse).data
                        : (raw as DashboardMetricsDto | null | undefined);
                setMetrics(m ?? null);
            })
            .catch((err) =>
                setError(err.response?.data?.message ?? err.message ?? t('admin.dashboardLoadFailed', 'Yönetim paneli verisi yüklenemedi'))
            )
            .finally(() => setLoading(false));
    }, [t]);

    const pageStyle: React.CSSProperties = {
        padding: 24,
        background: tokens.bg,
        color: tokens.text,
        minHeight: '100%',
        fontFamily: 'Inter, Roboto, Arial, sans-serif',
    };
    const panelStyle: React.CSSProperties = {
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        borderRadius: 8,
        boxShadow: '0 8px 22px rgba(0,0,0,0.12)',
        padding: 16,
    };
    const titleStyle: React.CSSProperties = { fontSize: '1.6rem', fontWeight: 700, marginBottom: 4, color: tokens.text };
    const mutedStyle: React.CSSProperties = { color: tokens.textMuted, fontSize: '0.875rem' };
    const tooltipStyle = {
        background: tokens.bgCard,
        border: `1px solid ${tokens.border}`,
        borderRadius: 8,
        fontSize: 12,
        color: tokens.text,
    };

    const kpis = useMemo(
        () => [
            { label: t('admin.totalTrade', 'Toplam Trade'), value: metrics?.totalTrades ?? 0, Icon: Activity, color: '#64FFDA' },
            { label: t('admin.whaleAlerts', 'Whale Uyarıları'), value: metrics?.totalWhaleAlerts ?? 0, Icon: AlertTriangle, color: '#22c55e' },
            { label: t('admin.suspiciousEvents', 'Şüpheli Olaylar'), value: metrics?.totalSuspiciousEvents ?? 0, Icon: ShieldAlert, color: '#f59e0b' },
        ],
        [metrics, t]
    );

    if (loading) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>{t('nav.admin', 'Yönetim Paneli')}</h1>
                <p style={mutedStyle}>{t('common.loading', 'Yükleniyor...')}</p>
            </div>
        );
    }
    if (error) {
        return (
            <div style={pageStyle}>
                <h1 style={titleStyle}>{t('nav.admin', 'Yönetim Paneli')}</h1>
                <p style={{ ...mutedStyle, color: tokens.error }}>
                    {t('news.errorPrefix', 'Hata')}: {error}
                </p>
            </div>
        );
    }

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>{t('nav.admin', 'Yönetim Paneli')}</h1>
            <p style={{ ...mutedStyle, marginBottom: 10 }}>{t('admin.dashboardSubtitle', 'Back-office operasyonlarının canlı özeti ve hızlı aksiyon alanı.')}</p>
            <p style={{ ...mutedStyle, marginBottom: 18 }}>{t('admin.legacyMetricsHint')}</p>

            <div style={{ ...panelStyle, marginBottom: 16 }}>
                <h2 style={{ marginTop: 0, fontSize: '1rem', color: tokens.text }}>
                    {t('admin.marketBackfillDevTitle', 'Piyasa verisi (geçici — geliştirici)')}
                </h2>
                <p style={{ ...mutedStyle, marginBottom: 12 }}>
                    {t(
                        'admin.marketBackfillDevHint',
                        'Finance-service üzerinden market-data iç backfill tetiklenir (tarayıcı JWT ile 8083 issuer uyumsuzluğu olmaz). Keycloak’ta ADMIN veya OPS rolü; Docker’da finance ve market-data için aynı NRS_INTERNAL_BACKFILL_TOKEN gerekir. İşlem uzun sürebilir.',
                    )}
                </p>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
                    <button
                        type="button"
                        disabled={backfillBusy !== null}
                        onClick={runBistBackfill}
                        style={{
                            padding: '10px 16px',
                            borderRadius: 8,
                            border: `1px solid ${tokens.border}`,
                            background: tokens.bg,
                            color: tokens.text,
                            fontWeight: 600,
                            cursor: backfillBusy !== null ? 'not-allowed' : 'pointer',
                            opacity: backfillBusy !== null ? 0.65 : 1,
                        }}
                    >
                        {backfillBusy === 'bist'
                            ? t('admin.marketBackfillRunning', 'Çalışıyor…')
                            : t('admin.marketBackfillBist', 'BIST günlük backfill')}
                    </button>
                    <button
                        type="button"
                        disabled={backfillBusy !== null}
                        onClick={runMetalsIsyatirimBackfill}
                        style={{
                            padding: '10px 16px',
                            borderRadius: 8,
                            border: `1px solid ${tokens.border}`,
                            background: tokens.bg,
                            color: tokens.text,
                            fontWeight: 600,
                            cursor: backfillBusy !== null ? 'not-allowed' : 'pointer',
                            opacity: backfillBusy !== null ? 0.65 : 1,
                        }}
                    >
                        {backfillBusy === 'metals'
                            ? t('admin.marketBackfillRunning', 'Çalışıyor…')
                            : t('admin.marketBackfillMetals', 'İş Yatırım USD/ons maden (2Y)')}
                    </button>
                </div>
                {backfillError ? (
                    <pre
                        style={{
                            marginTop: 12,
                            padding: 12,
                            borderRadius: 8,
                            background: 'rgba(239,68,68,0.12)',
                            color: tokens.error,
                            fontSize: 12,
                            overflow: 'auto',
                            whiteSpace: 'pre-wrap',
                        }}
                    >
                        {backfillError}
                    </pre>
                ) : null}
                {backfillResult ? (
                    <pre
                        style={{
                            marginTop: 12,
                            padding: 12,
                            borderRadius: 8,
                            background: tokens.bg,
                            border: `1px solid ${tokens.border}`,
                            color: tokens.textMuted,
                            fontSize: 12,
                            overflow: 'auto',
                            maxHeight: 320,
                        }}
                    >
                        {backfillResult}
                    </pre>
                ) : null}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 12, marginBottom: 16 }}>
                {kpis.map((kpi) => (
                    <div key={kpi.label} style={panelStyle}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <span style={mutedStyle}>{kpi.label}</span>
                            <kpi.Icon size={16} color={kpi.color} />
                        </div>
                        <div style={{ fontSize: '1.7rem', marginTop: 8, fontWeight: 700, color: kpi.color }}>
                            {kpi.value.toLocaleString(lang === 'en' ? 'en-US' : 'tr-TR')}
                        </div>
                    </div>
                ))}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 }}>
                <div style={panelStyle}>
                    <h2 style={{ marginTop: 0, fontSize: '1rem', color: tokens.text }}>Sembol Dağılımı</h2>
                    <div style={{ width: '100%', height: 280 }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <PieChart>
                                <Pie
                                    data={metrics?.topSymbols ?? []}
                                    dataKey="count"
                                    nameKey="symbol"
                                    outerRadius="78%"
                                    label={({ name }) => name}
                                >
                                    {(metrics?.topSymbols ?? []).map((_, i) => (
                                        <Cell key={i} fill={COLORS[i % COLORS.length]} />
                                    ))}
                                </Pie>
                                <Tooltip contentStyle={tooltipStyle} />
                            </PieChart>
                        </ResponsiveContainer>
                    </div>
                    <div style={{ ...mutedStyle, fontSize: '0.75rem' }}>Metrikler Servisinden Canlı Veri</div>
                </div>

                <div style={panelStyle}>
                    <h2 style={{ marginTop: 0, fontSize: '1rem', color: tokens.text }}>En Çok İşlem Gören Semboller</h2>
                    <div style={{ width: '100%', height: 280 }}>
                        <ResponsiveContainer width="100%" height="100%">
                            <BarChart data={metrics?.topSymbols ?? []} layout="vertical" margin={{ top: 8, right: 16, left: 56, bottom: 8 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke={tokens.border} />
                                <XAxis type="number" tick={{ fill: tokens.textMuted, fontSize: 11 }} />
                                <YAxis type="category" dataKey="symbol" tick={{ fill: tokens.text, fontSize: 11 }} width={56} />
                                <Tooltip contentStyle={tooltipStyle} />
                                <Bar dataKey="count" fill="#64FFDA" radius={[0, 4, 4, 0]} />
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                    <div style={{ ...mutedStyle, fontSize: '0.75rem' }}>Metrikler Servisinden Canlı Veri</div>
                </div>
            </div>

            <div style={panelStyle}>
                <h2 style={{ marginTop: 0, fontSize: '1rem', color: tokens.text }}>Denetim ve güvenlik</h2>
                <p style={{ ...mutedStyle, marginBottom: 12 }}>{t('admin.auditReplacesSuspiciousPanel')}</p>
                <Link
                    to="/admin/audit"
                    style={{
                        display: 'inline-block',
                        padding: '10px 16px',
                        borderRadius: 8,
                        background: tokens.accentGradient ?? tokens.accent,
                        color: '#fff',
                        fontWeight: 600,
                        textDecoration: 'none',
                    }}
                >
                    {t('admin.auditLogsCta')}
                </Link>
            </div>

            {typeof import.meta.env.VITE_GRAFANA_DASHBOARD_EMBED_URL === 'string' &&
                import.meta.env.VITE_GRAFANA_DASHBOARD_EMBED_URL.trim() !== '' && (
                    <div style={{ ...panelStyle, marginTop: 16 }}>
                        <h2 style={{ marginTop: 0, fontSize: '1rem', color: tokens.text }}>
                            {t('admin.grafanaEmbedTitle', 'System health (Grafana)')}
                        </h2>
                        <iframe
                            title="Grafana"
                            src={import.meta.env.VITE_GRAFANA_DASHBOARD_EMBED_URL}
                            style={{ width: '100%', height: 420, border: 0, borderRadius: 8 }}
                            referrerPolicy="no-referrer-when-downgrade"
                        />
                    </div>
                )}
        </div>
    );
}
