package com.nurseli.nrsfinanceportal.service.portfolio.ai;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConcentrationLevel;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiDetailLevel;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiRiskProfile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record PortfolioAiContextSnapshot(
        PortfolioAiAnalysisType analysisType,
        PortfolioAiRiskProfile riskProfile,
        PortfolioAiDetailLevel detailLevel,
        boolean includeNews,
        boolean includeMacro,
        boolean includeRealReturn,
        BigDecimal totalValueTry,
        BigDecimal totalCostTry,
        BigDecimal nominalPnlTry,
        Double nominalReturnPct,
        BigDecimal realPnlTry,
        Double realReturnPct,
        boolean realReturnAvailable,
        int healthScore,
        String largestPositionSymbol,
        double largestPositionWeightPct,
        PortfolioAiConcentrationLevel concentrationLevel,
        Map<String, Double> allocationByAssetClassPct,
        List<PositionLine> topByWeight,
        List<PositionLine> topGainers,
        List<PositionLine> topLosers,
        /** Birleşik varlık yorum hedefleri (tekilleştirilmiş). */
        List<PositionLine> assetCommentTargets,
        int openCount,
        int soldCount,
        Map<String, Object> macroSummary,
        List<Map<String, Object>> newsSummaries,
        /** Gerçek sembol bazlı haber özeti mevcut mu (placeholder değil). */
        boolean newsAvailable
) {
    public record PositionLine(
            String symbol,
            String assetName,
            String assetClass,
            String status,
            double quantity,
            Double currentPrice,
            double currentValue,
            Double buyPrice,
            Double costBasis,
            Double pnl,
            Double returnPct,
            double weightPct,
            Integer holdingDays
    ) {
    }
}
