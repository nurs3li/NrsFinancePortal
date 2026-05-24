package com.nurseli.nrsfinanceportal.api.dto.portfolioai;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiEmailDeliveryFrequency;
import jakarta.validation.constraints.Size;

/**
 * Portfolio AI email teslimat güncelleme request'i; etkinlik, email ve sıklık ayarlarını taşır.
 */
public record PortfolioAiEmailDeliveryUpsertRequest(
        boolean enabled,
        @Size(max = 255) String email,
        PortfolioAiEmailDeliveryFrequency frequency
) {
}
