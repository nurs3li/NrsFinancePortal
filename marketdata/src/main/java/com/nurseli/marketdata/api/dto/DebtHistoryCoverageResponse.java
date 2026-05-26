package com.nurseli.marketdata.api.dto;

import java.time.LocalDate;

public record DebtHistoryCoverageResponse(
        String isin,
        LocalDate requestedFrom,
        LocalDate requestedTo,
        LocalDate oldestAvailable,
        LocalDate newestAvailable,
        long availableDays,
        long expectedDays,
        boolean ready
) {}
