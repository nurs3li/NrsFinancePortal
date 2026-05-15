package com.nurseli.marketdata.api.dto.deposit;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DepositRateHistoryRowDto(
        String seriesCode,
        String currency,
        String term,
        LocalDate observationDate,
        BigDecimal ratePercent
) {}
