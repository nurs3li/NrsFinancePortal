package com.nurseli.whaleanalytics.event.investment;

import com.nurseli.whaleanalytics.domain.investor.InvestorLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvestorBehaviorUpdatedEvent(
        String eventId,
        Instant occurredAt,
        Long userId,
        InvestorLevel investorLevel,
        int portfolioImpactScore,
        BigDecimal totalPortfolioValueTry,
        BigDecimal totalInvestedAmountTry,
        BigDecimal totalNominalProfitTry,
        BigDecimal totalRealProfitTry,
        String largestPositionSymbol,
        BigDecimal largestPositionValueTry,
        BigDecimal largestPositionRatio,
        int assetConcentrationScore,
        int profitScore,
        int realProfitScore,
        int riskExposureScore,
        int positionCount,
        int openPositionCount,
        int closedPositionCount,
        BigDecimal cryptoExposureRatio,
        BigDecimal equityExposureRatio,
        BigDecimal fxExposureRatio,
        BigDecimal fundExposureRatio,
        BigDecimal metalExposureRatio,
        List<String> explanationMessages
) {}
