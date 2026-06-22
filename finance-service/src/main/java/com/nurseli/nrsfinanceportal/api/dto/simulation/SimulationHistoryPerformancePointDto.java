package com.nurseli.nrsfinanceportal.api.dto.simulation;

import java.math.BigDecimal;

/**
 * Simülasyon geçmişi performans serisi noktası.
 */
public record SimulationHistoryPerformancePointDto(
        String date,
        BigDecimal priceTry,
        BigDecimal cumulativeReturnPct
) {
}
