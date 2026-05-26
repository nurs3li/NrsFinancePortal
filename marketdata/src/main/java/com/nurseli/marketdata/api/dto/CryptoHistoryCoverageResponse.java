package com.nurseli.marketdata.api.dto;

import java.time.LocalDate;

public record CryptoHistoryCoverageResponse(
        String symbol,
        LocalDate requestedFrom,
        LocalDate requestedTo,
        LocalDate oldestAvailable,
        LocalDate newestAvailable,
        long availableDays,
        long expectedDays,
        boolean ready
) {}
