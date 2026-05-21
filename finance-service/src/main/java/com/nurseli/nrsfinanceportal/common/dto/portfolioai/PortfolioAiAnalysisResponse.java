package com.nurseli.nrsfinanceportal.common.dto.portfolioai;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConcentrationLevel;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConfidenceLevel;

import java.time.Instant;
import java.util.List;

public record PortfolioAiAnalysisResponse(
        String id,
        Instant createdAt,
        String title,
        PortfolioAiAnalysisType analysisType,
        int portfolioScore,
        int riskScore,
        PortfolioAiConfidenceLevel confidence,
        PortfolioAiConcentrationLevel concentrationRisk,
        String summary,
        List<String> findings,
        String scenarioComment,
        List<PortfolioAiAssetCommentDto> assetComments,
        String disclaimer,
        /** OPENAI | FALLBACK_RULE_BASED */
        String source,
        /** OpenAI model adı; fallback kayıtlarında null veya FALLBACK_RULE_BASED */
        String model,
        PortfolioAiPortfolioOverviewDto portfolioOverview,
        PortfolioAiDecisionPerspectiveDto decisionPerspective,
        PortfolioAiMacroAndNewsImpactDto macroAndNewsImpact,
        List<PortfolioAiAssetInsightDto> assetInsights,
        String finalNote
) {
}
