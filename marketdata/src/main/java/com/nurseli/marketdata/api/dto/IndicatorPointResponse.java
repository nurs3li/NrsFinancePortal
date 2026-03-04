package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IndicatorPointResponse(
        LocalDateTime t,
        BigDecimal value
) {}