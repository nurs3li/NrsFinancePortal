package com.nurseli.whaleanalytics.domain.investor;

import java.math.BigDecimal;

public record PortfolioExposureSummary(
        BigDecimal totalPortfolioValueTry,
        BigDecimal totalInvestedAmountTry,
        BigDecimal totalNominalProfitTry,
        BigDecimal totalRealProfitTry,
        int positionCount,
        int openPositionCount,
        int closedPositionCount,
        String largestPositionSymbol,
        BigDecimal largestPositionValueTry,
        BigDecimal largestPositionRatio,
        BigDecimal cryptoExposureRatio,
        BigDecimal equityExposureRatio,
        BigDecimal fxExposureRatio,
        BigDecimal fundExposureRatio,
        BigDecimal metalExposureRatio
) {
    public static PortfolioExposureSummary empty() {
        return new PortfolioExposureSummary(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0,
                0,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
