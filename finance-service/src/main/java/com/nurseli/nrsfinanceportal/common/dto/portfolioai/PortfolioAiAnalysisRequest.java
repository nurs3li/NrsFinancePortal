package com.nurseli.nrsfinanceportal.common.dto.portfolioai;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiDetailLevel;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiRiskProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PortfolioAiAnalysisRequest(
        @NotBlank @Size(max = 120) String title,
        @NotNull PortfolioAiAnalysisType analysisType,
        @NotNull PortfolioAiRiskProfile riskProfile,
        @NotNull PortfolioAiDetailLevel detailLevel,
        boolean includeNews,
        boolean includeMacro,
        boolean includeRealReturn
) {
}
