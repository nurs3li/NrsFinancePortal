package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SimulationPerformancePointDto(
        LocalDate date,
        BigDecimal priceTry,
        BigDecimal cumulativeReturnPct
) {
}
