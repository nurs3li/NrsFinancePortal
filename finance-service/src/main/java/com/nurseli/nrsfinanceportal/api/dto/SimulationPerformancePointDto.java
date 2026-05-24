package com.nurseli.nrsfinanceportal.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Yatırım simülasyonu performans noktası; tarih, TRY fiyatı ve kümülatif getiri yüzdesini taşır.
 */
public record SimulationPerformancePointDto(
        LocalDate date,
        BigDecimal priceTry,
        BigDecimal cumulativeReturnPct
) {
}
