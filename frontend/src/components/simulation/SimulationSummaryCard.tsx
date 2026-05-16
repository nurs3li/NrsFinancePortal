import { useLanguage } from '../../i18n/LanguageContext';
import { formatSimMoney } from './simCurrency';
import type { SimDisplayCurrency, SimulationSummaryStats } from './types';
import { SimulationExportActions } from './SimulationExportActions';

type SimulationSummaryCardProps = {
    stats: SimulationSummaryStats;
    displayCurrency: SimDisplayCurrency;
    hasResults: boolean;
    mutedColor: string;
    onExportCsv: () => void;
    onExportPdf: () => void;
    onSaveSimulation: () => void;
    exportDisabled: boolean;
    saveFeedback?: string | null;
};

function fmtPct(locale: string, v: number) {
    const sign = v > 0 ? '+' : '';
    return `${sign}${v.toLocaleString(locale, { maximumFractionDigits: 2 })}%`;
}

export function SimulationSummaryCard({
    stats,
    displayCurrency,
    hasResults,
    mutedColor,
    onExportCsv,
    onExportPdf,
    onSaveSimulation,
    exportDisabled,
    saveFeedback,
}: SimulationSummaryCardProps) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    if (!hasResults) {
        return (
            <section className="card-premium sim-summary-card">
                <h2 className="sim-section-title">{t('simulation.summaryTitle', 'Sonuç Özeti')}</h2>
                <p className="sim-lead sim-summary-empty" style={{ color: mutedColor }}>
                    {t(
                        'simulation.summaryEmpty',
                        'Henüz simülasyon eklenmedi. Bir tutar, tarih ve varlık seçerek ilk senaryonu oluştur.',
                    )}
                </p>
            </section>
        );
    }

    const best = stats.best;
    const worst = stats.worst;

    return (
        <section className="card-premium sim-summary-card">
            <h2 className="sim-section-title">{t('simulation.summaryTitle', 'Sonuç Özeti')}</h2>
            <div className="sim-summary-grid">
                <div className="sim-summary-stat sim-summary-stat--best">
                    <span className="sim-summary-stat__label">{t('simulation.bestScenario', 'En iyi senaryo')}</span>
                    {best ? (
                        <>
                            <span className="sim-summary-stat__name">{best.assetName}</span>
                            <span className="sim-summary-stat__value sim-pnl-pos">
                                {best.pnl >= 0 ? '+' : ''}
                                {formatSimMoney(locale, best.pnl, displayCurrency)} · {fmtPct(locale, best.pnlPct)}
                            </span>
                        </>
                    ) : (
                        <span className="sim-summary-stat__muted">—</span>
                    )}
                </div>
                <div className="sim-summary-stat sim-summary-stat--worst">
                    <span className="sim-summary-stat__label">{t('simulation.worstScenario', 'En kötü senaryo')}</span>
                    {worst ? (
                        <>
                            <span className="sim-summary-stat__name">{worst.assetName}</span>
                            <span className="sim-summary-stat__value sim-pnl-neg">
                                {worst.pnl >= 0 ? '+' : ''}
                                {formatSimMoney(locale, worst.pnl, displayCurrency)} · {fmtPct(locale, worst.pnlPct)}
                            </span>
                        </>
                    ) : (
                        <span className="sim-summary-stat__muted">—</span>
                    )}
                </div>
                <div className="sim-summary-stat">
                    <span className="sim-summary-stat__label">{t('simulation.avgReturn', 'Ortalama getiri')}</span>
                    <span className={`sim-summary-stat__value ${stats.avgReturnPct >= 0 ? 'sim-pnl-pos' : 'sim-pnl-neg'}`}>
                        {fmtPct(locale, stats.avgReturnPct)}
                    </span>
                    <span className="sim-summary-stat__sub" style={{ color: mutedColor }}>
                        {t('simulation.simCount', '{n} simülasyon').replace('{n}', String(stats.count))}
                    </span>
                </div>
                <div className="sim-summary-stat">
                    <span className="sim-summary-stat__label">{t('simulation.totalSimulated', 'Toplam simüle edilen')}</span>
                    <span className="sim-summary-stat__value">{formatSimMoney(locale, stats.totalInitial, displayCurrency)}</span>
                    <span className="sim-summary-stat__sub" style={{ color: mutedColor }}>
                        {t('simulation.totalToday', 'Bugün: {v}').replace(
                            '{v}',
                            formatSimMoney(locale, stats.totalCurrent, displayCurrency),
                        )}
                    </span>
                </div>
            </div>
            {saveFeedback ? (
                <p className="sim-save-feedback" style={{ color: mutedColor }}>
                    {saveFeedback}
                </p>
            ) : null}
            <SimulationExportActions
                onExportCsv={onExportCsv}
                onExportPdf={onExportPdf}
                onSaveSimulation={onSaveSimulation}
                disabled={exportDisabled}
                saveDisabled={exportDisabled}
            />
        </section>
    );
}
