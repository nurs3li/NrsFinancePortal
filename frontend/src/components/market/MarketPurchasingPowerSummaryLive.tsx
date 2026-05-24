import { memo } from 'react';
import type { ChartRangeId } from './heatmapRange';
import { usePurchasingPowerData } from '../../hooks/usePurchasingPowerData';
import { MarketPurchasingPowerSummaryCard } from './MarketPurchasingPowerSummaryCard';

type CandlePoint = { time: string; close: number };

type Props = {
    anchorDate: string;
    enabled: boolean;
    range: ChartRangeId;
    candles: CandlePoint[];
    assetInTry: boolean;
    symbol: string;
    displayName?: string;
    unitLabel: string;
    tokens: { bgCard: string; border: string; text: string; textMuted: string };
};

function MarketPurchasingPowerSummaryLiveImpl({
    anchorDate,
    enabled,
    range,
    candles,
    assetInTry,
    symbol,
    displayName,
    unitLabel,
    tokens,
}: Props) {
    const data = usePurchasingPowerData(enabled, range, candles, anchorDate, assetInTry);

    return (
        <MarketPurchasingPowerSummaryCard
            symbol={symbol}
            displayName={displayName}
            unitLabel={unitLabel}
            data={data}
            tokens={tokens}
        />
    );
}

export const MarketPurchasingPowerSummaryLive = memo(MarketPurchasingPowerSummaryLiveImpl);

