import { Eye, EyeOff, Trash2 } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import { displaySimulationAssetType } from '../../types/simulationAssetType';
import { SimDualMoney } from './SimDualMoney';
import { categoryLabel } from './assetBrandingSim';
import { formatSimMoney } from './simCurrency';
import { qualityLabel, qualityPillClass, sourceLabel } from './utils';
import type { SimDisplayCurrency, SimulationResultItem, SortMode } from './types';

type SimulationResultsListProps = {
    results: SimulationResultItem[];
    displayCurrency: SimDisplayCurrency;
    usdTryRate?: number | null;
    sortMode: SortMode;
    onSortModeChange: (m: SortMode) => void;
    onToggleVisible: (id: string) => void;
    onDelete: (id: string) => void;
    onShowDetail: (id: string) => void;
    onShowAll: () => void;
    onHideAll: () => void;
    borderColor: string;
    tableBorder: string;
    textColor: string;
    mutedColor: string;
    bgCard: string;
};

function formatStoryDate(locale: string, ymd: string) {
    return new Date(ymd).toLocaleDateString(locale, { day: '2-digit', month: 'long', year: 'numeric' });
}

export function SimulationResultsList({
    results,
    displayCurrency,
    usdTryRate,
    sortMode,
    onSortModeChange,
    onToggleVisible,
    onDelete,
    onShowDetail,
    onShowAll,
    onHideAll,
    borderColor: _borderColor,
    tableBorder: _tableBorder,
    textColor,
    mutedColor,
    bgCard: _bgCard,
}: SimulationResultsListProps) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    const fmtMoney = (v: number, currency: SimDisplayCurrency = displayCurrency) =>
        formatSimMoney(locale, v, currency);

    return (
        <section className="card-premium sim-results-section">
            <div className="sim-results-section__head">
                <h2 className="sim-section-title">{t('simulation.list', 'Simülasyon Sonuçları')}</h2>
                <div className="sim-results-toolbar">
                    <select
                        value={sortMode}
                        onChange={(e) => onSortModeChange(e.target.value as SortMode)}
                        className="sim-premium-select sim-results-sort"
                    >
                        <option value="LATEST">{t('simulation.sortLatest', 'En yeni')}</option>
                        <option value="PNL_DESC">{t('simulation.sortPnlDesc', 'En yüksek kazanç (₺)')}</option>
                        <option value="PNL_ASC">{t('simulation.sortPnlAsc', 'En düşük kazanç (₺)')}</option>
                        <option value="PNL_PCT_DESC">{t('simulation.sortPctDesc', 'En yüksek getiri (%)')}</option>
                        <option value="PNL_PCT_ASC">{t('simulation.sortPctAsc', 'En düşük getiri (%)')}</option>
                        <option value="NAME_ASC">{t('simulation.sortName', 'Sembol (A-Z)')}</option>
                    </select>
                    <button type="button" className="sim-toolbar-btn" onClick={onShowAll}>
                        {t('simulation.showAll', 'Tümünü göster')}
                    </button>
                    <button type="button" className="sim-toolbar-btn" onClick={onHideAll}>
                        {t('simulation.hideAll', 'Tümünü gizle')}
                    </button>
                </div>
            </div>

            {results.length === 0 ? (
                <p className="sim-lead" style={{ color: mutedColor }}>
                    {t('simulation.listEmpty', 'Henüz simülasyon yok. Soldaki formdan ilk senaryonu ekle.')}
                </p>
            ) : (
                <>
                    <div className="sim-result-cards">
                        {results.map((r) => {
                            const pos = r.pnl >= 0;
                            const dateStr = formatStoryDate(locale, r.buyDate);
                            const displayAssetType = displaySimulationAssetType(r.assetType, r.pickerAssetType);
                            return (
                                <article key={r.id} className="sim-result-card">
                                    <div className="sim-result-card__top">
                                        <div>
                                            <h3 className="sim-result-card__title">
                                                {r.assetName} · {categoryLabel(displayAssetType, t)}
                                            </h3>
                                            {r.scenarioLabel ? (
                                                <p className="sim-result-card__label" style={{ color: mutedColor }}>
                                                    {r.scenarioLabel}
                                                </p>
                                            ) : null}
                                        </div>
                                        <div className="sim-result-card__actions">
                                            <button
                                                type="button"
                                                className="sim-toolbar-btn sim-result-card__icon-btn"
                                                onClick={() => onToggleVisible(r.id)}
                                                title={
                                                    r.visible
                                                        ? t('simulation.hideFromChart', 'Grafikten gizle')
                                                        : t('simulation.showOnChart', 'Grafikte göster')
                                                }
                                            >
                                                {r.visible ? <Eye size={16} /> : <EyeOff size={16} />}
                                            </button>
                                            <button type="button" className="sim-toolbar-btn" onClick={() => onShowDetail(r.id)}>
                                                {t('simulation.detail', 'Detay')}
                                            </button>
                                            <button
                                                type="button"
                                                className="sim-toolbar-btn sim-result-card__icon-btn sim-result-card__icon-btn--danger"
                                                onClick={() => onDelete(r.id)}
                                                title={t('common.delete', 'Sil')}
                                            >
                                                <Trash2 size={16} />
                                            </button>
                                        </div>
                                    </div>
                                    <p className="sim-result-card__story">
                                        <span>
                                            {t(
                                                'simulation.resultStoryPrefix',
                                                '{date} tarihinde {amount} yatırılsaydı bugün',
                                            )
                                                .replace('{date}', dateStr)
                                                .replace('{amount}', fmtMoney(r.initialAmount, r.displayCurrency))}{' '}
                                        </span>
                                        <SimDualMoney
                                            locale={locale}
                                            value={r.currentValue}
                                            res={r}
                                            usdTryRate={usdTryRate}
                                        />
                                        <span> {t('simulation.resultStorySuffix', 'olurdu.')}</span>
                                    </p>
                                    <p className={`sim-result-card__pnl ${pos ? 'sim-pnl-pos' : 'sim-pnl-neg'}`}>
                                        {t('simulation.resultPnl', 'Kar/Zarar')}: {pos ? '+' : ''}
                                        <SimDualMoney locale={locale} value={r.pnl} res={r} usdTryRate={usdTryRate} /> (
                                        {pos ? '+' : ''}
                                        {r.pnlPct.toLocaleString(locale, { maximumFractionDigits: 2 })}%)
                                    </p>
                                    <p className="sim-result-card__meta" style={{ color: mutedColor }}>
                                        {sourceLabel(r.buyPriceSource, t)} · {qualityLabel(r.qualityFlag, t)}
                                    </p>
                                </article>
                            );
                        })}
                    </div>

                    <div className="sim-results-table-wrap">
                        <table className="tp-table sim-table">
                            <thead>
                                <tr>
                                    <th>{t('simulation.colVisible', 'Görünür')}</th>
                                    <th>{t('simulation.colAsset', 'Varlık')}</th>
                                    <th>{t('simulation.colScenario', 'Senaryo')}</th>
                                    <th style={{ textAlign: 'right' }}>{t('simulation.colTodayValue', 'Bugünkü değer')}</th>
                                    <th style={{ textAlign: 'right' }}>{t('simulation.colPnl', 'Kar/Zarar')}</th>
                                    <th>{t('simulation.colQuality', 'Kaynak')}</th>
                                    <th style={{ textAlign: 'right' }}>{t('simulation.colActions', 'İşlem')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {results.map((r) => (
                                    <tr key={r.id} className="sim-table-row">
                                        <td>
                                            <button
                                                type="button"
                                                className="sim-result-card__icon-btn"
                                                onClick={() => onToggleVisible(r.id)}
                                                style={{ background: 'transparent', border: 'none', color: textColor, cursor: 'pointer' }}
                                            >
                                                {r.visible ? <Eye size={16} /> : <EyeOff size={16} />}
                                            </button>
                                        </td>
                                        <td>
                                            <div>{r.assetName}</div>
                                            <div style={{ color: mutedColor, fontSize: '0.75rem' }}>
                                                {categoryLabel(displaySimulationAssetType(r.assetType, r.pickerAssetType), t)}
                                            </div>
                                        </td>
                                        <td style={{ maxWidth: 220 }}>
                                            <div className="sim-table-story" style={{ color: textColor, fontSize: '0.8125rem' }}>
                                                {formatStoryDate(locale, r.buyDate)} — {fmtMoney(r.initialAmount, r.displayCurrency)}
                                                {r.scenarioLabel ? ` · ${r.scenarioLabel}` : ''}
                                            </div>
                                        </td>
                                        <td style={{ textAlign: 'right' }}>
                                            <SimDualMoney
                                                locale={locale}
                                                value={r.currentValue}
                                                res={r}
                                                usdTryRate={usdTryRate}
                                                className="sim-dual-money--table"
                                            />
                                        </td>
                                        <td style={{ textAlign: 'right' }} className={r.pnl >= 0 ? 'sim-pnl-pos' : 'sim-pnl-neg'}>
                                            {r.pnl >= 0 ? '+' : ''}
                                            <SimDualMoney
                                                locale={locale}
                                                value={r.pnl}
                                                res={r}
                                                usdTryRate={usdTryRate}
                                                className="sim-dual-money--table"
                                            />{' '}
                                            ({r.pnlPct.toFixed(2)}%)
                                        </td>
                                        <td>
                                            <span className={qualityPillClass(r.qualityFlag)}>{qualityLabel(r.qualityFlag, t)}</span>
                                        </td>
                                        <td style={{ textAlign: 'right' }}>
                                            <button type="button" className="sim-toolbar-btn" onClick={() => onShowDetail(r.id)}>
                                                {t('simulation.detail', 'Detay')}
                                            </button>
                                            <button
                                                type="button"
                                                className="sim-toolbar-btn"
                                                style={{ marginLeft: 6, color: '#991B1B' }}
                                                onClick={() => onDelete(r.id)}
                                            >
                                                <Trash2 size={14} />
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </>
            )}
        </section>
    );
}
