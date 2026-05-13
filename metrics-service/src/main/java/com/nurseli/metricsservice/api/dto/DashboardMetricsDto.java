package com.nurseli.metricsservice.api.dto;

import java.util.List;

public record DashboardMetricsDto(
        long totalTrades,
        long totalWhaleAlerts,
        long totalSuspiciousEvents,
        List<TradeCountBySymbol> topSymbols,
        List<WhaleCountByLevel> whaleByLevel,
        long totalInvestorBehaviorEvents,
        List<WhaleCountByLevel> investorByLevel,
        double avgPortfolioImpactScore,
        double maxPortfolioImpactScore,
        double maxTotalPortfolioValueTry
) {
    public record TradeCountBySymbol(String symbol, long count) {}
    public record WhaleCountByLevel(String level, long count) {}
}