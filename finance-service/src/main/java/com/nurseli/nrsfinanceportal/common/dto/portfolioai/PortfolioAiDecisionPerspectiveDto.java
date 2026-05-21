package com.nurseli.nrsfinanceportal.common.dto.portfolioai;

import java.util.List;

public record PortfolioAiDecisionPerspectiveDto(
        String scenario,
        String comment,
        String shortTermView,
        String mediumTermView,
        List<String> watchPoints
) {
}
