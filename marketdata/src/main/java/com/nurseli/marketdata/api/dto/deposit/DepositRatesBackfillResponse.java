package com.nurseli.marketdata.api.dto.deposit;

import java.time.LocalDate;

public record DepositRatesBackfillResponse(
        int seriesTouched,
        int pointsUpserted,
        int pointsSkipped,
        LocalDate fromInclusive,
        LocalDate toInclusive,
        String status
) {}
