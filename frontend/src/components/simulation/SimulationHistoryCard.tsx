import { Eye, EyeOff, Trash2 } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import { AssetLogo } from '../AssetLogo';
import { formatSimMoney, simCurrencySymbol } from './simCurrency';
import { categoryFallbackIcon, categoryLabel, symbolLogoUrl } from './assetBrandingSim';
import type { SimDisplayCurrency, SimulationHistoryEntry } from './types';

type SimulationHistoryCardProps = {
    entries: SimulationHistoryEntry[];
    activeId: string | null;
    onView: (entry: SimulationHistoryEntry) => void;
    onDelete: (id: string) => void;
    mutedColor: string;
    textColor: string;
    borderColor: string;
    tableBorder: string;
};

function formatDate(locale: string, ymd: string) {
    const d = ymd.includes('T') ? new Date(ymd) : new Date(`${ymd}T12:00:00`);
    if (Number.isNaN(d.getTime())) return ymd;
    return d.toLocaleDateString(locale, { day: '2-digit', month: 'long', year: 'numeric' });
}

function formatDateTime(locale: string, iso: string) {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString(locale, {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });
}

export function SimulationHistoryCard({
    entries,
    activeId,
    onView,
    onDelete,
    mutedColor,
    textColor,
    borderColor,
    tableBorder,
}: SimulationHistoryCardProps) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    const fmtMoney = (v: number, currency: SimDisplayCurrency) => formatSimMoney(locale, v, currency);

    return (
        <section className="card-premium sim-history-section">
            <h2 className="sim-section-title">{t('simulation.historyTitle', 'Simülasyon Geçmişi')}</h2>
            <p className="sim-lead sim-history-section__lead" style={{ color: mutedColor }}>
                {t(
                    'simulation.historySubtitle',
                    'Kaydettiğin simülasyonları buradan tekrar grafiğe ve sonuçlara yükleyebilirsin.',
                )}
            </p>

            {entries.length === 0 ? (
                <p className="sim-lead" style={{ color: mutedColor }}>
                    {t('simulation.historyEmpty', 'Henüz kayıtlı simülasyon yok. Sonuç özetinden “Simülasyonu Kaydet” ile ekleyebilirsin.')}
                </p>
            ) : (
                <div className="sim-history-table-wrap" style={{ borderColor: tableBorder }}>
                    <table className="sim-history-table" style={{ color: textColor, borderColor: tableBorder }}>
                        <thead>
                            <tr style={{ borderColor: borderColor }}>
                                <th>{t('simulation.historyColTitle', 'Simülasyon')}</th>
                                <th>{t('simulation.historyColAssets', 'Varlıklar')}</th>
                                <th>{t('simulation.historyColBalance', 'Bakiye')}</th>
                                <th>{t('simulation.historyColBuyDate', 'Alım tarihi')}</th>
                                <th>{t('simulation.historyColSavedAt', 'Simülasyon tarihi')}</th>
                                <th>{t('simulation.historyColActions', 'İşlem')}</th>
                            </tr>
                        </thead>
                        <tbody>
                            {entries.map((entry) => {
                                const first = entry.items[0];
                                const buyDate = first?.buyDate ?? '—';
                                const balance = first?.initialAmount ?? 0;
                                const isActive = entry.id === activeId;
                                const entryCurrency: SimDisplayCurrency =
                                    entry.amountCurrency === 'USD' || first?.displayCurrency === 'USD' ? 'USD' : 'TRY';

                                return (
                                    <tr
                                        key={entry.id}
                                        className={isActive ? 'sim-history-row sim-history-row--active' : 'sim-history-row'}
                                        style={{ borderColor: borderColor }}
                                    >
                                        <td>
                                            <span className="sim-history-row__title">{entry.label}</span>
                                            {isActive ? (
                                                <span className="sim-history-row__badge">
                                                    {t('simulation.historyActive', 'Yüklendi')}
                                                </span>
                                            ) : null}
                                        </td>
                                        <td>
                                            <ul className="sim-history-assets">
                                                {entry.items.map((item) => {
                                                    const Fallback = categoryFallbackIcon(item.assetType);
                                                    return (
                                                        <li key={item.id} className="sim-history-asset">
                                                            <AssetLogo
                                                                src={symbolLogoUrl(item.assetName, item.assetType)}
                                                                alt=""
                                                                fallbackIcon={Fallback}
                                                                fallbackColor="#94a3b8"
                                                                size={22}
                                                            />
                                                            <span className="sim-history-asset__meta">
                                                                <span className="sim-history-asset__sym">{item.assetName}</span>
                                                                <span className="sim-history-asset__cat" style={{ color: mutedColor }}>
                                                                    {categoryLabel(item.assetType, t)}
                                                                </span>
                                                            </span>
                                                        </li>
                                                    );
                                                })}
                                            </ul>
                                        </td>
                                        <td className="tp-mono">
                                            {balance > 0 ? (
                                                <>
                                                    <span className="sim-history-currency" title={entryCurrency}>
                                                        {simCurrencySymbol(entryCurrency)}
                                                    </span>{' '}
                                                    {fmtMoney(balance, entryCurrency)}
                                                </>
                                            ) : (
                                                '—'
                                            )}
                                        </td>
                                        <td>{buyDate ? formatDate(locale, buyDate) : '—'}</td>
                                        <td>{formatDateTime(locale, entry.savedAt)}</td>
                                        <td>
                                            <div className="sim-history-actions">
                                                <button
                                                    type="button"
                                                    className={`sim-toolbar-btn sim-history-btn sim-history-btn--view${isActive ? ' sim-history-btn--view-on' : ''}`}
                                                    onClick={() => onView(entry)}
                                                    aria-pressed={isActive}
                                                >
                                                    {isActive ? (
                                                        <EyeOff size={15} aria-hidden />
                                                    ) : (
                                                        <Eye size={15} aria-hidden />
                                                    )}
                                                    {isActive
                                                        ? t('simulation.historyHide', 'Gizle')
                                                        : t('simulation.historyView', 'Gör')}
                                                </button>
                                                <button
                                                    type="button"
                                                    className="sim-toolbar-btn sim-history-btn sim-history-btn--delete"
                                                    onClick={() => onDelete(entry.id)}
                                                    aria-label={t('simulation.historyDelete', 'Sil')}
                                                >
                                                    <Trash2 size={15} aria-hidden />
                                                    {t('simulation.historyDelete', 'Sil')}
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}
        </section>
    );
}
