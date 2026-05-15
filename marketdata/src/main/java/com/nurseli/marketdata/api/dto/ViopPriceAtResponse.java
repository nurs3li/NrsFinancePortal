package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ViopPriceAtResponse(
        String contractCode,
        LocalDateTime requestedDate,
        LocalDateTime matchedPriceTime,
        BigDecimal price,
        String matchType,
        String source,
        String dataQuality) {}
