package com.nurseli.nrsfinanceportal.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Piyasa dashboard response'u; güncel fiyatlar, sparkline, heatmap, volatilite ve hesaplama zamanını birleştirir.
 */
public record MarketDashboardResponse(
        MarketOverviewResponse latest,
        List<SparklineEntry> sparklines,
        List<HeatmapTileEntry> heatmapTiles,
        HeatmapMeta heatmapMeta,
        List<VolatilityEntry> volatility,
        LocalDateTime computedAt
) {}
