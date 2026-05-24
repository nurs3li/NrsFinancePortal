package com.nurseli.nrsfinanceportal.api.dto.portfolioai;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiEmailDeliveryFrequency;

import java.time.Instant;

/**
 * Portfolio AI email teslimat ayarları DTO'su; etkinlik, hedef email ve sıklık bilgisini taşır.
 */
public record PortfolioAiEmailDeliveryDto(
        boolean enabled,
        String email,
        PortfolioAiEmailDeliveryFrequency frequency,
        Instant updatedAt
) {
}
