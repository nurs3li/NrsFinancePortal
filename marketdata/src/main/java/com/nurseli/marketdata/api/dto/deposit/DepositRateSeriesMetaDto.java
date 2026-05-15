package com.nurseli.marketdata.api.dto.deposit;

public record DepositRateSeriesMetaDto(
        String seriesCode,
        String logicalIndicatorCode,
        String category,
        String source,
        String frequency,
        String unit,
        String flowType,
        String currency,
        String term
) {}
