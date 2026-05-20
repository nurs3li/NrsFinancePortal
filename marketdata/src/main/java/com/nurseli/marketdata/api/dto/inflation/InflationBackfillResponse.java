package com.nurseli.marketdata.api.dto.inflation;

import java.time.LocalDate;
import java.util.List;

public record InflationBackfillResponse(
        String status,
        LocalDate from,
        LocalDate to,
        List<InflationSeriesSyncSummaryDto> series
) {}
