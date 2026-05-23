package com.nurseli.nrsfinanceportal.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Kripto: market-data latest ile aynı semantik (alış/satış + zaman). */
public record CryptoOverviewDto(
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        String source,
        LocalDateTime timestamp
) {}
