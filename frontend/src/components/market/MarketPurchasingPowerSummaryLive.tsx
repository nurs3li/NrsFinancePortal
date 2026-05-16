import { memo, useEffect, useState, type MutableRefObject } from 'react';
import type { ChartRangeId } from './heatmapRange';
import { usePurchasingPowerData } from '../../hooks/usePurchasingPowerData';
import { clampPpAnchorYmd } from '../../utils/marketPurchasingPower';
import { MarketPurchasingPowerSummaryCard } from './MarketPurchasingPowerSummaryCard';

type CandlePoint = { time: string; close: number };

type PpBounds = { first: string; last: string; today: string };

type Props = {
    crosshairHandlerRef: MutableRefObject<((dateYmd: string) => void) | null>;
    boundsRef: MutableRefObject<PpBounds>;
    chartAnchorDate: string;
    enabled: boolean;
    range: ChartRangeId;
    candles: CandlePoint[];
    assetInTry: boolean;
    symbol: string;
    displayName?: string;
    tokens: { bgCard: string; border: string; text: string; textMuted: string };
};

function MarketPurchasingPowerSummaryLiveImpl({
    crosshairHandlerRef,
    boundsRef,
    chartAnchorDate,
    enabled,
    range,
    candles,
    assetInTry,
    symbol,
    displayName,
    tokens,
}: Props) {
    const [summaryAnchor, setSummaryAnchor] = useState(chartAnchorDate);

    useEffect(() => {
        if (chartAnchorDate) setSummaryAnchor(chartAnchorDate);
    }, [chartAnchorDate]);

    useEffect(() => {
        crosshairHandlerRef.current = (d: string) => {
            const { first, last, today } = boundsRef.current;
            if (!d) {
                if (first) setSummaryAnchor(clampPpAnchorYmd(first, first, last, today));
                return;
            }
            setSummaryAnchor(clampPpAnchorYmd(d, first, last, today));
        };
        return () => {
            crosshairHandlerRef.current = null;
        };
    }, [crosshairHandlerRef, boundsRef, chartAnchorDate]);

    const data = usePurchasingPowerData(
        enabled,
        range,
        candles,
        chartAnchorDate,
        assetInTry,
        summaryAnchor,
    );

    return (
        <MarketPurchasingPowerSummaryCard
            symbol={symbol}
            displayName={displayName}
            data={data}
            tokens={tokens}
        />
    );
}

export const MarketPurchasingPowerSummaryLive = memo(MarketPurchasingPowerSummaryLiveImpl);
