package com.nurseli.nrsfinanceportal.api.dto.portfolioai;

import java.util.List;

/**
 * Portfolio AI makro ve haber etkisi DTO'su; özet, veri kullanılabilirliği ve ilgili maddeleri taşır.
 */
public record PortfolioAiMacroAndNewsImpactDto(
        String summary,
        String dataAvailability,
        List<String> relevantItems
) {
}
