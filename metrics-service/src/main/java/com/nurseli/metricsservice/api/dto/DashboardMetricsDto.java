package com.nurseli.metricsservice.api.dto;

import java.util.List;

public record DashboardMetricsDto(
        long totalTrades,
        long totalWhaleAlerts,
        long totalSuspiciousEvents,
        List<TradeCountBySymbol> topSymbols,
        List<WhaleCountByLevel> whaleByLevel
) {
    public record TradeCountBySymbol(String symbol, long count) {}
    public record WhaleCountByLevel(String level, long count) {}
}