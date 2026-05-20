package com.nurseli.marketdata.api.dto.eurobond;

import java.time.LocalDate;

public record EurobondBackfillResponse(
        int seriesTouched,
        int pointsUpserted,
        int pointsSkipped,
        LocalDate from,
        LocalDate to,
        String status) {}
