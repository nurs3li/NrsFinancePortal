package com.nurseli.marketdata.api.dto.inflation;

import java.util.List;

public record InflationCompareResponse(
        List<InflationCompareRowDto> rows
) {}
