package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * finance-service portfolio AI ham model kaydı — OpenAI'dan gelen deserialize edilmemiş JSON yapısını temsil eder.
 */
@JsonIgnoreProperties(ignoreUnknown = true)

public record PortfolioAiRawModel(
        Integer portfolioScore,
@JsonIgnoreProperties(ignoreUnknown = true)
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
    /**
     * PortfolioAiRawOverview — Ham model genel bakış alt yapısı.
     */
    public record PortfolioAiRawOverview(
            String summary,
    String currentSituation,
            String mainPositive,
            String mainRisk
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * PortfolioAiRawDecision — Ham model karar perspektifi alt yapısı.
     */
    public record PortfolioAiRawDecision(
    String scenario,
            String comment,
            String shortTermView,
            String mediumTermView,
            List<String> watchPoints
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * PortfolioAiRawMacroNews — Ham model makro/haber alt yapısı.
     */
    public record PortfolioAiRawMacroNews(
            String summary,
            String dataAvailability,
            List<String> relevantItems
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * PortfolioAiRawAssetInsight — Ham model varlık insight alt yapısı.
     */
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
    /**
     * PortfolioAiRawAsset — Ham model varlık yorumu alt yapısı.
     */
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
