package com.nurseli.nrsfinanceportal.api.dto;

import java.util.List;

/**
 * Portföy insight bildirim değerlendirme response'u; üretilen bildirim sayısı ve türlerini taşır.
 */
public record PortfolioInsightNotificationEvaluateResponse(
        int generatedCount,
        List<String> generatedTypes
) {}
