package com.nurseli.nrsfinanceportal.api.dto;

/**
 * Tek portföy insight öğesi; tip, UI şiddeti ve kullanıcıya gösterilecek mesajı taşır.
 */
public record PortfolioInsightItemDto(
        String type,
        String uiSeverity,
        String message
) {}
