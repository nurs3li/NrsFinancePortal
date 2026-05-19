import { X } from 'lucide-react';
import { useLanguage } from '../../i18n/LanguageContext';
import { formatSimMoney } from './simCurrency';
import { qualityLabel, qualityPillClass, sourceLabel } from './utils';
import type { SimDisplayCurrency, SimulationResultItem } from './types';

type SimulationResultDetailDrawerProps = {
    item: SimulationResultItem | null;
    displayCurrency: SimDisplayCurrency;
    onClose: () => void;
    textColor: string;
    mutedColor: string;
    borderColor: string;
    bgCard: string;
};

export function SimulationResultDetailDrawer({
    item,
    displayCurrency,
    onClose,
    textColor,
    mutedColor,
    borderColor,
    bgCard,
}: SimulationResultDetailDrawerProps) {
    const { t, lang } = useLanguage();
    const locale = lang === 'en' ? 'en-US' : 'tr-TR';

    if (!item) return null;

    const currency = item?.displayCurrency ?? displayCurrency;
    const fmt = (n: number, fractionDigits = 2) =>
        formatSimMoney(locale, n, currency, { maximumFractionDigits: fractionDigits });

    return (
        <div className="sim-drawer-backdrop" role="presentation" onClick={onClose}>
            <aside
                className="sim-drawer"
                style={{ borderColor, background: bgCard, color: textColor }}
                role="dialog"
                aria-modal="true"
                aria-labelledby="sim-drawer-title"
                onClick={(e) => e.stopPropagation()}
            >
                <header className="sim-drawer__head">
                    <h2 id="sim-drawer-title" className="sim-drawer__title">
                        {item.assetName} · {item.assetType}
                    </h2>
                    <button type="button" className="sim-drawer__close" onClick={onClose} aria-label={t('common.close', 'Kapat')}>
                        <X size={20} />
                    </button>
                </header>
                {item.scenarioLabel ? (
                    <p className="sim-drawer__scenario" style={{ color: mutedColor }}>
                        {item.scenarioLabel}
                    </p>
                ) : null}
                <dl className="sim-drawer__dl">
                    <dt>{t('simulation.exportColInitialTry', 'Başlangıç tutarı')}</dt>
                    <dd>{fmt(item.initialAmount)}</dd>
                    <dt>{t('simulation.buyDate', 'Alım tarihi')}</dt>
                    <dd>{new Date(item.buyDate).toLocaleDateString(locale)}</dd>
                    <dt>{t('simulation.exportColBuyUnitTry', 'Alış fiyatı')}</dt>
                    <dd>{fmt(item.buyPrice, 4)}</dd>
                    <dt>{t('simulation.exportColCurrentUnitTry', 'Güncel fiyat')}</dt>
                    <dd>{fmt(item.currentPrice, 4)}</dd>
                    <dt>{t('simulation.exportColValueTry', 'Güncel değer')}</dt>
                    <dd>{fmt(item.currentValue)}</dd>
                    <dt>PNL</dt>
                    <dd className={item.pnl >= 0 ? 'sim-pnl-pos' : 'sim-pnl-neg'}>
                        {item.pnl >= 0 ? '+' : ''}
                        {fmt(item.pnl)} ({item.pnlPct.toLocaleString(locale, { maximumFractionDigits: 2 })}%)
                    </dd>
                    <dt>{t('simulation.exportColPriceSource', 'Fiyat kaynağı')}</dt>
                    <dd>{sourceLabel(item.buyPriceSource, t)}</dd>
                    <dt>{t('simulation.exportColRefDate', 'Referans tarihi')}</dt>
                    <dd>{new Date(item.historicalPriceDate).toLocaleDateString(locale)}</dd>
                    <dt>{t('simulation.exportColQuality', 'Veri kalitesi')}</dt>
                    <dd>
                        <span className={qualityPillClass(item.qualityFlag)}>{qualityLabel(item.qualityFlag, t)}</span>
                    </dd>
                </dl>
                <p className="sim-drawer__formula" style={{ color: mutedColor }}>
                    {t(
                        'simulation.detailFormula',
                        'Hesaplama: başlangıç tutarı ÷ alış birim fiyatı = adet; adet × güncel birim fiyat = bugünkü değer; fark = kâr/zarar.',
                    )}
                </p>
                {item.message ? (
                    <p className="sim-drawer__msg" style={{ color: mutedColor }}>
                        {item.message}
                    </p>
                ) : null}
                <p className="sim-drawer__disclaimer" style={{ color: mutedColor }}>
                    {t('simulation.disclaimer', 'Yatırım tavsiyesi değildir. Sonuçlar geçmiş veriye dayalı simülasyondur.')}
                </p>
            </aside>
        </div>
    );
}
