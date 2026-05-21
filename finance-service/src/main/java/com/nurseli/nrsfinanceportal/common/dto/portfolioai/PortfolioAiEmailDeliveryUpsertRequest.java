package com.nurseli.nrsfinanceportal.common.dto.portfolioai;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiEmailDeliveryFrequency;
import jakarta.validation.constraints.Size;

public record PortfolioAiEmailDeliveryUpsertRequest(
        boolean enabled,
        @Size(max = 255) String email,
        PortfolioAiEmailDeliveryFrequency frequency
) {
}
