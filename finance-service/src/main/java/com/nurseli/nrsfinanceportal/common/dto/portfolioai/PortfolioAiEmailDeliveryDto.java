package com.nurseli.nrsfinanceportal.common.dto.portfolioai;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiEmailDeliveryFrequency;

import java.time.Instant;

public record PortfolioAiEmailDeliveryDto(
        boolean enabled,
        String email,
        PortfolioAiEmailDeliveryFrequency frequency,
        Instant updatedAt
) {
}
