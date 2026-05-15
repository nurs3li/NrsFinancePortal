import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { financeClient, readFinanceBinaryErrorMessage } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';

/** İş Yatırım USD/ons kıymetli maden — dashboard sparkline için tam kapsama. */
const ISYATIRIM_METAL_USD_OZ_SYMBOLS = 'XAU_USD_OZ,XAG_USD_OZ,XPT_USD_OZ,XPD_USD_OZ';

const MARKET_BACKFILL_HTTP_TIMEOUT_MS = 900_000;

function formatIstanbulDateOnly(d: Date): string {
    return d.toLocaleDateString('en-CA', { timeZone: 'Europe/Istanbul' });
}

export function AdminDashboard() {
    const { tokens } = useTheme();
    const { t } = useLanguage();
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

    return (
        <div style={pageStyle}>
            <h1 style={titleStyle}>{t('nav.admin', 'Yönetim Paneli')}</h1>
            <p style={{ ...mutedStyle, marginBottom: 18 }}>{t('admin.dashboardSubtitle', 'Back-office operasyonlarının canlı özeti ve hızlı aksiyon alanı.')}</p>

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
