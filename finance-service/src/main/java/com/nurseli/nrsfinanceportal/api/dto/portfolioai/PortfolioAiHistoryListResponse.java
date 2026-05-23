package com.nurseli.nrsfinanceportal.api.dto.portfolioai;

import java.util.List;

/**
 * Portfolio AI geçmiş analiz listesi response'u; önceki analiz kayıtlarını taşır.
 */
public record PortfolioAiHistoryListResponse(List<PortfolioAiHistoryItemDto> items) {
}
