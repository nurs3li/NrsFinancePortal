package com.nurseli.nrsfinanceportal.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Spot portföy sayfası için tek istekte positions + summary + insights + varsayılan grafik.
 */
public record ManualPortfolioPageResponse(
        List<ManualPortfolioView> positions,
        ManualPortfolioSummaryView summary,
        ManualPortfolioInsightsResponse insights,
        ManualPortfolioPageTimeseries timeseries,
        ManualPortfolioPageMeta meta
) {
    public record ManualPortfolioPageTimeseries(
            String range,
            String from,
            String to,
            List<ManualPortfolioTimeseriesPointDto> points
    ) {}

    public record ManualPortfolioPageMeta(
            Instant warmedAt,
            Instant pricingAt,
            long gapFillMs,
            String warmStatus
    ) {}
}
