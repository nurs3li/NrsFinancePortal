package com.nurseli.marketdata.api.dto.inflation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InflationSeriesSyncSummaryDto(
        String indicator,
        String code,
        LocalDate latestDate,
        BigDecimal latestValue,
        int observationCount,
        int rowsUpserted,
        String status
) {}
