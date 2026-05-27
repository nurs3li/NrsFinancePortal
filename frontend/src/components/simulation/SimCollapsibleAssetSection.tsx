import type { ReactNode } from 'react';
import { ChevronDown } from 'lucide-react';
import { AssetLogo } from '../AssetLogo';
import type { SimulationAssetType } from '../../types/simulationAssetType';
import { categoryFallbackIcon, symbolLogoUrl } from './assetBrandingSim';

export type CollapsedSymbolChip = {
    symbol: string;
    assetType: SimulationAssetType;
};

type SimCollapsibleAssetSectionProps = {
    title: string;
    hint?: string;
    expanded: boolean;
    onToggle: () => void;
    collapsedChips: CollapsedSymbolChip[];
    emptyCollapsedLabel: string;
    mutedColor: string;
    textColor: string;
    children: ReactNode;
};

export function SimCollapsibleAssetSection({
    title,
    hint,
    expanded,
    onToggle,
    collapsedChips,
    emptyCollapsedLabel,
    mutedColor,
    textColor,
    children,
}: SimCollapsibleAssetSectionProps) {
    return (
        <div className={`sim-collapsible-section${expanded ? ' sim-collapsible-section--open' : ''}`}>
            <button
                type="button"
                className="sim-collapsible-section__trigger"
                style={{ color: textColor }}
                onClick={onToggle}
                aria-expanded={expanded}
            >
                <span className="sim-collapsible-section__trigger-main">
                    <span className="sim-field__label sim-collapsible-section__title">{title}</span>
                    {!expanded ? (
                        <span className="sim-collapsed-chips">
                            {collapsedChips.length > 0 ? (
                                collapsedChips.map((chip) => {
                                    const Fallback = categoryFallbackIcon(chip.assetType);
                                    return (
                                        <span key={`${chip.assetType}:${chip.symbol}`} className="sim-collapsed-chip">
                                            <AssetLogo
                                                src={symbolLogoUrl(chip.symbol, chip.assetType)}
                                                alt=""
                                                fallbackIcon={Fallback}
                                                fallbackColor="#94a3b8"
                                                size={20}
                                            />
                                            <span className="sim-collapsed-chip__sym">{chip.symbol}</span>
                                        </span>
                                    );
                                })
                            ) : (
                                <span className="sim-collapsed-chips__empty" style={{ color: mutedColor }}>
                                    {emptyCollapsedLabel}
                                </span>
                            )}
                        </span>
                    ) : null}
                </span>
                <ChevronDown
                    size={18}
                    className={`sim-collapsible-section__chevron${expanded ? ' sim-collapsible-section__chevron--open' : ''}`}
                    aria-hidden
                />
            </button>
            {hint && expanded ? (
                <span className="sim-field__hint sim-collapsible-section__hint" style={{ color: mutedColor }}>
                    {hint}
                </span>
            ) : null}
            {expanded ? <div className="sim-collapsible-section__body">{children}</div> : null}
        </div>
    );
}
