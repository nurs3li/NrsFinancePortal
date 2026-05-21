package com.nurseli.marketdata.api.dto.eurobond;

import java.time.LocalDate;

public record EurobondInstrumentIngestResponse(
        int instrumentsTouched,
        int pointsUpserted,
        int pointsSkipped,
        LocalDate from,
        LocalDate to,
        String status) {}
