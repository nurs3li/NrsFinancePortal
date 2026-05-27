import { useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import { AssetLogo } from '../AssetLogo';
import type { SimulationAssetType } from '../../types/simulationAssetType';
import {
    SIM_ASSET_TYPES,
    categoryFallbackIcon,
    categoryLabel,
    symbolDisplayLabel,
    symbolLogoUrl,
} from './assetBrandingSim';

type SimAssetPickerProps = {
    assetType: SimulationAssetType;
    symbol: string;
    symbolOptions: string[];
    loading?: boolean;
    onTypeChange: (t: SimulationAssetType) => void;
    onSymbolChange: (s: string) => void;
    t: (k: string, d: string) => string;
    mutedColor: string;
    emptyMessage: string;
};

export function SimAssetPicker({
    assetType,
    symbol,
    symbolOptions,
    loading,
    onTypeChange,
    onSymbolChange,
    t,
    mutedColor,
    emptyMessage,
}: SimAssetPickerProps) {
    const [filter, setFilter] = useState('');

    const filtered = useMemo(() => {
        const q = filter.trim().toUpperCase();
        if (!q) return symbolOptions;
        return symbolOptions.filter((s) => s.toUpperCase().includes(q) || symbolDisplayLabel(s, assetType).toUpperCase().includes(q));
    }, [filter, symbolOptions, assetType]);

    const FallbackIcon = categoryFallbackIcon(assetType);
    const fallbackColor = '#94a3b8';

    return (
        <div className="sim-asset-picker">
            <div className="sim-category-pills" role="tablist" aria-label={t('simulation.assetType', 'Varlık türü')}>
                {SIM_ASSET_TYPES.map((cat) => {
                    const Icon = categoryFallbackIcon(cat);
                    const active = cat === assetType;
                    return (
                        <button
                            key={cat}
                            type="button"
                            role="tab"
                            aria-selected={active}
                            className={`sim-category-pill${active ? ' sim-category-pill--on' : ''}`}
                            onClick={() => onTypeChange(cat)}
                        >
                            <AssetLogo
                                src={cat === 'METAL' ? symbolLogoUrl('ALTIN', 'METAL') : null}
                                alt=""
                                fallbackIcon={Icon}
                                fallbackColor={fallbackColor}
                                size={18}
                            />
                            <span>{categoryLabel(cat, t)}</span>
                        </button>
                    );
                })}
            </div>

            {loading ? (
                <div className="sim-premium-input sim-field__loading">{t('common.loading', 'Yükleniyor...')}</div>
            ) : symbolOptions.length === 0 ? (
                <div className="sim-premium-input sim-field__empty" style={{ color: mutedColor }}>
                    {emptyMessage}
                </div>
            ) : (
                <>
                    <div className="sim-symbol-search-wrap">
                        <Search size={16} className="sim-symbol-search-icon" aria-hidden />
                        <input
                            type="search"
                            className="sim-premium-input sim-symbol-search"
                            placeholder={t('simulation.symbolSearch', 'Sembol ara…')}
                            value={filter}
                            onChange={(e) => setFilter(e.target.value)}
                        />
                    </div>
                    <ul className="sim-symbol-list" role="listbox" aria-label={t('simulation.symbolList', 'Sembol listesi')}>
                        {filtered.map((sym) => {
                            const selected = sym === symbol;
                            const label = symbolDisplayLabel(sym, assetType);
                            return (
                                <li key={sym}>
                                    <button
                                        type="button"
                                        role="option"
                                        aria-selected={selected}
                                        className={`sim-symbol-option${selected ? ' sim-symbol-option--on' : ''}`}
                                        onClick={() => onSymbolChange(sym)}
                                    >
                                        <AssetLogo
                                            src={symbolLogoUrl(sym, assetType)}
                                            alt={`${sym} logo`}
                                            fallbackIcon={FallbackIcon}
                                            fallbackColor={fallbackColor}
                                            size={28}
                                        />
                                        <span className="sim-symbol-option__text">
                                            <span className="sim-symbol-option__sym">{sym}</span>
                                            {label !== sym ? <span className="sim-symbol-option__sub">{label}</span> : null}
                                        </span>
                                    </button>
                                </li>
                            );
                        })}
                    </ul>
                </>
            )}
        </div>
    );
}
