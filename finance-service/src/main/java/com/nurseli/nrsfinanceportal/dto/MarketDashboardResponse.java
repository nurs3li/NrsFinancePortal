package com.nurseli.nrsfinanceportal.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MarketDashboardResponse(
        MarketOverviewResponse latest,
        List<SparklineEntry> sparklines,
        List<HeatmapTileEntry> heatmapTiles,
        HeatmapMeta heatmapMeta,
        List<VolatilityEntry> volatility,
        LocalDateTime computedAt
) {}
