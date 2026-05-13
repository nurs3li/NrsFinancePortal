import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { financeClient } from '../api/client';
import { useTheme } from '../theme/ThemeContext';
import { useLanguage } from '../i18n/LanguageContext';
import './Transactions.css';

type SnapshotRow = {
    id: number;
    snapshotAt: string;
    triggerType: string;
    portfolioValueTry?: number;
    portfolioCostTry?: number;
    portfolioPnlTry?: number;
};

function unwrapSnapshotList(payload: unknown): SnapshotRow[] {
    if (Array.isArray(payload)) return payload as SnapshotRow[];
    if (payload && typeof payload === 'object' && 'data' in payload && Array.isArray((payload as { data: unknown }).data)) {
        return (payload as { data: SnapshotRow[] }).data;
    }
    return [];
}

export function Transactions() {
    const { tokens } = useTheme();
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';
    const [rows, setRows] = useState<SnapshotRow[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(() => {
        setLoading(true);
        setError(null);
        const end = new Date();
        const start = new Date();
        start.setDate(start.getDate() - 365);
        financeClient
            .get('/api/portfolio/snapshots/me', {
                params: { from: start.toISOString(), to: end.toISOString() },
            })
            .then((res) => {
                const list = unwrapSnapshotList(res.data);
                setRows(
                    [...list].sort((a, b) => new Date(b.snapshotAt).getTime() - new Date(a.snapshotAt).getTime())
                );
            })
            .catch(() => setError(t('transactions.snapshotLoadFailed', 'Portföy anlıkları yüklenemedi.')))
            .finally(() => setLoading(false));
    }, [t]);

    useEffect(() => {
        load();
    }, [load]);

    const muted = { color: tokens.textMuted, fontSize: '0.9rem' };

    return (
        <div className="tx-page" style={{ background: tokens.bg, color: tokens.text }}>
            <div className="tx-container">
                <h1 className="tx-title">{t('transactions.snapshotTitle', 'Portföy anlıkları')}</h1>
                <p className="tx-subtitle" style={muted}>
                    {t(
                        'transactions.snapshotSubtitle',
                        'Operasyonel banka / kasa hareketleri kaldırıldı. Aşağıda kayıtlı portföy değeri anlıkları listelenir; güncel dağılım için Portföy sayfasını kullanın.',
                    )}
                </p>
                <p style={{ marginBottom: 20 }}>
                    <Link to="/portfolio" style={{ color: tokens.accent, fontWeight: 600 }}>
                        {t('transactions.goPortfolio', 'Portföy sayfasına git →')}
                    </Link>
                </p>

                {loading && <p style={muted}>{t('common.loading', 'Yükleniyor...')}</p>}
                {error && <p style={{ color: tokens.error }}>{error}</p>}

                {!loading && !error && rows.length === 0 && (
                    <p style={muted}>{t('transactions.snapshotEmpty', 'Henüz portföy anlığı yok.')}</p>
                )}

                {!loading && !error && rows.length > 0 && (
                    <div className="tx-table-scroll">
                        <table className="tx-table">
                            <thead>
                                <tr>
                                    <th>{t('transactions.snapshotTime', 'Zaman')}</th>
                                    <th>{t('transactions.snapshotTrigger', 'Tetik')}</th>
                                    <th className="tx-th-num">{t('transactions.snapshotValue', 'Portföy TRY')}</th>
                                    <th className="tx-th-num">{t('transactions.snapshotCost', 'Maliyet TRY')}</th>
                                    <th className="tx-th-num">{t('transactions.snapshotPnl', 'PNL TRY')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {rows.map((r) => (
                                    <tr key={r.id} className="tx-row">
                                        <td>{new Date(r.snapshotAt).toLocaleString(locale)}</td>
                                        <td>{r.triggerType}</td>
                                        <td style={{ textAlign: 'right' }}>
                                            {Number(r.portfolioValueTry ?? 0).toLocaleString(locale, { maximumFractionDigits: 2 })}
                                        </td>
                                        <td style={{ textAlign: 'right' }}>
                                            {Number(r.portfolioCostTry ?? 0).toLocaleString(locale, { maximumFractionDigits: 2 })}
                                        </td>
                                        <td style={{ textAlign: 'right' }}>
                                            {Number(r.portfolioPnlTry ?? 0).toLocaleString(locale, { maximumFractionDigits: 2 })}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
}
