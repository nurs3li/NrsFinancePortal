import { X } from 'lucide-react';
import type { CompareDraftItem } from './types';
import { AssetLogo } from '../AssetLogo';
import { categoryFallbackIcon, categoryLabel, symbolDisplayLabel, symbolLogoUrl } from './assetBrandingSim';

type SimCompareDraftListProps = {
    items: CompareDraftItem[];
    onRemove: (id: string) => void;
    t: (k: string, d: string) => string;
    mutedColor: string;
};

export function SimCompareDraftList({ items, onRemove, t, mutedColor }: SimCompareDraftListProps) {
    if (items.length === 0) return null;

    return (
        <div className="sim-compare-queue">
            <span className="sim-field__label">{t('simulation.compareQueueTitle', 'Karşılaştırmaya eklenecekler')}</span>
            <ul className="sim-compare-queue__list">
                {items.map((item) => {
                    const Fallback = categoryFallbackIcon(item.assetType);
                    return (
                        <li key={item.id} className="sim-compare-queue__item">
                            <AssetLogo
                                src={symbolLogoUrl(item.symbol, item.assetType)}
                                alt={`${item.symbol} logo`}
                                fallbackIcon={Fallback}
                                fallbackColor="#94a3b8"
                                size={24}
                            />
                            <span className="sim-compare-queue__meta">
                                <span className="sim-compare-queue__sym">{item.symbol}</span>
                                <span className="sim-compare-queue__cat" style={{ color: mutedColor }}>
                                    {categoryLabel(item.assetType, t)} · {symbolDisplayLabel(item.symbol, item.assetType)}
                                </span>
                            </span>
                            <button
                                type="button"
                                className="sim-compare-queue__remove"
                                onClick={() => onRemove(item.id)}
                                aria-label={t('simulation.compareRemove', 'Kaldır')}
                            >
                                <X size={16} />
                            </button>
                        </li>
                    );
                })}
            </ul>
        </div>
    );
}
