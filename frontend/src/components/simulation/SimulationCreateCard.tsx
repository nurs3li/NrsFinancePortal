import { useState, type FormEvent, type RefObject } from 'react';
import { useLanguage } from '../../i18n/LanguageContext';
import type { SimulationAssetType } from '../../types/simulationAssetType';
import { SimAssetPicker } from './SimAssetPicker';
import { SimCollapsibleAssetSection } from './SimCollapsibleAssetSection';
import { SimCompareDraftList } from './SimCompareDraftList';
import { simCurrencySymbol } from './simCurrency';
import { istanbulTodayYmd } from './simDates';
import { SimCurrencyToggle } from './SimCurrencyToggle';
import type { SimDisplayCurrency } from './types';
import { SimTermInfo } from './SimTermInfo';
import type { BuyPriceMode, CompareDraftItem } from './types';

export type SimulationCreateCardProps = {
    textColor: string;
    mutedColor: string;
    scenarioLabel: string;
    onScenarioLabelChange: (v: string) => void;
    type: SimulationAssetType;
    onTypeChange: (t: SimulationAssetType) => void;
    symbol: string;
    onSymbolChange: (s: string) => void;
    amount: string;
    onAmountChange: (v: string) => void;
    amountCurrency: SimDisplayCurrency;
    onAmountCurrencyChange: (c: SimDisplayCurrency) => void;
    buyDate: string;
    onBuyDateChange: (v: string) => void;
    buyPriceMode: BuyPriceMode;
    onBuyPriceModeChange: (m: BuyPriceMode) => void;
    manualBuyPrice: string;
    onManualBuyPriceChange: (v: string) => void;
    manualPricePrompt?: boolean;
    manualPriceInputRef?: RefObject<HTMLInputElement | null>;
    symbolOptions: string[];
    overviewLoading: boolean;
    compareType: SimulationAssetType;
    onCompareTypeChange: (t: SimulationAssetType) => void;
    compareSymbol: string;
    onCompareSymbolChange: (s: string) => void;
    compareSymbolOptions: string[];
    compareOptionsLoading: boolean;
    compareDrafts: CompareDraftItem[];
    onAddCompare: () => void;
    onRemoveCompare: (id: string) => void;
    loading: boolean;
    onSubmit: (e: FormEvent) => void;
};

