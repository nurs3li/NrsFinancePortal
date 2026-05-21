package com.nurseli.nrsfinanceportal.common.dto.portfolioai;

import java.util.List;

public record PortfolioAiMacroAndNewsImpactDto(
        String summary,
        String dataAvailability,
        List<String> relevantItems
) {
}
