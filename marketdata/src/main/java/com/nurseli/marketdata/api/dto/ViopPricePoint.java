package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ViopPricePoint(
        String contractCode,
        Instant time,
        BigDecimal price,
        String source,
        int periodMinutes
) {}
