import { AssetLogo } from '../AssetLogo';
import { CHART_PALETTE } from './constants';
import { categoryFallbackIcon, symbolLogoUrl } from './assetBrandingSim';
import { SimDualMoney } from './SimDualMoney';
import type { SimDisplayCurrency, SimulationResultItem } from './types';

type SimulationLegendChipsProps = {
    items: SimulationResultItem[];
    locale: string;
    displayCurrency: SimDisplayCurrency;
    usdTryRate?: number | null;
    onToggleVisible: (id: string) => void;
};

export function SimulationLegendChips({
    items,
    locale,
    displayCurrency: _displayCurrency,
    usdTryRate,
    onToggleVisible,
}: SimulationLegendChipsProps) {
    if (items.length === 0) return null;

    return (
        <div className="sim-legend-chips" role="list" aria-label="Grafik serileri">
            {items.map((res, i) => {
                const color = CHART_PALETTE[i % CHART_PALETTE.length];
                const pos = res.pnlPct >= 0;
                return (
                    <button
                        key={res.id}
                        type="button"
                        role="listitem"
                        className={`sim-legend-chip${res.visible ? '' : ' sim-legend-chip--off'}`}
                        style={{ borderColor: color }}
                        onClick={() => onToggleVisible(res.id)}
                        title={res.visible ? 'Grafikten gizle' : 'Grafikte göster'}
                    >
                        <AssetLogo
                            src={symbolLogoUrl(res.assetName, res.assetType)}
                            alt=""
                            fallbackIcon={categoryFallbackIcon(res.assetType)}
                            fallbackColor="#94a3b8"
                            size={20}
                        />
                        <span className="sim-legend-chip__sym">{res.assetName}</span>
                        <span className={pos ? 'sim-pnl-pos' : 'sim-pnl-neg'}>
                            {pos ? '+' : ''}
                            {res.pnlPct.toLocaleString(locale, { maximumFractionDigits: 2 })}%
                        </span>
                        <span className="sim-legend-chip__val">
                            <SimDualMoney
                                locale={locale}
                                value={res.currentValue}
                                res={res}
                                usdTryRate={usdTryRate}
                                className="sim-legend-chip__dual"
                            />
                        </span>
                    </button>
                );
            })}
        </div>
    );
}