export function SimulationCreateCard({
    textColor,
    mutedColor,
    scenarioLabel,
    onScenarioLabelChange,
    type,
    onTypeChange,
    symbol,
    onSymbolChange,
    amount,
    onAmountChange,
    amountCurrency,
    onAmountCurrencyChange,
    buyDate,
    onBuyDateChange,
    buyPriceMode,
    onBuyPriceModeChange,
    manualBuyPrice,
    onManualBuyPriceChange,
    manualPricePrompt = false,
    manualPriceInputRef,
    symbolOptions,
    overviewLoading,
    compareType,
    onCompareTypeChange,
    compareSymbol,
    onCompareSymbolChange,
    compareSymbolOptions,
    compareOptionsLoading,
    compareDrafts,
    onAddCompare,
    onRemoveCompare,
    loading,
    onSubmit,
}: SimulationCreateCardProps) {
    const { t } = useLanguage();
    const [assetPickerOpen, setAssetPickerOpen] = useState(false);
    const [comparePickerOpen, setComparePickerOpen] = useState(false);

    const canAddCompare =
        compareSymbol.trim().length > 0 &&
        !compareOptionsLoading &&
        !(compareType === type && compareSymbol.toUpperCase() === symbol.toUpperCase()) &&
        !compareDrafts.some((d) => d.assetType === compareType && d.symbol.toUpperCase() === compareSymbol.toUpperCase());

    return (
        <section className="card-premium card-premium--static sim-create-card">
            <h2 className="sim-section-title">{t('simulation.createTitle', 'Simülasyon Oluştur')}</h2>
            <form className="sim-create-form" onSubmit={onSubmit}>
                <label className="sim-field" style={{ color: textColor }}>
                    <span className="sim-field__label">{t('simulation.scenarioLabel', 'Harcama / senaryo adı')}</span>
                    <span className="sim-field__hint">{t('simulation.scenarioLabelHint', 'İsteğe bağlı — sonuçlarda görünür')}</span>
                    <input
                        type="text"
                        value={scenarioLabel}
                        onChange={(e) => onScenarioLabelChange(e.target.value)}
                        className="sim-premium-input"
                        placeholder={t('simulation.scenarioPlaceholder', 'Telefon harcaması, tatil bütçesi…')}
                    />
                </label>

                <div className="sim-amount-date-row" style={{ color: textColor }}>
                    <label className="sim-field sim-amount-date-row__amount">
                        <span className="sim-field__label sim-amount-date-row__label">
                            {t('simulation.stepAmountShort', '1. Tutar')}
                        </span>
                        <span className="sim-field__hint sim-amount-date-row__hint">
                            {t('simulation.stepAmountHint', 'Ne kadar para ile simüle edilsin?')}
                        </span>
                        <div className="sim-amount-input-wrap">
                            <span className="sim-amount-input__prefix" aria-hidden>
                                {simCurrencySymbol(amountCurrency)}
                            </span>
                            <input
                                type="number"
                                step="0.01"
                                min="0"
                                value={amount}
                                onChange={(e) => onAmountChange(e.target.value)}
                                className="sim-premium-input sim-amount-input"
                                aria-label={t('simulation.stepAmount', '1. Ne kadar para ile simüle etmek istiyorsun?')}
                            />
                        </div>
                    </label>

                    <div
                        className="sim-amount-date-row__currency"
                        role="group"
                        aria-label={t('simulation.amountCurrency', 'Para birimi')}
                    >
                        <SimCurrencyToggle compact value={amountCurrency} onChange={onAmountCurrencyChange} />
                    </div>

                    <label className="sim-field sim-amount-date-row__date">
                        <span className="sim-field__label sim-amount-date-row__label">
                            {t('simulation.stepDateShort', '2. Alım tarihi')}
                        </span>
                        <span className="sim-field__hint sim-amount-date-row__hint">
                            {t('simulation.stepDateHint', 'Yatırım yapmış gibi hesaplanacak gün')}
                        </span>
                        <input
                            type="date"
                            value={buyDate}
                            max={istanbulTodayYmd()}
                            onChange={(e) => onBuyDateChange(e.target.value)}
                            className="sim-premium-input sim-amount-date-row__date-input"
                            aria-label={t('simulation.stepDate', '2. Hangi tarihte yatırım yapmış gibi hesaplayalım?')}
                        />
                    </label>
                </div>

                <div className="sim-field sim-collapsible-field" style={{ color: textColor }}>
                    <SimCollapsibleAssetSection
                        title={t('simulation.stepAsset', '3. Hangi varlığı seçmek istiyorsun?')}
                        expanded={assetPickerOpen}
                        onToggle={() => setAssetPickerOpen((o) => !o)}
                        collapsedChips={symbol.trim() ? [{ symbol: symbol.trim().toUpperCase(), assetType: type }] : []}
                        emptyCollapsedLabel={t('simulation.noAssetSelected', 'Henüz varlık seçilmedi')}
                        mutedColor={mutedColor}
                        textColor={textColor}
                    >
                        <SimAssetPicker
                            assetType={type}
                            symbol={symbol}
                            symbolOptions={symbolOptions}
                            loading={overviewLoading}
                            onTypeChange={onTypeChange}
                            onSymbolChange={onSymbolChange}
                            t={t}
                            mutedColor={mutedColor}
                            emptyMessage={t('simulation.noSymbolForType', 'Bu varlık türü için kayıtlı sembol yok.')}
                        />
                    </SimCollapsibleAssetSection>
                </div>

                <div className="sim-field sim-compare-block sim-collapsible-field" style={{ color: textColor }}>
                    <SimCollapsibleAssetSection
                        title={t('simulation.compareSection', 'Karşılaştırma ekle')}
                        hint={t(
                            'simulation.compareSectionHint',
                            'Aynı tutar ve tarihle başka bir varlık seç; simülasyon çalıştırıldığında hepsi birlikte grafiğe eklenir.',
                        )}
                        expanded={comparePickerOpen}
                        onToggle={() => setComparePickerOpen((o) => !o)}
                        collapsedChips={compareDrafts.map((d) => ({
                            symbol: d.symbol,
                            assetType: d.assetType,
                        }))}
                        emptyCollapsedLabel={t('simulation.noCompareAdded', 'Henüz karşılaştırma eklenmedi')}
                        mutedColor={mutedColor}
                        textColor={textColor}
                    >
                        <SimAssetPicker
                            assetType={compareType}
                            symbol={compareSymbol}
                            symbolOptions={compareSymbolOptions}
                            loading={compareOptionsLoading}
                            onTypeChange={onCompareTypeChange}
                            onSymbolChange={onCompareSymbolChange}
                            t={t}
                            mutedColor={mutedColor}
                            emptyMessage={t('simulation.noSymbolForType', 'Bu varlık türü için kayıtlı sembol yok.')}
                        />
                        <button
                            type="button"
                            className="sim-toolbar-btn sim-compare-add-btn"
                            disabled={!canAddCompare}
                            onClick={onAddCompare}
                        >
                            {t('simulation.compareAdd', 'Karşılaştırmaya ekle')}
                        </button>
                        <SimCompareDraftList items={compareDrafts} onRemove={onRemoveCompare} t={t} mutedColor={mutedColor} />
                    </SimCollapsibleAssetSection>
                </div>

                <div className="sim-field" style={{ color: textColor }}>
                    <span className="sim-field__label sim-field__label--inline">
                        {t('simulation.stepPriceSource', '4. Geçmiş fiyat nasıl belirlensin?')}
                        <SimTermInfo
                            termKey="price-source"
                            title={t('simulation.termPriceSourceTitle', 'Sistem geçmiş fiyatı')}
                            body={t(
                                'simulation.termPriceSourceBody',
                                'Seçilen tarihteki veya önceki işlem günündeki fiyat otomatik kullanılır. O tarihte veri yoksa manuel alış fiyatı girmeniz gerekir.',
                            )}
                            mutedColor={mutedColor}
                        />
                    </span>
                    <select
                        value={buyPriceMode}
                        onChange={(e) => onBuyPriceModeChange(e.target.value as BuyPriceMode)}
                        className="sim-premium-select"
                    >
                        <option value="SYSTEM">{t('simulation.priceSourceSystem', 'Sistem geçmiş fiyatı (önerilen)')}</option>
                        <option value="MANUAL">{t('simulation.priceSourceManual', 'Manuel fiyat gir')}</option>
                    </select>
                    <span className="sim-field__hint">{t('simulation.priceSourceHint', 'Seçilen tarihteki geçmiş fiyat bulunursa sistem otomatik hesaplar.')}</span>
                </div>

                {buyPriceMode === 'MANUAL' ? (
                    <label
                        className={`sim-field${manualPricePrompt ? ' sim-field--prompt' : ''}`}
                        style={{ color: textColor }}
                    >
                        <span className="sim-field__label">
                            {t('simulation.manualBuyPrice', 'Manuel alış fiyatı ({sym} / birim)').replace(
                                '{sym}',
                                simCurrencySymbol(amountCurrency),
                            )}
                        </span>
                        {manualPricePrompt ? (
                            <span className="sim-field__hint sim-field__hint--warn">
                                {t(
                                    'simulation.manualPriceRequired',
                                    'Seçilen tarih için sistem fiyatı yok. Lütfen o güne ait alış fiyatını girin.',
                                )}
                            </span>
                        ) : null}
                        <input
                            ref={manualPriceInputRef}
                            type="number"
                            step="0.00000001"
                            min="0"
                            value={manualBuyPrice}
                            onChange={(e) => onManualBuyPriceChange(e.target.value)}
                            className="sim-premium-input"
                            placeholder="1250.75"
                        />
                    </label>
                ) : null}

                <button
                    type="submit"
                    disabled={loading || overviewLoading || !symbol}
                    className="sim-submit-btn sim-submit-btn--block"
                >
                    {loading
                        ? t('simulation.adding', 'Ekleniyor...')
                        : t('simulation.simulateAndAdd', 'Simüle Et ve Karşılaştırmaya Ekle')}
                </button>
            </form>
        </section>
    );
}
