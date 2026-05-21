package com.nurseli.nrsfinanceportal.service.portfolio.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PortfolioAiRawModel(
        Integer portfolioScore,
        Integer riskScore,
        String confidence,
        String concentrationRisk,
        String summary,
        List<String> findings,
        String scenarioComment,
        List<PortfolioAiRawAsset> assetComments,
        String disclaimer,
        PortfolioAiRawOverview portfolioOverview,
        PortfolioAiRawDecision decisionPerspective,
        PortfolioAiRawMacroNews macroAndNewsImpact,
        List<PortfolioAiRawAssetInsight> assetInsights,
        String finalNote
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PortfolioAiRawOverview(
            String summary,
            String currentSituation,
            String mainPositive,
            String mainRisk
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PortfolioAiRawDecision(
            String scenario,
            String comment,
            String shortTermView,
            String mediumTermView,
            List<String> watchPoints
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PortfolioAiRawMacroNews(
            String summary,
            String dataAvailability,
            List<String> relevantItems
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PortfolioAiRawAssetInsight(
            String symbol,
            String assetName,
            String assetClass,
            Double weightPct,
            Double returnPct,
            String role,
            String impactOnPortfolio,
            String positiveView,
            String riskView,
            List<String> whatToWatch,
            String shortComment,
            String detailComment
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PortfolioAiRawAsset(
            String symbol,
            String assetName,
            String assetClass,
            Double weightPct,
            Double returnPct,
            Integer assetScore,
            Integer riskScore,
            String riskLevel,
            String role,
            List<String> positiveFactors,
            List<String> riskFactors,
            String shortComment,
            String detailComment
    ) {
    }
}
