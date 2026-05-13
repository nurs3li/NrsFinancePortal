package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record IndicatorPointResponse(
        OffsetDateTime t,
        BigDecimal value
) {}
