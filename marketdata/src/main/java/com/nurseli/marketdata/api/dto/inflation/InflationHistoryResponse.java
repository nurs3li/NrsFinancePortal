package com.nurseli.marketdata.api.dto.inflation;

import java.util.List;

public record InflationHistoryResponse(
        String indicatorType,
        List<InflationHistoryRowDto> rows
) {}
