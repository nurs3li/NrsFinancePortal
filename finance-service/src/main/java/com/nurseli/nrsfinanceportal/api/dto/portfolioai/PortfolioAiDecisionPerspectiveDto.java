package com.nurseli.nrsfinanceportal.api.dto.portfolioai;

import java.util.List;

/**
 * Portfolio AI karar perspektifi DTO'su; senaryo, yorum ve kısa/orta vadeli görüşleri taşır.
 */
public record PortfolioAiDecisionPerspectiveDto(
        String scenario,
        String comment,
        String shortTermView,
        String mediumTermView,
        List<String> watchPoints
) {
}
