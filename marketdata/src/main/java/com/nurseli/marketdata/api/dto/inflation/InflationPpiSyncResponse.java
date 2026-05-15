package com.nurseli.marketdata.api.dto.inflation;

import java.time.LocalDate;

public record InflationPpiSyncResponse(
        int monthsProcessed,
        int rowsUpserted,
        LocalDate from,
        LocalDate to,
        String status
) {}
