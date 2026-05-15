package com.nurseli.marketdata.api.dto.deposit;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DepositRateLatestRowDto(
        String seriesCode,
        String logicalIndicatorCode,
        String category,
        String source,
        String frequency,
        String unit,
        String flowType,
        String currency,
        String term,
        LocalDate observationDate,
        BigDecimal ratePercent
) {}
