package com.nurseli.marketdata.api.dto.inflation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InflationHistoryRowDto(
        String category,
        String indicatorType,
        String source,
        String frequency,
        String unit,
        Integer baseYear,
        String country,
        String seriesCode,
        LocalDate indexMonth,
        BigDecimal indexValue,
        BigDecimal monthlyChangePercent,
        BigDecimal annualChangePercent
) {}
