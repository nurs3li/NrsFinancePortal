package com.nurseli.nrsfinanceportal.service.portfolio.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConcentrationLevel;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConfidenceLevel;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PortfolioAiParsedOutput(
        int portfolioScore,
        int riskScore,
        PortfolioAiConfidenceLevel confidence,
        PortfolioAiConcentrationLevel concentrationRisk,
        String summary,
        List<String> findings,
        String scenarioComment,
        List<PortfolioAiParsedAssetComment> assetComments,
        String disclaimer,
        /** OPENAI | FALLBACK_RULE_BASED — API ve kota için. */
        String source,
        PortfolioOverviewParsed portfolioOverview,
        DecisionPerspectiveParsed decisionPerspective,
        MacroAndNewsImpactParsed macroAndNewsImpact,
        List<AssetInsightParsed> assetInsights,
        String finalNote
) {
    public PortfolioAiParsedOutput withSource(String newSource) {
        return new PortfolioAiParsedOutput(
                portfolioScore,
                riskScore,
                confidence,
                concentrationRisk,
                summary,
                findings,
                scenarioComment,
                assetComments,
                disclaimer,
                newSource,
                portfolioOverview,
                decisionPerspective,
                macroAndNewsImpact,
                assetInsights,
                finalNote
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PortfolioOverviewParsed(
            String summary,
            String currentSituation,
            String mainPositive,
            String mainRisk
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DecisionPerspectiveParsed(
            String scenario,
            String comment,
            String shortTermView,
            String mediumTermView,
            List<String> watchPoints
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MacroAndNewsImpactParsed(
            String summary,
            String dataAvailability,
            List<String> relevantItems
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AssetInsightParsed(
            String symbol,
            String assetName,
            String assetClass,
            double weightPct,
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
    public record PortfolioAiParsedAssetComment(
            String symbol,
            String assetName,
            String assetClass,
            double weightPct,
            Double returnPct,
            int assetScore,
            int riskScore,
            String riskLevel,
            String role,
            List<String> positiveFactors,
            List<String> riskFactors,
            String shortComment,
            String detailComment
    ) {
    }
}
